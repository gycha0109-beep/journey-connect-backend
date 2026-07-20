# IP-1 Common Contract Types, Validation & Recommendation Compatibility Adapter

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-1` |
| 상태 | `IMPLEMENTED / PARTIALLY VERIFIED` |
| 소유 트랙 | Intelligence Platform |
| 선행 단계 | `SC-1 COMPLETE`, `IP-0 COMPLETE` |
| 신규 모듈 | `jc-intelligence-contracts` |
| 기본 패키지 | `com.jc.intelligence.contract` |
| 기준 추천 | `P0 CLOSED / P1 CLOSED / P2 TECHNICAL CLOSED / P2 PRODUCTION HOLD` |
| 기준 DB | `journey-connect-db-v2.7/01..26` |
| DB/SQL 영향 | 없음 |
| 기존 추천 production 영향 | 없음 |
| 기준일 | 2026-07-19 |

`PARTIALLY VERIFIED`는 신규 pure-Java 계약·adapter와 기존 recommendation core 회귀를 실제 실행으로 검증했으나, 실행 환경에서 Gradle 8.14.5 배포본을 내려받을 수 없어 Spring backend 전체 Gradle test를 재실행하지 못했다는 의미다. 기존 Batch18의 Backend `83/83 PASS`는 보호 기준선이며 이번 단계의 새 실행 결과로 재표기하지 않는다.

## 2. 목적과 범위

IP-1은 IP-0의 common evidence 계약을 Java 21 타입과 validator로 구현하고 기존 P0/P1/P2를 변경하지 않는 read-only compatibility 계층을 추가한다.

공통화한 범위:

- immutable terminal run evidence
- input/candidate/output snapshot reference
- feature namespace와 authority
- user/operator/evaluation/debug explanation
- model inference provenance
- identity scheme와 exposure source registry
- exact/semantic/evidence replay
- stable validation error code
- JSON fixture와 round-trip contract

공통화하지 않은 의미:

- recommendation score와 candidate/terminal partition
- search relevance
- content confidence
- trip constraint result
- domain metric
- 일반 추천 exposure, behavior impression, P2 experiment exposure, search exposure

## 3. 의존 방향

```text
jc-backend
    ├─→ jc-recommendation-core
    └─→ jc-intelligence-contracts

jc-intelligence-contracts
    ✕ jc-backend
    ✕ jc-recommendation-core
    ✕ Spring / JPA / HTTP / DB
    ✕ system clock / environment
```

기존 `com.jc.backend.recommendation/**`와 `jc-recommendation-core/src/main/**`는 수정하지 않았다.

## 4. 변경 요약

| 범위 | 수량 | 내용 |
|---|---:|---|
| contract module production Java | 60 | 7개 contract, enum/value object, registry, validator, JSON codec |
| backend compatibility production Java | 10 | run/snapshot/entity/P1/P2 adapter와 metric protection |
| contract module test Java | 1 | dependency-free executable contract test |
| backend compatibility test Java | 3 | shared assertions, manual executable, JUnit wrapper |
| JSON fixture | 11 | 공통 9개, compatibility/metric 2개 |
| IP-1 문서 | 2 | 본문, handoff |

수정:

- `jc-backend/settings.gradle.kts`: 신규 module 등록
- `jc-backend/build.gradle.kts`: module dependency와 verification task 추가
- `docs/platform/intelligence/README.md`: IP-1 index 추가

## 5. 공통 Java 계약

SC-1 예약 ID와 동일한 타입을 구현했다.

- `intelligence-run-v1` → `IntelligenceRunV1`
- `intelligence-input-snapshot-v1` → `IntelligenceInputSnapshotV1`
- `intelligence-candidate-snapshot-v1` → `IntelligenceCandidateSnapshotV1`
- `intelligence-output-snapshot-v1` → `IntelligenceOutputSnapshotV1`
- `intelligence-feature-value-v1` → `IntelligenceFeatureValueV1`
- `intelligence-explanation-v1` → `IntelligenceExplanationV1`
- `model-inference-record-v1` → `ModelInferenceRecordV1`

모든 record는 생성 시 불변조건을 검사하며 collection은 defensive copy 후 unmodifiable view로 보존한다.

### 5.1 Run

- terminal status: `succeeded`, `fallback`, `failed`
- `startedAt <= completedAt`
- succeeded/fallback은 output snapshot 필수
- fallback은 `fallbackCode`, failed는 `failureCode` 필수
- request/correlation은 request 기반 실행에서만 사용하며 없는 값을 생성하지 않음
- deterministic recommendation만 증거 충족 시 exact replay
- model/prompt version은 각각 실제 적용된 경우에만 기록
- recommendation adapter를 위해 `domainRunMode`와 `surface`를 보존

### 5.2 Snapshot

- 기존 canonical payload 전체를 common object에 복제하지 않음
- snapshot ID/schema/source/hash/build를 immutable evidence로 처리
- candidate/result list의 순서를 보존
- score/relevance/confidence/constraint를 하나의 숫자로 합치지 않음
- 기존 recommendation bytes/hash는 검증만 하고 재계산하지 않음

### 5.3 Feature

authority:

- `source_fact`
- `observed_behavior`
- `derived_aggregate`
- `model_inference`
- `operator_override`

source fact/observed behavior에 가짜 confidence를 넣지 않는다. P1 feature ID와 vocabulary version은 adapter에서 그대로 보존한다.

### 5.4 Explanation

audience:

- `user`
- `operator`
- `evaluation`
- `debug`

user explanation에 stack trace, raw prompt, token, 내부 score component를 넣지 못하도록 검증한다. 설명이 없는 경우 빈 설명을 생성하지 않는다.

### 5.5 Model inference

- IP-1은 실제 모델을 호출하지 않음
- model/prompt/tool/parameter/input/output/safety/build/latency/usage/result hash를 계약으로만 정의
- model inference에 exact replay를 자동 부여하지 않음
- fixture는 `evidence_replay`

## 6. Validator와 JSON

안정적 오류 코드:

- `INTELLIGENCE_CONTRACT_ID_INVALID`
- `INTELLIGENCE_ENTITY_REF_INVALID`
- `INTELLIGENCE_SUBJECT_REF_INVALID`
- `INTELLIGENCE_VERSION_INVALID`
- `INTELLIGENCE_TIME_RANGE_INVALID`
- `INTELLIGENCE_HASH_INVALID`
- `INTELLIGENCE_RUN_STATUS_INVALID`
- `INTELLIGENCE_REPLAY_CLASS_INVALID`

추가로 snapshot/feature/explanation/inference/enum/privacy 세부 오류 코드를 정의했다.

JSON contract:

- camelCase field
- lowercase snake_case wire enum
- UTC ISO-8601 `Instant`
- enum ordinal 미사용
- unknown optional field 무시
- unknown required enum fail-closed
- duplicate key 거부
- ordered collection round-trip
- 민감 정보 fixture 금지
- 새로운 canonical hash 알고리즘 미도입
- Map iteration order를 hash 근거로 사용하지 않음

## 7. Identity와 exposure registry

### 7.1 Identity

| scheme | wire | 상태 |
|---|---|---|
| `platform_subject_v1` | `subject:<opaque-id>` | ACTIVE |
| `legacy_user_numeric_v1` | `user:<positive-numeric-id>` | PROTECTED COMPATIBILITY |

자동 변환, mapping fallback, 실제 join은 구현하지 않았다.

### 7.2 Exposure

| ID | 의미 |
|---|---|
| `recommendation_general_exposure_v1` | 일반 recommendation page exposure |
| `recommendation_behavior_impression_v1` | behavior fact |
| `recommendation_p2_experiment_exposure_v1` | P2 experiment exposure와 평가 분모 authority |
| `search_exposure_v1` | RESERVED, runtime 미구현 |

source 합산을 금지한다.

## 8. Recommendation compatibility adapter

### 8.1 Run

`RecommendationRunCompatibilityInputV1`은 기존 recommendation package를 수정하지 않고 application boundary가 제공할 immutable read model이다.

adapter가 보존하는 값:

- run ID
- request/correlation
- status
- mode
- surface
- `user:<id>`
- reference/start/completion time
- ranking input/output snapshot ref
- ranking policy와 feature vocabulary
- build
- fallback/failure partition
- replay evidence
- 실제 확인 가능한 experiment/exposure ref

없는 model/prompt/entity/experiment 값은 추측하지 않는다. DB write와 source mutation은 없다.

### 8.2 Snapshot role

| 기존 kind | common role | 처리 |
|---|---|---|
| `ranking_input_v1` | input | adapter-compatible |
| `diversity_metadata_v1` | candidate dependency | domain extension 유지 |
| `exploration_metadata_v1` | candidate dependency | domain extension 유지 |
| `ranking_result_v1` | output | 순서/fingerprint 비변경 |
| `exposure_event_v1` | exposure evidence | 미예약 output contract로 오표기하지 않음 |

### 8.3 Entity/P1/P2

- positive numeric POST ID만 경계에서 `post:<id>`로 변환
- 기존 core entityId, DB `post_id`, replay bytes 변경 없음
- P1 profile의 segment/policy/vocabulary/reference time/sorted unique signals/fingerprint 보존
- Data P1 shadow projection은 authoritative=false
- P2 assignment의 `user:<id>`, variant, assignedAt 보존
- P2 physical writer는 recommendation, semantic owner는 Reliability로 표시
- P2 exposure의 assignment/run/user/session/variant/fingerprint 결속과 denominator authority 보존
- identity mapping과 physical ownership migration 없음

## 9. Compatibility matrix

| source | classification |
|---|---|
| `recommendation_run` | adapter_compatible |
| snapshot 5종 | adapter_compatible |
| candidate/terminal partition | intentionally_domain_specific |
| general exposure/behavior event | intentionally_domain_specific |
| P1 profile | adapter_compatible |
| P1 assignment/comparison | intentionally_domain_specific |
| P2 assignment | adapter_compatible |
| P2 experiment exposure | exact_compatible |
| P2 dataset/evaluation/gates | intentionally_domain_specific |
| P0 numeric post ID | future_version_migration_required |

JSON fixture와 executable test가 matrix를 고정한다.

## 10. P2 metric protection

```text
engagement_rate
- exposure source: recommendation_p2_experiment_exposure
- window: exposure 이후 7일
- allowlist: click, like, save, share
- excluded: view, impression, hide, report
- unit: exposed eligible subject

fallback_rate
- numerator: bound exposed distinct run 중 run_status=fallback
- denominator: bound exposed distinct run
- unit: run
```

새 metric calculator는 구현하지 않았다.

## 11. 단계별 누적 기록

### IP-1.1 Baseline and Dependency Inventory

- 목적: 최신 SC-1/IP-0/DP-0와 Batch18 실제 구조 대조
- 확인: Gradle root `jc-backend`, sibling core, Java 21, wrapper 8.14.5
- 확인: snapshot kind 5종, run mode/status, P1 direct source, P2 exposure authority
- 작업 전 protected file 320개 SHA-256 기록
- 리스크: Gradle distribution/dependency cache 부재

### IP-1.2 Contract Module Foundation

- `jc-intelligence-contracts` 생성
- `java-library`, Java 21
- backend/core/framework 역의존 없음
- dependency-free contract executable 등록

### IP-1.3 Common Records and Enums

- 7개 record와 enum/value object 구현
- domain score/metric/exposure 의미 분리
- terminal immutable evidence 원칙 반영

### IP-1.4 Validation and Error Contract

- ID/ref/subject/version/hash/time/run/replay validator
- stable code
- fail-closed enum
- system clock 비의존

### IP-1.5 JSON Fixtures and Serialization Contract

- common fixture 9개
- round-trip, UTC, enum wire, unknown field, duplicate key, immutability 검증
- secret scan
- 신규 canonicalization 미구현

### IP-1.6 Recommendation Compatibility Adapter

- backend 신규 intelligence compatibility package
- 기존 recommendation package/core/store 수정 없음
- read-only input/view/adapter 구현

### IP-1.7 Compatibility and Authority Fixtures

- compatibility matrix
- identity schemes
- exposure sources
- P2 metric protection
- source aggregation 금지

### IP-1.8 Regression Validation

| 대상 | 실제 결과 |
|---|---|
| contract main/test compile | PASS, Java 21 `-Xlint:all -Werror` |
| contract executable | PASS, 739 assertions |
| adapter main/test compile | PASS, Java 21 `-Xlint:all -Werror` |
| adapter executable | PASS, 226 assertions |
| recommendation core foundation/Wave1..7/golden/isolation | PASS |
| P1 Core | PASS, 17 scenarios |
| P2 Core | PASS, 23 scenarios |
| Gradle contract/backend/root | BLOCKED, Gradle distribution DNS 실패 |
| PostgreSQL | 미실행, DB/SQL 비변경 |

### IP-1.9 Self Review and Final Hardening

#### 리뷰 1 — 계약·아키텍처

발견 4 / 수정 4 / 보류 0:

1. request/correlation 과제약 → conditional
2. model/prompt 동시 강제 → 독립 optional
3. run mode/surface 유실 → common evidence에 보존
4. exposure snapshot을 output contract로 오표기 가능 → role만 제공

#### 리뷰 2 — 구현·회귀·보안

발견 3 / 수정 3 / 보류 0:

1. null 값이 먼저 온 duplicate JSON key 검출 누락 → `containsKey` 기반 거부
2. P1 signal sorted unique 불변조건 누락 → 명시 검증
3. 관련 부정 테스트 누락 → duplicate/model-only/optional trace/exposure/signal 테스트 추가

보완 후 contract 739개, adapter 226개 assertion과 recommendation core executable 전체를 재실행해 PASS했다.

## 12. 보호 검증

작업 전 해시 목록과 동일한 320개 path를 작업 후 재계산했으며 exact diff가 `PASS`였다.

- `jc-recommendation-core/src/main/**`
- 기존 `jc-backend/src/main/java/com/jc/backend/recommendation/**`
- `database/journey-connect-db-v2.7/01..26`
- 기존 core test resources

신규 module/adapter/fixture/docs는 비교 대상에서 분리했다.

- protected path: 320개
- before/after line: 320/320
- diff: 0 line
- 결과: `PROTECTED_SHA256_EXACT_MATCH: PASS`

## 13. Blocker와 완료 판정

Gradle wrapper가 요구하는 `gradle-8.14.5-bin.zip` 완성본이 cache에 없고 `services.gradle.org` DNS 접근이 차단됐다. 따라서 다음을 새로 실행하지 못했다.

- `:jc-intelligence-contracts:check`
- `:jc-backend:test`
- `p1Verification`
- `p2Verification`
- root `test`

dependency-free `javac/java` 검증은 신규 코드와 core 회귀를 검증하지만 Spring context, JUnit engine, Testcontainers/PostgreSQL을 대체하지 않는다.

```text
IP-1 구현: COMPLETE
pure-Java 검증: PASS
전체 Gradle 회귀: HOLD
IP-2 진입: HOLD
```
