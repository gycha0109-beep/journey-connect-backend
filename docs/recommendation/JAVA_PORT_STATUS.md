# 추천 알고리즘 Java 전수 포팅 진행 상태

## 현재 기준선

- 참조 구현: `reference/recommendation-ts-2.9b`
- 기준 패키지: `yeojeong-personalization-phase2-9b-offline-evaluation@0.1.0`
- 원본 ZIP SHA-256: `e4bd7e79c5e539c6798fcfd25ed3cdfcda177fa4b6f1301e35892359535b63f5`
- 동결 manifest: 418개 파일
- TypeScript 소스 파일: 90개
- 테스트 파일: 65개
- 기준 검증: typecheck PASS / lint PASS / 963 tests PASS
- Java Core 버전: `1.0.0`
- 동결일: 2026-07-17
- Core 1.0 게이트 확정일: 2026-07-18

## 단계 상태

| 단계 | 상태 | 근거 |
|---|---|---|
| Stage 0 — TypeScript 기준선 동결 | 완료 | 참조 소스, SHA-256 manifest, 963 tests |
| Stage 1 — Java 추천 코어 골격·계약 | 완료 | 독립 모듈, 순수성 게이트, Foundation/Wave 1 exact golden |
| Stage 2 — Java 전수 포팅·동등성 | **완료** | Foundation~Wave 7 계약·canonical·IEEE-754 raw bits exact match |
| Java Core 1.0 | **고정** | replay·offline evaluation·전체 orchestration·통합 exact-equivalence gate PASS |
| P0 — 저장·이벤트·프로젝트 통합 | **진행 중** | P0-1 DB 실제 실행 완료, P0-2 canonical JPA·adapter·Testcontainers 구현 및 리뷰 보완 완료; 로컬 Gradle 최종 게이트 대기 |
| P1 — 추천 품질 개선 | 진입 금지 | P0 완료 필요 |
| P2 — 통계 검증·출시 판정 | 진입 금지 | P1 정책·데이터 필요 |

> Wave 번호는 Java 포팅 작업 배치명이다. Journey Connect 플랫폼 본체의 기존 Phase 번호와 관계없다.

## 완료된 Java 이식 범위

### Foundation / Wave 1

- entity, feature, event, context 기반 계약
- feature vocabulary v1 42개
- 정책·validation·strict UTC·canonical JSON
- candidate limit, repeat/time decay, saturation
- explicit preference 및 state-event resolution
- UTF-16 code-unit comparator

### Wave 2 — scoring

- interest match
- hard context eligibility
- soft context match
- freshness
- popularity
- score composition
- component provenance 및 terminal result

### Wave 3 — ranking·diversity·exploration primitive

- base ranking v1과 complete tie-break
- signed zero 보존
- ranking cursor v1 및 pagination
- diversity reranking과 movement/window invariant
- FNV-1a UTF-8 seeded exploration
- target rank 6·16 insertion
- hard-excluded resurrection 방지
- terminal migration과 partition invariant

### Wave 4 — ranking-v2/v3 통합

- 공용 `BaseRankingSnapshotBuilder`
- base → diversity → exploration 통합
- cursor-v2/v3 encode/decode 및 pagination
- page boundary·terminal partition·provenance 보존

### Wave 5 — exposure trace

- 최소 노출 이벤트 투영
- replay key·page fingerprint SHA-256
- signed-zero 별도 보존
- event ID·idempotency·run binding 충돌 분리
- transitive alias ambiguity와 deterministic audit

### Wave 6 — behavior resolution·outcome attribution

- behavior canonical signature
- duplicate/conflict/alias resolution
- run·session·user 범위 검증
- 최신 선행 exposure 선택
- 30분/7일 attribution window와 cutoff
- positive·negative·severe report outcome
- attribution audit category 11종

### Wave 7 — replay·offline evaluation·orchestration

- cursor exhaustion 기반 full ranking collection
- page 연속성·rank·partition·score·signed-zero invariant
- exact replay / partial observation / mismatch / invalid trace / invalid snapshot
- observation coverage interval union
- snapshot·policy·seed·cursor·fingerprint binding
- baseline/treatment policy comparison
- Top-5/10/20 overlap·Jaccard·rank shift
- origin·diversity·common-support·attribution quality metric
- evaluation decision: `block`, `insufficient_evidence`, `review`, `pass`
- 단일 case orchestration
- exact/partial만 attribution·comparison 진행
- mismatch/invalid 시 downstream fail-closed

## Core 1.0 동등성 상태

```text
Foundation oracle                         EXACT MATCH
Wave 1 foundation golden                 EXACT MATCH
Wave 2 scoring golden                    EXACT MATCH
Wave 3 base ranking golden               EXACT MATCH
Wave 3 diversity golden                  EXACT MATCH
Wave 3 exploration golden                EXACT MATCH
Wave 4 ranking-v2/v3 integration         EXACT MATCH
Wave 5 exposure trace/resolution         EXACT MATCH
Wave 6 behavior/outcome attribution      EXACT MATCH
Wave 7 replay/offline evaluation         EXACT MATCH
Reference SHA-256                        418/418 PASS
Java compile                             --release 21 / -Xlint:all / -Werror PASS
```

통합 게이트:

```bash
bash scripts/recommendation/verify_java_core_1_0.sh
```

검증 내용:

- 순수 코어 framework dependency scan
- Java main/test 단일 컴파일
- Foundation~Wave 7 계약 테스트
- TypeScript exporter 10종 병렬 실행
- Java oracle 10종 실행
- canonical exact diff
- IEEE-754 raw-bit 비교
- committed golden 9종 회귀
- 참조 구현 SHA-256 baseline 검증

## 언어 간 차이 처리

### `Math.pow` 1 ULP 차이

Java `Math.pow`와 V8 사이 freshness decay 경계의 1 ULP 차이를 확인했다. Java 포팅은 `StrictMath.pow`로 TypeScript raw bits와 일치시켰다. epsilon 비교나 기대값 갱신은 사용하지 않았다.

## 참조 구현 내부 불일치

TypeScript의 독립 `calculateInterestMatch()`는 hard exclusion 시 `score: 0`을 반환하지만 `scoreCandidate()` validation은 같은 상태에서 `score: null`만 허용한다.

Java는 양쪽 실제 계약을 각각 재현하고 재현 테스트로 고정했다. 승인된 새 계약 버전 없이 silent normalization하지 않는다. 상세 내용은 `KNOWN_REFERENCE_INCONSISTENCIES.md`를 참조한다.

## 백엔드 빌드 상태

이전 Wave 4~6 적용본은 사용자 로컬에서 다음 검증이 성공했다.

```powershell
.\gradlew.bat clean test
```

Core 1.0 최종 적용본은 사용자 로컬에서 `clean test`와 전체 빌드 성공이 확인되어 P0 착수 게이트가 종료됐다.

별도 `integrationTest` 태스크는 현재 프로젝트에 정의되어 있지 않다.

## 다음 구현 순서

1. 사용자 로컬에서 P0-3 `clean test` 통과
2. 추천 orchestration application service
3. candidate → Core → snapshot/run/candidate 저장 트랜잭션
4. exposure·behavior API 연결
5. `SHADOW` 실행 경로
6. persistence → replay exact-match 통합 테스트
7. canary 이전 독립 리뷰

## 금지 상태

- TypeScript 참조 구현 수정 금지
- Core 1.0 정책 상수 덮어쓰기 금지
- P0에서 코어 내부에 Spring/JPA/DB 의존 추가 금지
- snapshot·run·evaluation 과거 row 수정 금지
- 페이지별 재랭킹 금지
- exact 차이를 epsilon/기대값 일괄 갱신으로 은폐 금지
- P0 완료 전에 P1 정책 개선 혼합 금지


## P0-1 추천 신뢰 저장소

구현 완료:

- DB v1.8 `01~06` 불변 보존 및 SHA-256 manifest
- v1.9 `07_recommendation_storage.sql`
- v1.9 `08_recommendation_security_roles.sql`
- v1.9 `09_recommendation_smoke_test.sql`
- snapshot·run·ranked/terminal candidate·exposure·behavior 7개 테이블
- ranking 결과 snapshot과 run surface 결속
- domain-separated snapshot SHA-256 및 event SHA-256 DB 재검증
- snapshot/exposure/behavior payload 크기 상한
- append-only trigger와 최소 권한 `jc_recommendation`
- ranked·terminal 게시글 접근성·active author 검증
- run partition 및 exposure page deferred integrity
- PostgreSQL 15·18 CI workflow

현재 백엔드 기본 Flyway/JPA는 과거 `user_account/journey_post` 기준이므로 신규 V7·V8은 `db/migration-v1_8`에 격리했다. P0-2에서 DB v1.8 기준선으로 수렴하기 전 기본 migration 위치로 이동하지 않는다.


## P0-2 canonical 백엔드 수렴

구현·리뷰 완료:

- DB v2.0 `10_backend_runtime.sql`~`12_backend_runtime_smoke_test.sql`
- `refresh_tokens`, `crews`, `crew_members`
- JPA를 `app_users/posts/regions` 및 canonical interaction 테이블로 수렴
- 게시글 status/visibility/moderation/논리삭제 모델링
- canonical `can_user_view_post` 기반 detail/bookmark/recommendation 정책
- 비활성 계정 authenticated mutation fail-closed
- candidate projection 및 Core input mapper
- snapshot/run/exposure/behavior/replay persistence adapter
- PostgreSQL 15 Testcontainers와 Hibernate `ddl-auto=validate`
- PostgreSQL 15·18 canonical SQL 01~12 CI
- H2/create-drop/PostGIS legacy CI 제거
- 독립 리뷰 후 권한·reviewer trigger·bookmark·idempotency·cursor 보완

종료 게이트는 사용자 로컬에서 통과했다.

```powershell
.\gradlew.bat clean test --stacktrace
.\gradlew.bat :jc-recommendation-core:check --stacktrace
```

P0-2.2 전체 build 성공까지 확인되어 canonical 수렴 단계는 종료됐다.


## P0-3 최소 권한 DB 역할 라우팅

구현·독립 리뷰 완료:

- 단일 restricted login + transaction-local `SET LOCAL ROLE`
- 허용 역할 enum: `jc_app`, `jc_auth`, `jc_recommendation`
- 허용 전파 enum: `REQUIRED`, `REQUIRES_NEW`
- 같은 트랜잭션 안의 역할 변경 차단
- 검증된 JWT `sub`만 `jc.current_user_id`로 transaction-local 주입
- 익명 트랜잭션의 사용자 GUC 명시 초기화
- auth credential mapping과 app-safe profile mapping 분리
- `app_users.email/password_hash/role`의 app SQL 조회 차단
- 인증 프로필 및 refresh token은 `jc_auth`로 제한
- 추천 persistence/candidate/replay는 `jc_recommendation`으로 제한
- `NOT_SUPPORTED` 기반 원본 로그인 SQL 실행 제거
- 조회수 컴포넌트의 임의 사용자 GUC 재설정 제거
- startup 시 restricted login 속성·역할 membership·직접 grant·객체 ownership 검증
- DB v2.1 `13_backend_role_routing.sql`, `14_backend_role_routing_smoke_test.sql`
- PostgreSQL 15/18 CI와 Testcontainers canonical 01~14 갱신

현재 검증:

```text
P0-3 static contract gate                 PASS
Gradle clean testClasses                  PASS
Java recommendation core check            PASS
Workflow YAML parse                        PASS
PostgreSQL Testcontainers runtime          사용자 로컬 종료 게이트 대기
```

사용자 로컬 종료 게이트:

```powershell
.\gradlew.bat clean :test --stacktrace
.\gradlew.bat :jc-recommendation-core:check --stacktrace
```

이 게이트가 통과하면 다음 작업은 추천 orchestration application service다.
