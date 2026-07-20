# Journey Connect System Contract V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `jc-system-contract-v1` |
| 문서 개정 | `V1.1 / SC-1` |
| 상태 | `ACTIVE / P2_BASELINE_ALIGNED` |
| 적용 범위 | Journey Connect 백엔드, 추천 코어, DB, Data·Intelligence·Operations·Reliability 플랫폼 트랙 |
| 기준 소스 | `Journey-Connect-P2-Final-Validation-Batch18` + DP-0/IP-0 정합화 계약 |
| 기준 추천 상태 | `P1 CLOSED / P2 TECHNICAL CLOSED / P2 PRODUCTION HOLD` |
| 기준 검증 | Backend `83/83 PASS`, P1 Core `17/17 PASS`, P2 Core `23/23 PASS` |
| 기준 DB | `journey-connect-db-v2.7`, canonical SQL `01..26` |
| 변경 승인 | System Coordination `SC-1` |
| 승인일 | `2026-07-19` |

이 문서는 Data, Intelligence, Operations, Reliability 네 트랙이 동시에 개발될 때 서로 다른 식별자, 이벤트, 시간, 버전, 상태 모델을 생성하지 않도록 고정하는 공통 계약이다.

`P2 TECHNICAL CLOSED`는 기술 구현과 검증의 종료를 의미한다. `P2 PRODUCTION HOLD`는 실제 CANARY 증거와 운영 승인 전까지 운영 승격을 보류한다는 의미이며, 구현 실패나 계약 미완료를 의미하지 않는다.

규범 용어 `MUST`, `MUST NOT`, `SHOULD`, `MAY`는 각각 필수, 금지, 권고, 선택을 의미한다.

---

## 2. 기준선 보호 계약

### 2.1 추천 P0·P1·P2 기준선

현재 추천 시스템은 다음 상태를 보호 기준선으로 가진다.

| 영역 | 상태 |
|---|---|
| 추천 P0 | `CLOSED` — Java 코어 동등성, 결정론, snapshot/run/exposure/behavior/replay 통합 |
| 추천 P1 | `CLOSED` — deterministic profile snapshot, 정책 선택, 비교 경로 |
| 추천 P2 기술 구현 | `CLOSED` — assignment, experiment exposure, dataset, evaluation, Gate A~E, release evidence |
| 추천 P2 운영 출시 | `HOLD` — 실제 CANARY 표본과 운영 승인 대기 |
| Backend | `83/83 PASS` |
| P1 Core | `17/17 PASS` |
| P2 Core | `23/23 PASS` |
| canonical DB | `journey-connect-db-v2.7/01..26` |

신규 트랙은 다음을 MUST 준수한다.

1. `jc-recommendation-core`의 기존 정책 상수, wire value, comparator, canonicalization, replay 의미를 덮어쓰지 않는다.
2. P1 profile source와 `recommendation_p1_profile_snapshot` 생성·정책 선택 경로를 승인 없이 교체하지 않는다.
3. P2 assignment·experiment exposure·dataset·evaluation·release evidence를 수정하거나 일반 플랫폼 계약으로 흡수하지 않는다.
4. 과거 snapshot, run, exposure, assignment, evaluation, gate, release decision은 append-only 증거로 유지한다.
5. Data projection을 도입하더라도 `recommendation-profile-input-v1`과 `experiment-outcome-input-v1`은 reconciliation과 전체 회귀 승인 전까지 shadow-only다.
6. P2 experiment exposure의 authoritative source는 `recommendation_p2_experiment_exposure`다.
7. 기존 P2 metric의 분자·분모·attribution window를 동일 version 안에서 변경하지 않는다.
8. 기존 P0/P1/P2 호환성 예외는 새 플랫폼 계약으로 위장하지 않고 versioned adapter로 격리한다.

### 2.2 현재 P0 식별자 호환성 예외

P0 recommendation backend는 POST 후보의 core `entityId`를 양의 10진수 문자열로 사용하고 저장 시 `BIGINT post_id`로 변환한다.

- 이 동작은 `recommendation-p0-post-id-v1` 호환성 예외로 유지한다.
- 다른 트랙은 이 숫자 문자열을 범용 엔티티 식별자로 사용하면 안 된다.
- 트랙 간 경계에서는 반드시 `entityRef`를 사용한다.
- 변경 시 기존 replay와 P0/P1/P2 run을 보존하는 새 adapter 또는 새 schema version이 필요하다.

### 2.3 현재 P1·P2 실행 경로 보호

현재 authoritative runtime은 다음과 같다.

```text
P1
recommendation_behavior_event + content facts + explicit preference
→ RecommendationP1ProfileSource
→ deterministic profile builder
→ recommendation_p1_profile_snapshot
→ P1 policy selection

P2
recommendation_p2_experiment_assignment
→ recommendation_p2_experiment_exposure
→ recommendation_run + recommendation_behavior_event + recommendation_p1_profile_snapshot
→ recommendation-evaluation-dataset-v1
→ evaluation / Gate A..E / release evidence
```

- 현재 P1/P2 경로는 Data Platform projection을 필수 선행조건으로 사용하지 않는다.
- 기존 P2 row, canonical bytes, dataset hash, release evidence를 rewrite하지 않는다.
- 새로운 source·consumer·identity scheme으로 전환하려면 새 version, reconciliation, replay, 전체 회귀 및 System Coordination 승인이 필요하다.

## 3. 아키텍처 경계와 의존 방향

```text
Frontend / External Client
          ↓
Backend API / Application Layer
          ↓
┌─────────────────────────────────────────────────────────┐
│ Data │ Intelligence │ Operations │ Reliability          │
└─────────────────────────────────────────────────────────┘
          ↓ stable ports, projections, event contracts
PostgreSQL / External Providers

Intelligence backend
          ↓
jc-recommendation-core

jc-recommendation-core
          ✕ Spring / JPA / HTTP / DB / system clock / env
```

### 3.1 공통 규칙

- 트랙 간 직접 write는 MUST NOT 한다.
- 다른 트랙 데이터는 승인된 application port, read projection 또는 versioned event로 소비한다.
- 같은 PostgreSQL을 사용하되 논리적 소유권을 분리한다.
- 신규 마이크로서비스, Kafka, Elasticsearch는 필수 전제가 아니다. 현재는 모듈형 모놀리스와 PostgreSQL을 기본으로 한다.
- `jc-recommendation-core`는 Intelligence 소유의 순수 계산 모듈이며 다른 트랙 인프라에 의존하지 않는다.
- IP-1 공통 계약의 승인된 기본 위치는 `jc-intelligence-contracts` / `com.jc.intelligence.contract`다.
- `jc-intelligence-contracts`는 Spring, JPA, HTTP, DB, system clock, environment에 의존하지 않는다.
- 기존 recommendation 객체를 공통 계약으로 읽는 compatibility adapter는 backend/application 경계에 두며 기존 core·row·snapshot을 수정하지 않는다.

---

## 4. 명명 및 직렬화 계약

| 영역 | 규칙 | 예시 |
|---|---|---|
| Java/Kotlin 타입 | PascalCase | `PlatformEventEnvelope` |
| Java/Kotlin 필드·메서드 | camelCase | `occurredAt` |
| JSON 필드 | camelCase | `schemaVersion` |
| DB 테이블·컬럼 | snake_case | `event_schema_version` |
| wire enum | lowercase snake_case | `search_result_click` |
| 계약·정책 ID | lowercase kebab-case + 버전 | `ranking-policy-v2` |
| API path | lowercase plural resource | `/api/v1/events` |

### 4.1 금지

- unversioned `latest`, `current`, `default`를 영속 식별자로 사용하지 않는다.
- locale 종속 문자열 비교를 계약 판정에 사용하지 않는다.
- enum ordinal을 DB나 JSON에 저장하지 않는다.
- Map 순서를 신뢰하여 hash 또는 fingerprint를 생성하지 않는다.

---

## 5. 식별자 계약

### 5.1 DB 내부 식별자

현재 핵심 도메인 PK는 양의 `BIGINT`를 유지할 수 있다.

- Java: `long`/`Long`
- DB: `BIGINT`
- 외부 API에서 내부 PK를 노출하는 기존 v1 API는 유지 가능하나, 신규 교차 시스템 추적에는 사용하지 않는다.

### 5.2 플랫폼 엔티티 참조

트랙 간 엔티티 참조는 `entityRef`를 사용한다.

```text
<entity-type>:<source-id>
```

예시:

```text
post:123
journey:42
place:987
crew:18
user:10
tag:healing
region:KR-11
itinerary:6a5e4f...
```

규칙:

- entity type은 레지스트리에 등록된 lowercase 값이어야 한다.
- `source-id`는 공백이 없어야 하며 최대 128자다.
- 동일 타입 내에서 안정적이어야 한다.
- 데이터베이스 PK가 같아도 타입이 다르면 다른 엔티티다.
- `entityRef`를 파싱하지 않고 문자열 일부를 비즈니스 의미로 추론하지 않는다.

### 5.3 실행·추적 식별자

| 식별자 | 목적 | 생성 주체 |
|---|---|---|
| `requestId` | 하나의 API 요청 | API/Application |
| `correlationId` | 여러 호출·이벤트의 상위 흐름 | 최초 진입 계층 |
| `causationId` | 직접 원인 이벤트·명령 | producer |
| `eventId` | 이벤트 유일성 | producer |
| `idempotencyKey` | 동일 명령 중복 방지 | client 또는 server |
| `sessionId` | 사용자 세션 결합 | server-derived 우선 |
| `runId` | 추천·검색·분석·생성 실행 | 실행 서비스 |
| `experimentId` | 실험 정의 | Reliability |
| `assignmentId` | 사용자·세션 실험 배정 | Reliability |
| `operationId` | 운영자 조치 | Operations |

신규 서버 생성 ID는 UUID v4 또는 현재 P0의 prefix + UUID 형식을 사용할 수 있다. 형식은 계약 버전 내에서 고정해야 한다.

### 5.4 신뢰 경계

- `userId`, 역할, 계정 상태는 클라이언트 값만 신뢰하지 않는다.
- 인증 주체는 JWT/보안 컨텍스트에서 서버가 결정한다.
- client-provided `sessionId`는 검증하거나 server-derived 값으로 대체한다.
- run·cursor·event는 사용자와 세션 소유권을 검증한다.

---

### 5.5 Identity scheme registry

| scheme ID | wire 형식 | 상태 | 용도 |
|---|---|---|---|
| `platform_subject_v1` | `subject:<opaque-id>` | ACTIVE | 신규 Data/Intelligence 교차 트랙 pseudonymous subject |
| `legacy_user_numeric_v1` | `user:<numeric-id>` | PROTECTED COMPATIBILITY | 현재 P2 assignment 및 기존 평가 증거 |

규칙:

- 두 scheme은 같은 문자열 공간이 아니며 자동 변환하지 않는다.
- 상호 연결은 제한된 `IdentityMappingReadPort`를 통해서만 수행한다.
- mapping은 단일 write owner, purpose binding, read allowlist, 접근 감사, version, invalidation 및 삭제 정책을 가져야 한다.
- mapping 실패를 anonymous 또는 다른 subject로 fallback하지 않는다.
- 기존 P2 row와 dataset hash material은 identity 정합화를 이유로 rewrite하지 않는다.

## 6. 시간 계약

### 6.1 저장과 전송

- 신규 시스템 컬럼은 `TIMESTAMPTZ`를 MUST 사용한다.
- Java/Kotlin은 `Instant`를 MUST 사용한다.
- JSON은 UTC ISO-8601 `Z` 형식을 MUST 사용한다.

```text
2026-07-19T03:15:30.123Z
```

- offset 없는 `LocalDateTime`은 교차 트랙 계약에서 금지한다.
- 기존 local datetime은 mapper에서 명시적 business timezone `Asia/Seoul`로 해석한 뒤 `Instant`로 변환한다.
- 화면 표시는 사용자 locale에 따라 변환할 수 있다.

이 규칙은 기존 README의 `YYYY-MM-DD HH:mm:ss` 표기 규칙보다 신규 machine contract에서 우선한다.

### 6.2 시간 필드 의미

| 필드 | 의미 |
|---|---|
| `occurredAt` | 실제 행위·사건 발생 시각 |
| `receivedAt` | 서버 수신 시각 |
| `createdAt` | 영속 레코드 생성 시각 |
| `referenceTime` | 결정론적 계산 기준 시각 |
| `effectiveFrom` | 정책 효력 시작 시각 |
| `evaluatedAt` | 평가 실행 시각 |

계산 코어는 시스템 현재 시각을 직접 읽지 않고 `referenceTime`을 입력받는다.

---

## 7. 버전 계약

### 7.1 버전 종류

| 버전 | 의미 |
|---|---|
| `contractVersion` | 교차 트랙 계약 |
| `schemaVersion` | payload/DB snapshot 구조 |
| `policyVersion` | 추천·검색·분류·운영 정책 |
| `metricDefinitionVersion` | 실험 지표 정의 |
| `canonicalizationVersion` | canonical bytes 생성 규칙 |
| `modelVersion` | AI/ML 모델 또는 provider 모델 |
| `promptVersion` | 콘텐츠 분석·일정 생성 프롬프트 |
| `producerBuildId` | 이벤트·결과 생성 빌드 |
| `evaluatorBuildId` | 검증·평가 빌드 |

### 7.2 변경 규칙

- 의미가 바뀌면 새 버전을 추가한다.
- 기존 버전의 상수·threshold·wire value를 수정하지 않는다.
- 과거 snapshot, run, event, evaluation, audit를 강제 재작성하지 않는다.
- consumer가 모르는 필드는 무시할 수 있지만, 모르는 필수 enum 값은 실패해야 한다.
- breaking change는 새 schema/contract version과 migration/replay 계획이 필요하다.
- 설정은 항상 명시적 버전을 선택해야 한다.

---

### 7.3 Contract registry — SC-1 reserved IDs

다음 계약 ID를 Intelligence 공통 계약용으로 예약한다.

| 계약 ID | 상태 | 의미 |
|---|---|---|
| `intelligence-run-v1` | RESERVED / IP-1 IMPLEMENTATION ALLOWED | immutable terminal run evidence |
| `intelligence-input-snapshot-v1` | RESERVED | immutable input snapshot |
| `intelligence-candidate-snapshot-v1` | RESERVED | ordered/domain-extended candidate evidence |
| `intelligence-output-snapshot-v1` | RESERVED | immutable output snapshot |
| `intelligence-feature-value-v1` | RESERVED | namespaced feature value and authority |
| `intelligence-explanation-v1` | RESERVED | user/operator/evaluation explanation separation |
| `model-inference-record-v1` | RESERVED | model/prompt/tool inference provenance |

예약은 DB table 생성, 기존 recommendation schema rename 또는 runtime consumer 전환을 의미하지 않는다. 최초 구현은 production DB를 변경하지 않는 Java contract type·validator·fixture·read-only compatibility adapter 범위로 제한한다.

## 8. 공통 이벤트 계약

### 8.1 이벤트 패밀리 분리

단일 거대 `EventType` enum을 만들지 않는다.

| eventFamily | 책임 |
|---|---|
| `user_behavior` | 조회, 클릭, 좋아요, 저장, 검색 등 사용자 행동 |
| `content_lifecycle` | 발행, 숨김, 복구, 삭제 등 콘텐츠 상태 변화 |
| `ai_analysis` | 콘텐츠 분석 요청·완료·실패 |
| `search_runtime` | 검색 실행·결과·선택 |
| `recommendation_runtime` | 추천 run·exposure·fallback |
| `experiment_runtime` | 배정·노출·성과 귀속 |
| `admin_audit` | 운영자 조치 |
| `trip_planner_runtime` | 일정 생성·재생성·승인 |
| `data_quality` | 검증 실패·격리·재처리 |

### 8.2 `platform-event-v1` 저장 envelope

신규 범용 이벤트 저장·전달은 다음 필드를 가진다.

```json
{
  "eventId": "event:...",
  "eventFamily": "user_behavior",
  "eventType": "view",
  "eventSchemaVersion": "platform-event-v1",
  "occurredAt": "2026-07-19T03:15:30.123Z",
  "receivedAt": "2026-07-19T03:15:30.200Z",
  "actorRef": "subject:opaque-example",
  "sessionId": "session:server-derived-example",
  "entityRef": "post:123",
  "correlationId": "request:...",
  "causationId": null,
  "idempotencyKey": "...",
  "producer": "jc-backend",
  "producerBuildId": "...",
  "payload": {}
}
```

조건부 필드는 이벤트 패밀리별 schema가 결정한다.

### 8.3 이벤트 불변 조건

- 저장된 이벤트는 append-only다.
- 같은 `idempotencyKey` + 같은 fingerprint는 dedupe한다.
- 같은 `idempotencyKey` + 다른 fingerprint는 conflict다.
- 원문 자유 텍스트, 인증 토큰, 비밀키는 이벤트 payload에 저장하지 않는다.
- 이벤트를 삭제해야 하는 개인정보 정책은 원문 최소화, pseudonymization, 별도 subject mapping으로 처리한다.
- 실패 재처리는 원본 이벤트를 수정하지 않고 처리 시도 레코드를 추가한다.

### 8.4 P0 recommendation event adapter

P0 `recommendation-behavior-event-v1`은 기존 API·DB 계약으로 유지한다.

```text
recommendation-behavior-event-v1
        ↓ versioned mapper
platform-event-v1 / user_behavior
```

mapper는 event type, post ID, run ID, server-derived actor/session, timestamp 의미를 보존해야 한다.

---

### 8.5 Exposure source registry

| registry ID | authoritative source | 목적 |
|---|---|---|
| `recommendation_general_exposure_v1` | `recommendation_exposure_event` 및 candidate rows | 일반 추천 페이지 노출 증거 |
| `recommendation_behavior_impression_v1` | `recommendation_behavior_event`의 `impression` | 행동 사실; 실험 분모로 자동 사용 금지 |
| `recommendation_p2_experiment_exposure_v1` | `recommendation_p2_experiment_exposure` | P2 실험 노출 및 평가 분모 |
| `search_exposure_v1` | 미구현 / ID 예약 | 향후 검색 노출 증거 |

이 네 source는 동일 의미가 아니다. 공통 envelope, 이름 또는 동일 사용자를 이유로 합산하지 않는다. 각 metric은 authoritative source, dedupe key, eligibility, attribution window를 명시해야 한다.

## 9. 데이터 소유권과 DB 계약

### 9.1 단일 write owner

모든 테이블·뷰·materialized dataset은 정확히 하나의 authorized physical writer와 하나의 semantic owner를 가진다. 둘이 다른 경우에는 System Coordination이 승인한 compatibility arrangement와 migration gate를 명시해야 한다.

| 데이터 | 현재 physical writer | semantic owner |
|---|---|---|
| 신규 `platform-event-v1` raw event / ingestion state | Data | Data |
| 기존 `recommendation_behavior_event`와 P0/P1 run/snapshot/general exposure/policy | recommendation backend / recommendation DB role | Intelligence |
| 현재 P2 assignment/experiment exposure/dataset/evaluation/release evidence | recommendation P2 package / recommendation DB role | Reliability |
| 검색 index metadata / AI feature result / itinerary run | Intelligence 후속 구현 | Intelligence |
| 신고·제재·복구·운영 정책·감사 | Operations | Operations |
| 공통 identity mapping | System Coordination이 지정할 단일 owner | System Coordination |
| contract/identity/exposure registry | 문서 registry | System Coordination |

다른 트랙은 직접 `INSERT/UPDATE/DELETE`하지 않는다. 현재 P2 physical write path는 P2 CLOSED 기준선의 보호된 호환 배치다. 이를 Reliability port 또는 별도 role로 이전하는 작업은 별도 High-risk proposal, DB role/grant 검증, canonical hash/replay 보존 및 전체 P0/P1/P2 회귀를 요구한다.

기존 `recommendation_behavior_event`는 Intelligence가 계속 write owner다. Data Platform은 이를 versioned adapter/projection으로 소비하며 신규 범용 이벤트 경로가 준비되더라도 승인 없이 dual-write 또는 source cutover하지 않는다.

`posts`처럼 기존 도메인 테이블에 moderation 컬럼이 공존하는 경우 Operations는 승인된 stored procedure/application service를 통해 해당 상태만 변경하며 게시글 본문 소유권까지 획득하지 않는다.

### 9.2 읽기 방식

우선순위:

1. application port/API
2. versioned read projection/view
3. versioned event/dataset snapshot
4. 승인된 repository query

ORM entity 공유와 타 트랙 repository 직접 호출은 SHOULD NOT 한다.

### 9.3 append-only 대상

다음 데이터는 기본적으로 append-only다.

- raw event
- recommendation/search/analysis/planner run
- snapshot과 canonical payload
- exposure
- experiment assignment
- evaluation result
- admin audit
- release/rollback decision

상태 수정이 필요한 경우 새 상태 전이 또는 superseding record를 추가한다.

### 9.4 DB 기준선과 변경 번호

- 현재 canonical runtime baseline은 `database/journey-connect-db-v2.7/01..26`이다.
- SQL `25..26`은 P2 evaluation/release 기준선으로 보호하며 수정하지 않는다.
- Flyway는 canonical baseline 운영 결정 전까지 임의로 활성화하지 않는다.
- 각 트랙은 독자적으로 migration 번호를 선택하지 않는다.
- 다음 신규 SQL은 System Coordination이 `26` 이후 sequence와 target DB version을 배정한 뒤 작성한다.
- DB 변경은 DDL, 권한, 데이터 migration, rollback/forward-fix, smoke test를 함께 제출한다.
- schema 변경 후 Hibernate validation, SQL smoke, PostgreSQL integration test와 영향받는 P0/P1/P2 회귀를 수행한다.

---

## 10. API 계약

### 10.1 경로와 응답

- 신규 API는 `/api/v1/...`를 사용한다.
- 정상 응답은 기존 `ApiResponse<T>` 형식을 유지한다.
- 오류 응답은 기존 `ApiErrorResponse` 형식을 유지한다.
- 페이지 응답은 기존 `PageResponse` 또는 `CursorPageResponse`를 사용한다.
- 내부 stack trace, SQL, provider raw error를 외부에 노출하지 않는다.

### 10.2 오류 코드

- 오류 코드는 `UPPER_SNAKE_CASE`로 안정적으로 유지한다.
- message는 사용자 표시용이며 client 분기의 기준이 아니다.
- validation field error는 `errors` map에 담는다.
- 동일 오류 의미의 HTTP status와 code를 트랙마다 다르게 만들지 않는다.

### 10.3 idempotency 적용 대상

다음 command는 idempotency를 MUST 지원한다.

- 행동 이벤트 수집
- AI 분석 요청
- 여행 일정 생성·재생성
- 운영자 상태 변경
- 외부 provider side effect
- 실험 승격·rollback

일반적인 단순 조회에는 요구하지 않는다.

---

## 11. 콘텐츠·AI 계약

### 11.1 원문과 파생값 분리

- 사용자 원문은 파생 AI 결과로 덮어쓰지 않는다.
- AI 결과는 별도 versioned result로 저장한다.
- 최소 필드: `analysisRunId`, `schemaVersion`, `modelVersion`, `promptVersion`, `sourceContentVersion`, `status`, `confidence`, `createdAt`.
- 사용자 확정값, 운영자 확정값, AI 추정값의 source를 분리한다.
- 추천·검색은 source priority policy를 통해 어떤 값을 사용할지 결정한다.

### 11.2 상태

AI 실행 공통 상태:

```text
queued → running → succeeded | failed | quarantined
```

- retry는 새로운 attempt로 기록한다.
- 부분 결과는 `succeeded`로 위장하지 않는다.
- schema validation 실패 결과는 downstream 검색·추천에 공급하지 않는다.

### 11.3 여행 일정 생성

- 생성 결과는 `itineraryRunId`, 입력 snapshot, 장소 snapshot, 제약조건, model/prompt version을 기록한다.
- 영업시간·이동시간·비용 같은 외부 사실은 source와 조회 시각을 기록한다.
- 지도·장소 provider 실패 시 추정값을 사실처럼 표시하지 않는다.
- 사용자가 확정한 일정과 AI draft를 분리한다.

---

## 12. 운영·정책 통제 계약

- Operations는 콘텐츠의 공개 가능성과 추천·검색 노출 가능성을 통제할 수 있다.
- 운영자 조치는 `operationId`, actor, reason code, before/after, occurredAt를 가진 감사 로그를 남긴다.
- 이미 생성된 추천 run/snapshot을 수정하지 않는다. 후속 read에서 현재 visibility를 재검증하거나 새 run을 생성한다.
- 추천 제외·승격은 정책 레코드로 관리하고 ranking policy 자체를 몰래 수정하지 않는다.
- 관리자 권한은 일반 app role과 분리한다.
- 신고 원문과 개인정보는 최소 권한으로 제한한다.

---

## 13. 실험·품질·출시 계약

### 13.1 실험

- 배정은 결정론적이며 `experimentId`, `experimentVersion`, subject, assignment unit, variant, assignedAt를 기록한다.
- 동일 실험 버전에서 동일 subject는 동일 variant를 받아야 한다.
- 추천 정책, 검색 정책, UI 변경이 동시에 섞이면 별도 factorial 설계가 없는 한 하나의 실험으로 묶지 않는다.
- 노출되지 않은 배정은 성과 분모로 자동 포함하지 않는다.

### 13.2 지표

모든 지표는 다음을 가진다.

- `metricDefinitionVersion`
- 분자·분모
- attribution window
- deduplication rule
- eligibility rule
- segment dimensions
- guardrail 여부

현재 보호된 `recommendation-metrics-v1` 의미:

- `engagement_rate`: valid P2 experiment exposure 이후 7일 이내 `click`, `like`, `save`, `share` 중 하나가 존재하는 exposed eligible subject 비율
- `fallback_rate`: 결속된 exposed distinct run 중 `recommendation_run.run_status = fallback`인 run 비율
- `view`, 일반 behavior impression, 일반 recommendation exposure, `hide`, `report`를 동일 metric version에 임의 추가하지 않는다.

### 13.3 게이트

```text
Gate A: 계약·계산·구조 무결성
Gate B: 데이터 품질
Gate C: 증거량 충분성
Gate D: 성과 개선과 guardrail
Gate E: 운영 승인
```

어느 하나도 단일 종합 점수로 대체하지 않는다.

### 13.4 출시 상태

```text
DRAFT → SHADOW → CANARY → LIVE
                     ↘ HOLD
LIVE → ROLLED_BACK
```

추천 P0의 OFF는 실행이 생성되지 않는 설정 상태이며 run mode enum에 억지로 추가하지 않는다.

현재 P2는 `HOLD`다. `CANARY` 또는 `LIVE` 전환은 Reliability의 release evidence와 Operations의 승인·감사 경로가 모두 준비된 경우에만 가능하다.

---

## 14. 관측성과 로그 계약

공통 구조화 로그 필드:

- timestamp
- level
- service/module
- requestId
- correlationId
- actorRef 또는 pseudonymous subject
- runId/operationId/experimentId 중 해당 값
- errorCode
- durationMs
- buildId

금지:

- JWT/access token/refresh token
- DB password/API key
- canonical payload 전체
- 사용자 자유 텍스트 전체
- 정밀 위치 원문을 불필요하게 기록
- 외부 응답에 stack trace 노출

metric 이름은 unit을 포함한다. 예: `_seconds`, `_milliseconds`, `_total`, `_bytes`.

---

## 15. 변경 위험도와 필수 검증

| 위험도 | 예시 | 필수 게이트 |
|---|---|---|
| Low | 문서, 비동작 fixture, 설명 | 링크·형식 검증 |
| Medium | 새 endpoint, 새 projection, 새 optional field | unit + integration + contract test |
| High | identity, event semantics, canonicalization, policy selector, experiment assignment, moderation state, DB 권한 | 전체 회귀 + PostgreSQL + replay/golden + 독립 검토 + 호환성 보고 |

다음 변경은 항상 High다.

- 기존 wire enum 의미 변경
- 기존 정책 상수 변경
- event fingerprint/idempotency 변경
- snapshot canonicalization 변경
- run ownership/cursor binding 변경
- 개인정보 보존·삭제 방식 변경
- 운영자 권한과 moderation predicate 변경

---

## 16. 공통 완료 정의

트랙 기능은 다음을 모두 만족해야 완료다.

```text
[ ] 소유 트랙과 계약 ID가 명시됨
[ ] 입력·출력·상태·오류·버전이 문서화됨
[ ] 타 트랙 write 없음
[ ] 시간은 Instant/TIMESTAMPTZ/UTC 사용
[ ] server-derived identity와 권한 검증
[ ] idempotency/retry/replay 전략 명시
[ ] 개인정보·로그 정책 검토
[ ] DB 변경 번호와 owner 승인
[ ] 단위·통합·계약 테스트 통과
[ ] 기존 P0/P1/P2 추천 회귀 없음
[ ] 변경 문서와 handoff 보고서 작성
[ ] rollback 또는 forward-fix 경로 존재
```

---

## 17. 절대 금지 목록

- P0/P1/P2 정책 또는 과거 결과 덮어쓰기
- 한 트랙이 다른 트랙 테이블에 직접 write
- DB row/JPA entity를 추천 코어 타입으로 직접 전달
- core에서 현재 시각, DB, HTTP, 환경변수 읽기
- 범용 `latest` 정책 사용
- event type을 문자열 추측으로 처리
- idempotency conflict를 정상 dedupe로 처리
- AI 결과로 사용자 원문 덮어쓰기
- 운영자 조치의 감사 로그 생략
- 실험 배정, 일반 노출, behavior impression, P2 experiment exposure 혼동
- 테스트 실패 후 기대값 일괄 갱신
- canonical DB/Flyway를 트랙별로 따로 운영
- restricted identity mapping 없이 `platform_subject_v1`과 `legacy_user_numeric_v1` 결합
- shadow projection을 authoritative runtime source로 무승인 전환
