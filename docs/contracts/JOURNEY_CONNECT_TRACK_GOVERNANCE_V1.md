# Journey Connect Track Governance V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 문서 개정 | `V1.1 / SC-1` |
| 상태 | `ACTIVE / P2_BASELINE_ALIGNED` |
| 기준 추천 | `P1 CLOSED / P2 TECHNICAL CLOSED / P2 PRODUCTION HOLD` |
| 기준 DB | `journey-connect-db-v2.7/01..26` |
| 변경 승인 | System Coordination `SC-1` |
| 승인일 | `2026-07-19` |

## 1. 목적

이 문서는 Journey Connect의 네 실행 트랙과 총괄 트랙이 병렬로 작업할 때 책임 중복, DB 충돌, 계약 드리프트를 막는 운영 규칙이다.

---

## 2. 트랙별 책임

## 2.1 Data Platform

### 소유

- 신규 범용 raw user behavior ingestion
- 공통 `platform-event-v1` envelope와 validation
- deduplication, quarantine, retry, replay
- 세션·행동 집계 데이터셋
- 추천·검색·실험 입력용 versioned snapshot/projection
- 데이터 품질 규칙과 lineage

### 소유하지 않음

- 추천 점수·정렬 정책
- 검색 ranking 정책
- 콘텐츠 moderation 결정
- 실험 출시 판정

### 경계 출력

- 기존 P0 `recommendation_behavior_event`를 보존하는 versioned adapter/projection
- validated behavior stream
- user signal/profile input snapshot
- data quality report
- dataset snapshot ID와 schema version

---

## 2.2 Intelligence Platform

### 소유

- 기존 추천 P0/P1/P2 보호 및 기존 `recommendation_behavior_event` write 경로 보존
- P1 profile·policy selection과 P2 runtime의 Intelligence 계산 의미 보호
- 추천 후보·점수·diversity·exploration
- 검색 retrieval/ranking
- 콘텐츠 이해·분석 결과
- 여행 일정 생성기
- model/prompt/policy version
- Intelligence run, snapshot, provenance

### 소유하지 않음

- raw event ingestion의 범용 저장
- 운영자 권한·감사 체계
- 실험 배정과 최종 출시 승인
- 타 트랙 데이터 품질 판정 수정

### 내부 모듈

```text
com.jc.backend.intelligence
├─ recommendation   # 기존 com.jc.backend.recommendation 유지 가능
├─ search
├─ contentanalysis
└─ tripplanner
```

`jc-recommendation-core`는 계속 독립 순수 Java 모듈로 유지한다.

---

## 2.3 Operations Platform

### 소유

- admin authentication/authorization
- 신고 접수·분류·우선순위
- 콘텐츠 hide/restore/remove
- 계정 제재·복구
- 추천/검색 eligibility override
- 운영 정책과 관리자 audit
- AI 결과 수동 교정 승인

### 소유하지 않음

- 과거 추천 run/snapshot 수정
- 추천 가중치 직접 변경
- 실험 통계 계산
- raw event 재작성

### 경계 출력

- versioned moderation/visibility state
- eligibility override projection
- admin audit event
- policy change record

---

## 2.4 Reliability Platform

### 소유

- experiment definition과 assignment의 semantic contract
- metric definition과 denominator/attribution authority
- offline/online evaluation semantics
- quality gates, regression, performance
- monitoring, alert, release evidence
- SHADOW/CANARY/LIVE 승격·HOLD·rollback 판정

### 소유하지 않음

- 추천/검색 계산 의미
- raw event 수정
- moderation 결정
- AI prompt를 평가 결과에 맞춰 직접 변경

### 현재 물리 배치와 경계 출력

현재 P2 assignment·experiment exposure·evaluation·release evidence는 recommendation P2 package와 recommendation DB role에 물리적으로 구현되어 있다. 이는 P2 CLOSED 기준선의 보호된 compatibility arrangement이며 Reliability가 의미 소유권을 가진다. 물리 이전은 별도 High-risk 제안과 전체 회귀 전까지 금지한다.

경계 출력:

- assignment contract
- authoritative experiment exposure contract
- evaluation run
- release evidence report
- release/rollback decision

---

## 2.5 System Coordination

### 소유

- `jc-system-contract-v1`
- contract registry
- identity scheme registry
- exposure source registry
- entity/surface/event/version vocabulary
- DB canonical version과 SQL sequence 배정
- 트랙 간 의존성·통합 순서
- breaking change 승인
- 공통 완료 게이트와 최종 충돌 판정

총괄 트랙은 기능 구현을 독점하지 않는다. 경계를 결정하고 통합 증거를 검증한다.

---

## 3. 병렬 진행 허용 범위

### 현재 즉시 병렬 가능

| Data | Intelligence | Operations | Reliability |
|---|---|---|---|
| DP-1 event domain type/validator | IP-1 common contract type/validator | OP-0 admin/visibility/audit contract | RP-0 experiment/metric/release contract |
| fixture·canonicalization test | recommendation read-only adapter fixture | eligibility/stop control 설계 | P2 physical/semantic compatibility inventory |
| DB 비변경 | DB 비변경 | DB 비변경 | DB 비변경 |

### 선행 계약 후 병렬 가능

| 선행 계약 | 후속 작업 |
|---|---|
| DP-1 event type·identity validation | DP-2 event store 설계 |
| Operations visibility/eligibility contract | recommendation/search candidate filtering |
| Reliability assignment/exposure contract | 신규 search/content/planner experiment hook |
| place fact/provider authority contract | Trip Planning runtime |
| identity mapping owner·privacy policy | opaque identity 기반 P1/P2 shadow reconciliation |

### 병렬 금지

- 동일 DB 테이블을 두 트랙이 동시에 설계·write
- 동일 wire enum, contract ID, identity scheme, exposure source를 각 트랙에서 독립 정의
- 기존 P1/P2 source를 Data shadow projection으로 무승인 전환
- P2 physical writer를 Reliability로 문서만으로 이전
- canonical DB version과 SQL sequence를 여러 대화방에서 별도 생성
- 일반 추천 exposure, behavior impression, P2 experiment exposure를 공통 분모로 합산

## 4. 트랙 작업 단위

각 트랙의 단계는 다음 산출물을 가진다.

```text
1. Scope
2. Current Baseline
3. Contract Impact
4. Design
5. Implementation
6. Verification
7. Compatibility
8. Handoff
```

각 중간 단계마다 저장소 문서에 다음을 누적한다.

- 목적
- 변경 파일
- 구현 내용
- 검증 결과
- 보완 사항
- 잔여 리스크

채팅 보고는 간결하게 하되 상세 근거는 소스 문서에 남긴다.

---

## 5. 변경 제안 절차

다음 중 하나라도 해당하면 구현 전 `TRACK_CHANGE_PROPOSAL_TEMPLATE.md`를 작성한다.

- 공통 ID/enum/time/version 변경
- 다른 트랙 DB 읽기·쓰기 추가
- 새 event family 또는 event type 추가
- 신규 canonical snapshot/hash
- API 공통 응답 변경
- 정책 selector/experiment assignment 연동
- moderation eligibility 변경
- 개인정보 수집·보존 범위 변경

절차:

```text
Track Proposal
→ System Coordination 영향 판정
→ contract registry 예약/갱신
→ 구현
→ 트랙 검증
→ 교차 트랙 contract test
→ handoff
→ 통합 승인
```

---

## 6. DB 변경 운영

### 6.1 현재 상태

- canonical DB: `journey-connect-db-v2.7/01..26`
- SQL `25..26`: P2 evaluation/release 보호 기준선
- Flyway: canonical 운영 결정 전까지 임의 활성화 금지
- Hibernate: validate only

### 6.2 규칙

1. 트랙은 새 canonical DB 디렉터리나 SQL 번호를 임의로 만들지 않는다.
2. 총괄 트랙이 `26` 이후 target DB version과 sequence range를 배정한다.
3. 하나의 변경 묶음은 schema + role/grant + validation + smoke test를 포함한다.
4. table write owner를 문서에 기록한다.
5. 다른 트랙 컬럼을 추가할 때 해당 owner 승인과 read/write contract가 필요하다.
6. 기존 SQL을 수정하기보다 새 forward migration을 우선한다. 기준선 패키지 자체 수정은 재생성·전체 검증이 가능한 경우에만 허용한다.
7. PostgreSQL 실제 실행이 불가능한 상태에서 DB 단계 완료를 선언하지 않는다.
8. P1/P2 보호 table·role·grant 변경은 High-risk로 분류하고 전체 추천 회귀와 canonical evidence 검증을 요구한다.

---

## 7. 브랜치와 변경 범위

기존 팀 브랜치 규칙은 유지하되 시스템 트랙은 기능명을 명확히 적는다.

```text
back/<name>/data-event-contract
back/<name>/intelligence-search-v1
back/<name>/operations-admin-audit
back/<name>/reliability-experiment-assignment
```

한 PR에서 여러 트랙의 핵심 계약과 구현을 동시에 변경하지 않는다. 공통 계약 변경 PR과 트랙 구현 PR을 분리하는 것을 원칙으로 한다.

---

## 8. 트랙별 필수 테스트

### Data

- schema validation
- duplicate/idempotency conflict
- out-of-order event
- retry/quarantine/replay
- lineage and snapshot reproducibility
- PostgreSQL concurrency

### Intelligence

- deterministic input/output
- policy/model/prompt version binding
- existing P0/P1/P2 recommendation golden/replay regression
- search ranking fixture
- AI output schema and failure isolation
- planner constraint validation

### Operations

- role/permission matrix
- audit append-only
- moderation transition
- hidden/removed content leakage
- recommendation/search eligibility propagation

### Reliability

- deterministic assignment
- metric numerator/denominator fixture
- attribution window boundaries
- sample threshold/guardrail
- release and rollback state transition

### 공통

- API contract
- security
- DB role/grant
- existing feed non-regression
- P0/P1/P2 recommendation verification

---

## 9. 통합 순서

현재 기준선 이후 권장 순서는 다음과 같다.

```text
SC-1 System/Governance P2 Alignment [COMPLETE]
        ↓
DP-1 Event Domain Types & Validation
     ╲
      ╲ 병렬
       IP-1 Common Contract Types & Recommendation Adapter
        ↓
OP-0 Operations Visibility / Eligibility / Audit Contract
        ↓
RP-0 Reliability Experiment / Metric / Release Contract
        ↓
DP-2 Event Store & Idempotency + 후속 IP 검색/분석 기반
        ↓
교차 트랙 contract test
        ↓
Search / Content Analysis runtime
        ↓
Place authority 확정 후 Trip Planner
```

DP-1과 IP-1은 production DB를 변경하지 않는 범위에서 병렬 진행할 수 있다. 기존 추천 P1/P2 runtime 전환, P2 ownership migration, identity bulk migration은 별도 승인 전까지 이 순서에 포함하지 않는다.

## 10. 총괄 handoff 형식

각 트랙은 단계 종료 시 다음을 제출한다.

```text
완료:
기준선:
변경 계약 ID:
변경 파일:
DB 변경:
다른 트랙 영향:
검증 결과:
호환성 결과:
잔여 리스크:
다음 통합 작업:
```

상세 형식은 `templates/TRACK_HANDOFF_TEMPLATE.md`를 사용한다.

---

## 11. 통합 거부 조건

다음 중 하나라도 해당하면 총괄 트랙은 병합을 거부한다.

- 공통 enum 또는 ID 중복 정의
- 다른 트랙 테이블 direct write
- 버전 없는 schema/policy/metric
- P0/P1/P2 replay·golden 회귀
- PostgreSQL 미검증 DB 변경
- 운영 권한·감사 누락
- AI 결과 provenance 누락
- 실험 지표 정의 불명확
- rollback/forward-fix 부재
- 문서와 구현 불일치
- P2 `HOLD`를 기술 실패로 해석하거나 운영 증거 없이 `LIVE`로 승격
- 승인되지 않은 identity mapping 또는 shadow dataset runtime cutover
- protected P2 physical path를 전체 회귀 없이 이전
