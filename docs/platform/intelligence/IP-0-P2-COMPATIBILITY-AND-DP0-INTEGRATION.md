# IP-0 P2 Compatibility and DP-0 Integration

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-0-p2-compatibility-dp0-integration-v1` |
| 상태 | `ACTIVE / PROTECTIVE` |
| 기준 프로젝트 | `Journey-Connect-P2-Final-Validation-Batch18` |
| 기준 DB | `journey-connect-db-v2.7/01..26` |
| 목적 | 기존 P1/P2 비회귀와 DP-0 shadow bridge 경계 |

## 2. P1/P2 current-state inventory

### 2.1 P0/P1 recommendation evidence

현재 recommendation backend는 다음 보호 객체를 사용한다.

| 객체 | 현재 역할 |
|---|---|
| `recommendation_snapshot` | canonical bytes/hash 기반 immutable snapshot |
| `recommendation_run` | mode/status, policy vector, snapshot refs, fingerprint |
| `recommendation_run_candidate` | ordered ranked candidates |
| `recommendation_run_terminal_candidate` | excluded/not-applicable partition |
| `recommendation_exposure_event` | 일반 page exposure evidence |
| `recommendation_exposure_candidate` | exposed candidate positions |
| `recommendation_behavior_event` | behavior source, P1/P2 outcome source |
| `recommendation_replay_audit` | replay verification evidence |
| `recommendation_user_preference` | explicit preference |
| `recommendation_p1_profile_snapshot` | deterministic profile result |
| `recommendation_p1_policy_assignment` | baseline/treatment run binding |
| `recommendation_p1_comparison` | baseline/treatment ranking comparison |

현재 snapshot kind:

- `ranking_input_v1`
- `diversity_metadata_v1`
- `exploration_metadata_v1`
- `ranking_result_v1`
- `exposure_event_v1`

현재 run mode/status:

- mode: `shadow`, `canary`, `live`
- status: `succeeded`, `fallback`, `failed`

### 2.2 P1 profile path

```text
recommendation_behavior_event
+ posts / regions / post_tags / tags
+ recommendation_user_preference
  → RecommendationP1ProfileSource
  → BehaviorProfileBuilder
  → recommendation_p1_profile_snapshot
  → P1PolicySelector
  → P1 treatment run / assignment / comparison
```

보호 의미:

- segment: `empty`, `explicit_only`, `emerging`, `established`
- profile policy version
- feature vocabulary version (`feature-vocabulary-v2` 포함)
- reference time
- input/accepted/ignored/duplicate partition
- sorted unique signal list
- SHA-256 fingerprint

### 2.3 P2 runtime path

```text
recommendation_p2_experiment_assignment
  → recommendation_p2_experiment_exposure
  → recommendation_run + recommendation_behavior_event
  → latest eligible recommendation_p1_profile_snapshot
  → RecommendationP2ObservationSource
  → recommendation-evaluation-dataset-v1
  → recommendation_p2_dataset_snapshot
  → evaluation run / metric result / Gate A..E
  → optional release transition decision
```

현재 versions:

- dataset: `recommendation-evaluation-dataset-v1`
- metric: `recommendation-metrics-v1`
- evaluation policy: `recommendation-evaluation-policy-v1`

## 3. 기존 구현과 Common Contract 호환성

| 현재 객체/의미 | 분류 | 공통 계약 대응 | 처리 |
|---|---|---|---|
| `recommendation_run` | adapter compatible | `IntelligenceRun` | 기존 row 비변경, adapter projection |
| recommendation snapshot 5종 | adapter compatible | input/candidate/output snapshot | kind별 역할 mapping |
| candidate/terminal partition | intentionally domain-specific | CandidateSnapshot extension | partition 의미 보존 |
| `recommendation_exposure_event` | intentionally domain-specific | recommendation general exposure | 별도 authority 유지 |
| `recommendation_behavior_event` | intentionally domain-specific | behavior fact | Data adapter source, 직접 통합 금지 |
| P1 profile snapshot | adapter compatible | FeatureValue/profile input reference | 기존 feature IDs 보존 |
| P1 policy assignment/comparison | domain-specific | run dependency/evaluation evidence | 공통 run에 억지 병합 금지 |
| P2 experiment assignment | adapter compatible | Reliability assignment ref | current physical path 보호 |
| P2 experiment exposure | exact semantic authority | experiment exposure hook | 분모 authority 유지 |
| P2 dataset/evaluation/gates | intentionally domain-specific | Reliability evidence | metric 의미 보존 |
| P0 numeric post core ID | future version migration required | `entityRef=post:<id>` | boundary adapter 필요 |

## 4. Compatibility adapter 후보

### 4.1 `RecommendationRunToIntelligenceRunAdapterV1`

입력:

- `recommendation_run`
- 관련 snapshot refs
- P1 assignment/P2 assignment refs가 있으면 read-only join

출력:

- `intelligence-run-v1` compatible view/DTO

규칙:

- `runType = recommendation`
- 기존 `run_mode`, `run_status`, policy fields를 그대로 매핑
- 존재하지 않는 common field를 추측하지 않는다.
- `referenceTime`은 기존 run의 값
- `replayClass = exact_replay`는 Batch18에서 보호된 deterministic path에만 적용
- adapter output은 기존 run authority를 대체하지 않는다.

### 4.2 `RecommendationSnapshotRoleAdapterV1`

| existing kind | common role |
|---|---|
| `ranking_input_v1` | IntelligenceInputSnapshot |
| `diversity_metadata_v1` | CandidateSnapshot dependency |
| `exploration_metadata_v1` | CandidateSnapshot dependency |
| `ranking_result_v1` | IntelligenceOutputSnapshot |
| `exposure_event_v1` | exposure evidence snapshot |

하나의 snapshot을 다른 kind로 rewrite하지 않는다.

### 4.3 `RecommendationEntityRefAdapterV1`

현재 P0/P1 candidate source ID는 numeric post ID와 저장 `post` entity type을 사용한다. cross-track boundary에서만 `post:<id>`를 생성한다. 기존 replay bytes와 candidate row를 변경하지 않는다.

## 5. DP-0 shadow-only integration

### 5.1 P1 bridge

```text
validated behavior stream
  → user behavior aggregate
  → recommendation-profile-input-v1 [shadow]
```

현재 P1 runtime source를 대체하지 않는다.

전환 게이트:

1. event coverage/order/dedupe exact definition
2. region/tag/content facts projection 승인
3. current source ↔ shadow input fixture reconciliation
4. P1 Core 17개 PASS
5. P0/P1 전체 회귀 PASS
6. profile replay/fingerprint 비회귀
7. 새 profile source/schema version
8. Intelligence + System Coordination 승인

### 5.2 P2 bridge

```text
assignment read contract
+ recommendation_p2_experiment_exposure [authoritative]
+ canonical outcomes
+ run/profile refs
  → experiment-outcome-input-v1 [shadow]
```

현재 `recommendation-evaluation-dataset-v1`을 대체하지 않는다.

전환 게이트:

1. assignment/exposure/run/user/session/variant binding exact match
2. observed window boundaries exact match
3. stale unexposed assignment exclusion exact match
4. engagement semantics exact match
5. fallback semantics exact match
6. one observation per experiment/version/subject dedupe exact match
7. metric/dataset version binding
8. canonical bytes/hash reproducibility
9. P2 Core 23개 + backend P2 gate PASS
10. release evidence/state transition 비회귀
11. Reliability + System Coordination 승인

## 6. Source authority matrix

| meaning | authoritative source | physical writer | semantic owner | consumer | version owner | migration gate |
|---|---|---|---|---|---|---|
| P0/P1 behavior fact | `recommendation_behavior_event` | recommendation backend / `jc_recommendation` | Intelligence current compatibility | P1 profile, P2 outcomes, Data adapter | Intelligence current contract | new event/source version + reconciliation |
| 일반 추천 page exposure | `recommendation_exposure_event` + candidates | recommendation backend | Intelligence | operational view, general analytics | Intelligence | explicit exposure contract migration |
| behavior `impression` | `recommendation_behavior_event` row | recommendation backend | behavior fact; Data future canonicalization | behavior analytics only | current P0 contract/Data adapter | no denominator reuse without metric contract |
| P2 experiment exposure | `recommendation_p2_experiment_exposure` | recommendation P2 path / `jc_recommendation` | Reliability semantic | P2 evaluation, Data shadow bridge | Reliability contract, current physical implementation | full P2 binding/regression + approval |
| P2 assignment | `recommendation_p2_experiment_assignment` | recommendation P2 path | Reliability | runtime/P2 evaluation | Reliability | deterministic equivalence + migration plan |
| P1 profile result | `recommendation_p1_profile_snapshot` | recommendation P1 path | Intelligence | P1 policy, P2 segment | Intelligence | new source/schema + P1 replay |
| P2 fallback | `recommendation_run.run_status` for bound exposed runs | recommendation backend | Intelligence status; metric semantics Reliability | P2 evaluator | current run + metric versions | metric version change required |
| P2 evaluation dataset | `recommendation_p2_dataset_snapshot` | recommendation P2 evaluation path | Reliability | P2 evaluator/release | Reliability | new dataset schema/consumer version |
| P2 release evidence | P2 evaluation/gate/release tables | recommendation P2 path | Reliability | Operations/admin read | Reliability | High-risk ownership migration |
| Data P1 shadow input | `recommendation-profile-input-v1` | Data future | Data facts / Intelligence consumption semantics | comparison only until approval | Data schema + Intelligence feature semantics | P1 gate set |
| Data P2 shadow input | `experiment-outcome-input-v1` | Data future | Data facts / Reliability metric semantics | comparison only until approval | Data schema + Reliability metric | P2 gate set |
| future search exposure | future `search-exposure-v1` | Intelligence Search | Intelligence evidence; metric semantics Reliability | search evaluation | Search + Reliability | new contract approval |

## 7. P2 metric 의미 보호

### 7.1 `engagement_rate`

```text
분자:
exposure 이후 7일 이내 click, like, save, share 중 하나가 존재하는
exposed eligible subject

분모:
valid assignment와 최소 1개의 bound recommendation P2 experiment exposure가 있는 subject

dedupe:
experimentId + experimentVersion + subjectRef당 1 observation
```

다음을 임의 추가하지 않는다.

- `view`
- 일반 behavior impression
- 일반 recommendation exposure
- hide/report
- exposure 이전 행동

### 7.2 `fallback_rate`

```text
분자:
bound exposed recommendation run 중 run_status=fallback인 distinct run

분모:
bound exposed recommendation distinct run
```

subject 단위 engagement와 run 단위 fallback 의미를 혼합하지 않는다.

## 8. Identity mapping 필요성

### 8.1 충돌

- 신규 Data actor: `subject:<opaque-id>`
- 현재 P2 subject: `user:<numeric-id>`이며 DB check로 user_id와 결속

### 8.2 제한된 mapping 계약 최소 조건

- 단일 write owner
- read allowlist와 purpose binding
- 요청 actor/service audit
- mapping version
- created/effective/invalidated timestamps
- delete/tombstone/cryptographic erasure 정책
- historical evaluation hash material 보호
- bulk export 금지
- cache TTL과 revocation

### 8.3 소비 규칙

- mapping 실패를 anonymous 또는 다른 subject로 fallback하지 않는다.
- 기존 P2 row를 rewrite하지 않는다.
- opaque identity를 사용하는 P2 dataset은 새 schema version을 사용한다.
- mappingRef는 common input lineage에 기록하되 raw numeric ID를 일반 log에 복제하지 않는다.

## 9. Physical owner와 semantic owner

| 영역 | current physical owner/write path | semantic owner | future target owner | migration prerequisite |
|---|---|---|---|---|
| assignment | recommendation P2 package/role | Reliability | Reliability port/service 또는 승인된 shared module | deterministic assignment equivalence, DB role/grant, full regression |
| experiment exposure | recommendation P2 package/role | Reliability | Reliability-owned exposure port 가능 | binding trigger 동등성, source authority 보존 |
| dataset/evaluation | recommendation P2 package/role | Reliability | Reliability | canonical bytes/hash, metric semantics, Gate A-E exact preservation |
| release decision | recommendation P2 package/role | Reliability + Operations approval | Reliability decision + Operations approval port | audit/authorization/state machine migration |

현재 위치가 governance 위반이라고 단정하지 않는다. P2 CLOSED 기준선의 보호된 physical arrangement이며, 이전은 별도 High-risk 작업이다.

## 10. Migration 금지 조건

다음 중 하나라도 존재하면 consumer/source/owner 전환을 금지한다.

- source count 또는 identity reconciliation mismatch
- canonical bytes/hash mismatch
- P1/P2 core/backend/SQL regression
- metric numerator/denominator 의미 불명확
- exposure source 중복 집계 가능성
- unknown identity mapping behavior
- rollback/forward-fix 부재
- DB role/grant 검증 부재
- Operations approval path 미확정
- 기존 release evidence 재작성 필요

## 11. 총괄방 승인 필요 항목

1. System Contract V1의 기준 추천/DB 표기를 P2/v2.7로 정합화
2. P2 physical owner와 Reliability semantic owner의 장기 배치
3. restricted identity mapping owner와 privacy deletion 정책
4. Data P1/P2 shadow bridge read 권한과 승인 절차
5. future search exposure contract의 registry ID와 metric authority
6. IP-1에서 공통 contract type을 둘 모듈/package 위치

## 12. 보호 판정

- 기존 P1/P2 production source 변경: 없음
- 기존 SQL `01..26` 변경: 없음
- 기존 P2 hash material 변경: 없음
- source authority 재정의: 없음
- metric 의미 재정의: 없음
- runtime consumer cutover: 없음
