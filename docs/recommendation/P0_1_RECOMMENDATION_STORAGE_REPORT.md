# P0-1 추천 신뢰 저장소 구현 보고서

## 1. 기준선

- 추천 코어: Java Core 1.0, Stage 2 exact-equivalence 완료
- 데이터베이스: Journey Connect DB v1.8
- PostgreSQL: 15+
- 적용 방식: 기존 `01~06` 불변, v1.9 증분 `07~09`

## 2. 구현 범위

### 2.1 신규 테이블

| 테이블 | 목적 |
|---|---|
| `recommendation_snapshot` | ranking·diversity·exploration 입력과 ranking 결과 canonical bytes 보존 |
| `recommendation_run` | 사용자·세션·정책·snapshot·결과 fingerprint 결속 |
| `recommendation_run_candidate` | 전체 최종 순위와 provenance 보존 |
| `recommendation_run_terminal_candidate` | 제외·적용 불가 후보 감사 정보 보존 |
| `recommendation_exposure_event` | 페이지 노출 이벤트와 replay/page fingerprint 보존 |
| `recommendation_exposure_candidate` | 실제 노출 후보·순위·origin 보존 |
| `recommendation_behavior_event` | 행동 이벤트·idempotency·run binding 보존 |

### 2.2 신뢰 저장 규칙

- canonical 원본은 `BYTEA`로 저장한다.
- 일반 이벤트 SHA-256은 canonical payload bytes를 직접 검증한다.
- snapshot SHA-256은 `journey-connect:snapshot:v1\0kind\0schemaVersion\0payload` 도메인 분리 bytes를 검증한다.
- payload 크기는 `octet_length`와 일치해야 하며 snapshot 16 MiB, exposure 2 MiB, behavior 256 KiB 상한을 둔다.
- snapshot kind와 run의 ranking/diversity/exploration/result FK 종류를 trigger로 검증한다.
- 모든 추천 이력 테이블에 UPDATE·DELETE 차단 trigger를 둔다.
- runtime role에는 UPDATE·DELETE·TRUNCATE를 부여하지 않는다.
- snapshot·run·candidate·exposure·behavior row는 과거 값을 수정하지 않는다.

### 2.3 순위 및 노출 무결성

- `PRIMARY KEY(run_id, absolute_rank)`
- `UNIQUE(run_id, entity_key)`
- P0 후보 identity는 `post:<posts.id>`로 고정한다.
- ranked·terminal 후보 삽입 시 원본 게시글이 사용자 접근 가능하고 `published + visible`이며 작성자가 active인지 검증한다.
- run에는 요청 surface와 immutable ranking result snapshot을 함께 결속한다.
- exposure event는 run의 user·session·context·surface와 일치하고 `served_at >= reference_time`이어야 한다.
- exposure candidate는 같은 run의 저장 후보와 rank·identity·origin·score·signed-zero가 일치해야 한다.
- 노출 페이지의 후보 수, 시작·끝 rank, page position 연속성을 deferred constraint trigger로 검증한다.
- personalized score와 exploration quality는 `[0,1]`, FNV tie-break key는 unsigned 32-bit 범위를 강제한다.
- signed zero는 `score`와 `score_is_negative_zero`를 분리 저장한다.

### 2.4 이벤트 idempotency

- exposure와 behavior 모두 `event_id` PK를 갖는다.
- `idempotency_key`는 별도 UNIQUE다.
- canonical payload SHA-256을 `payload_fingerprint`로 저장한다.
- 같은 key와 다른 payload의 충돌 판정은 persistence adapter에서 기존 fingerprint를 조회해 409로 변환한다.
- DB UNIQUE는 동시 삽입 race의 최종 방어선이다.

### 2.5 권한

신규 NOLOGIN 역할:

```text
jc_recommendation
```

| 역할 | 권한 |
|---|---|
| `jc_recommendation` | 후보 원본 SELECT, 추천 이력 SELECT·INSERT |
| `jc_admin` | 추천 이력 SELECT |
| `jc_app` | 추천 이력 접근 없음 |
| `jc_auth` | 추천 이력 접근 없음 |
| `jc_security_owner` | SHA-256 보안 함수 소유 |

행동 이벤트 API도 일반 `jc_app` 연결로 직접 INSERT하지 않는다. Spring의 추천 persistence adapter는 별도 추천 DB principal 또는 명시적 역할 전환 경계를 사용해야 한다.

## 3. DB v1.8 보존

`01~06` SHA-256 manifest를 추가했다.

```text
V1_8_BASELINE_SHA256.txt
```

정적 검증기는 다음을 보장한다.

- v1.8 기준 파일 6개 hash 불변
- canonical SQL과 Flyway SQL exact match
- canonical smoke test와 test resource exact match
- SQL quote·dollar block·괄호 균형
- PostgreSQL AST parsing(`pglast`)
- 7개 테이블과 append-only trigger 존재
- 위험한 runtime grant 부재

실행 명령:

```bash
python scripts/recommendation-db/verify_p0_recommendation_storage.py
```

## 4. PostgreSQL CI

신규 workflow:

```text
.github/workflows/recommendation-p0-db-ci.yml
```

검증 환경:

- PostgreSQL 15
- PostgreSQL 18

검증 항목:

- `01~09` 순차 실행
- 기존 base·security smoke test
- 신규 추천 smoke test
- recommendation security migration 재실행
- 테이블 수 및 실제 role privilege 확인

## 5. 백엔드 스키마 충돌 처리

현재 백엔드 기본 Flyway V1·V2와 JPA는 `user_account/journey_post`를 사용하고, DB v1.8은 `app_users/posts`를 사용한다.

이번 추천 마이그레이션을 기본 `db/migration`에 넣지 않았다. 잘못된 기준선에서 자동 실행되는 것을 막기 위한 조치다.

전용 위치:

```text
jc-backend/src/main/resources/db/migration-v1_8
```

상세 결정은 `P0_DB_BASELINE_CONVERGENCE.md`에 기록했다.

## 6. 검증 결과

현재 실행 환경에서 완료한 검증:

```text
v1.8 baseline SHA-256          6/6 PASS
canonical ↔ Flyway SQL        EXACT MATCH
canonical ↔ smoke resource    EXACT MATCH
SQL lexical/static gate       PASS
PostgreSQL AST parse 01~09     PASS
security contract gate        PASS
GitHub Actions YAML parse     PASS
Java Core source              변경 없음
Java Core 1.0 contracts       9/9 PASS
```

현재 실행 환경에는 PostgreSQL server와 `psql`이 없어 실제 SQL 실행은 수행하지 못했다. 또한 Gradle wrapper는 `services.gradle.org` DNS 차단으로 다운로드 단계에서 중단됐다. Java Core 계약은 독립 `javac -Werror` 경로로 9/9 통과했고, 이전 Core 1.0 적용본의 백엔드 빌드는 사용자 로컬에서 성공했다. PostgreSQL 15·18 service 기반 CI를 추가했으며, 로컬 또는 CI에서 `09_recommendation_smoke_test.sql`까지 통과해야 P0-1 DB 실행 게이트가 최종 종료된다.

## 7. 다음 단계

P0-2는 다음 순서로 진행한다.

1. 백엔드 JPA 기준선을 DB v1.8로 수렴
2. `posts` 기반 candidate projection query
3. DB projection → Java Core input mapper
4. snapshot/run/candidate persistence adapter
5. PostgreSQL Testcontainers 또는 실제 DB 통합 테스트
6. persistence → replay exact-match
