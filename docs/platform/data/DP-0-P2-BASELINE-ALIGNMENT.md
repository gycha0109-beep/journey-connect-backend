# DP-0 P2 Baseline Alignment

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `dp-0-p2-baseline-alignment-v1` |
| 상태 | `ACTIVE AMENDMENT` |
| 소유 트랙 | Data Platform |
| 원 계약 | `jc-data-platform-contract-foundation-v1` |
| 기준 프로젝트 | `Journey-Connect-P2-Final-Validation-Batch18` |
| 기준 추천 상태 | `P2 TECHNICAL CLOSED / PRODUCTION HOLD` |
| 현재 canonical DB | `journey-connect-db-v2.7/01..28` |
| 원 P2 검증 기준 | `journey-connect-db-v2.7/01..26` |
| 작성 기준일 | 2026-07-19 |
| SC 보정일 | 2026-07-21 |

## 2. 목적

DP-0은 추천 P0 종료 기준선에서 작성되었다. 이후 추천 트랙이 P1과 P2까지 구현되었으므로, 이미 존재하는 P1 프로필과 P2 실험·평가 경로를 Data Platform이 대체하거나 의미를 바꾸지 않도록 호환 기준선을 추가한다.

이 문서는 기존 `platform-event-v1`, P0 adapter, idempotency, retry, quarantine, replay 계약을 변경하지 않는다. P1/P2에 대한 향후형 가정을 현재 구현에 맞게 보정한다.

## 3. 실제 확인한 P2 기준선

원 alignment에서 다음을 확인했다.

- P1 기술 구현: `CLOSED`
- P2 기술 구현: `CLOSED`
- P2 운영 출시 상태: 실제 CANARY 표본과 운영 승인 부재로 `HOLD`
- Java Core: P1 17개, P2 23개 계약 시나리오 PASS
- Backend 전체: `83/83 PASS`
- P0/P1/P2 계약 게이트 PASS
- PostgreSQL canonical SQL `01..26` fresh database PASS
- P2 assignment/exposure/evaluation/release PostgreSQL integration PASS
- P2 assignment runtime 기본값: 비활성
- P0/P1 기준 파일과 SQL `01..24`: 비변경 검증

SC reconciliation은 저장소의 SQL 27/28을 current canonical sequence로 추가 인식한다. 이 보정은 원 `01..26` 검증을 소급 확장해 PASS로 선언하지 않으며 SQL 27/28의 Search/Operations ownership을 유지한다.

## 4. 현재 P1 실행 경로

현재 P1은 Data Platform projection을 소비하지 않는다.

```text
recommendation_behavior_event
+ posts / regions / post_tags / tags
+ recommendation_user_preference
        ↓
RecommendationP1ProfileSource
        ↓
P1 deterministic profile builder
        ↓
recommendation_p1_profile_snapshot
        ↓
P1 policy selection / SHADOW / CANARY
```

현재 고정된 핵심 계약:

- profile table: `recommendation_p1_profile_snapshot`
- segment: `empty`, `explicit_only`, `emerging`, `established`
- profile policy version과 feature vocabulary version 결속
- reference time, event partition count, signal list, SHA-256 fingerprint
- behavior source는 기존 `recommendation_behavior_event`
- profile transform, decay, saturation, feature vocabulary는 Intelligence 소유

따라서 `recommendation-profile-input-v1`은 현재 P1 런타임의 필수 선행조건이 아니다. Data Platform이 이후 생성할 수 있는 병렬 입력 facts snapshot 또는 차기 profile source 후보다.

## 5. 현재 P2 실행 경로

```text
recommendation_p2_experiment_assignment
        ↓
recommendation_p2_experiment_exposure
        ↓
recommendation_run
+ recommendation_behavior_event
+ recommendation_p1_profile_snapshot
        ↓
RecommendationP2ObservationSource
        ↓
recommendation-evaluation-dataset-v1
        ↓
recommendation_p2_dataset_snapshot
        ↓
evaluation run / metric result / Gate A~E
        ↓
optional release transition decision
```

현재 고정된 계약:

| 영역 | 현재 계약 |
|---|---|
| assignment identity | `(experiment_id, experiment_version, subject_ref)` unique |
| assignment subject | `subject_ref = user:<numeric-user-id>` |
| variant | `baseline`, `treatment` |
| exposure authority | `recommendation_p2_experiment_exposure` |
| exposure binding | assignment, run, user, session, variant 검증 |
| dataset schema | `recommendation-evaluation-dataset-v1` |
| metric definition | `recommendation-metrics-v1` |
| evaluation policy | `recommendation-evaluation-policy-v1` |
| primary metric | `engagement_rate` |
| guardrail metric | `fallback_rate` |
| release states | `draft`, `shadow`, `canary`, `live`, `hold`, `rolled_back` |
| final decision | `canary`, `live`, `hold`, `rollback` |
| persistence | dataset/evaluation/result/gate/decision append-only |

`engagement_rate`는 실제 결속된 exposure 이후 7일 이내 `click`, `like`, `save`, `share` 중 하나가 존재하는 exposed eligible subject 비율이다. `view`, `hide`, `report`를 추가하지 않는다.

`fallback_rate`는 결속된 exposed run 중 `recommendation_run.run_status = fallback`인 run 비율이다.

## 6. Data Platform 적용 결정

### 6.1 기존 P1/P2 runtime 비개입

Data Platform은 다음을 하지 않는다.

- `RecommendationP1ProfileSource` 즉시 교체
- `recommendation_p1_profile_snapshot` 재작성
- P2 assignment/exposure/dataset/evaluation/gate/release table write
- P2 metric/window/threshold/state 변경
- 기존 P2 dataset canonical bytes/hash 재계산 또는 강제 이관
- P1/P2 runtime에 Data dependency 추가

### 6.2 P1 bridge

DP-5에서 shadow-only로 생성 가능:

```text
validated behavior stream
→ user behavior aggregate
→ recommendation-profile-input-v1
```

전환 조건:

1. current P1 source와 동일 관측 범위/order/dedupe 정의
2. region/tag facts approved projection
3. comparison fixtures
4. P1 core/P0-P1 full regression
5. existing profile replay/fingerprint preservation
6. new source/schema/consumer version
7. Intelligence+SC approval

승인 전 authoritative source가 아니다.

### 6.3 P2 bridge

DP-5에서 shadow-only로 생성 가능:

```text
assignment read contract
+ authoritative P2 exposure
+ canonical outcome facts
+ run/profile references
→ experiment-outcome-input-v1
```

기존 `recommendation-evaluation-dataset-v1`을 자동 대체하지 않는다.

전환 조건:

1. assignment/exposure/run/user/session/variant exact binding
2. exact observation window
3. stale unexposed exclusion
4. exact engagement semantics
5. exact fallback semantics
6. one observation per experiment/version/subject
7. metric/dataset/consumer version binding
8. canonical bytes/hash reproducibility
9. P2 core/backend full gate
10. release evidence/state non-regression
11. Reliability+SC approval

## 7. Source authority와 중복 방지

| 의미 | authoritative source |
|---|---|
| P0/P1 behavior fact | `recommendation_behavior_event` |
| 일반 추천 page exposure | 기존 `recommendation_exposure_event` 경로 |
| P2 실험 exposure | `recommendation_p2_experiment_exposure` |
| P2 assignment | `recommendation_p2_experiment_assignment` |
| P2 segment lookup | 관측 종료 전 latest eligible `recommendation_p1_profile_snapshot` |
| P2 fallback | `recommendation_run.run_status` |
| P2 release evidence | P2 append-only dataset/evaluation/metric/gate/release tables |

일반 exposure, behavior impression, P2 experiment exposure를 동시에 분모로 합산하지 않는다.

## 8. Identity 호환성

신규 Data actor는 `subject:<opaque-id>`, current P2 subject는 `user:<numeric-user-id>`다.

- 두 namespace는 동일하지 않다.
- existing P2 row/dataset/hash를 rewrite하지 않는다.
- automatic conversion/anonymous or other-subject fallback/actual join을 금지한다.
- 연결에는 restricted `IdentityMappingReadPort`, single write owner, purpose binding, access audit, version, invalidation/deletion policy가 필요하다.
- opaque identity 기반 전환은 new dataset/schema/consumer version과 migration/replay plan이 필요하다.

## 9. 소유권 배치

Track Governance상 assignment/evaluation/release semantic owner는 Reliability다. Current physical implementation은 `com.jc.backend.recommendation.p2`, `recommendation_p2_*`, recommendation role에 있다. 이는 protected compatibility arrangement다.

- Data가 해결하거나 이전하지 않는다.
- physical migration은 별도 High-risk proposal, role/grant, canonical hash/replay preservation, full regression이 필요하다.
- Data는 approved read contract 없이 접근하지 않고 절대 write하지 않는다.

## 10. DB 기준선과 SQL 27/28

- current canonical DB: `journey-connect-db-v2.7/01..28`
- SQL 25..26: protected P2 evaluation/release
- SQL 27: Search-owned rebuildable `search_document_projection_v1` + Operations semantic authority `search_document_operational_eligibility_v1`
- SQL 28: SQL 27 smoke test
- SQL 27/28은 Data migration/Data authority가 아니다.
- DP-1은 SQL 01..28을 수정하지 않고 신규 SQL을 추가하지 않는다.
- DP-2 이후 SQL은 SC가 28 이후로 별도 배정한다.

## 11. DP 단계 보정

| 단계 | P2 기준선 반영 |
|---|---|
| DP-1 | P1/P2 identity/version/source-authority fixture; runtime/SQL 비변경 |
| DP-2 | SC가 28 이후 DB sequence 배정 |
| DP-3 | replay가 existing P1/P2 evidence를 수정하지 않음 |
| DP-4 | P0 adapter + P1/P2 non-regression fixtures |
| DP-5 | P1/P2 bridge shadow-only |
| DP-6 | source-to-shadow reconciliation/lineage quality |
| DP-7 | cross-track contract test |

DP-1 namespace reservation:

```text
module: jc-data-contracts
package: com.jc.data.contract
status: RESERVED / NOT IMPLEMENTED
```

## 12. 검증 결과

| 검증 | 결과 |
|---|---|
| P2 reports/summary review | PASS |
| P1 direct source 확인 | PASS |
| P2 assignment/exposure/observation/evaluation path 확인 | PASS |
| dataset/metric/evaluation versions 확인 | PASS |
| P2 subjectRef 형식 확인 | PASS |
| existing DP-0/P0 adapter 영향 없음 | PASS |
| production code/SQL 변경 없음 | PASS |
| 원 P2 test rerun | NOT_EXECUTED — document stage |
| SC reconciliation protected diff/static | separate machine-readable evidence |

## 13. 잔여 승인

- P2 physical writer/Reliability semantic ownership long-term migration
- identity mapping owner/deletion policy
- P1/P2 shadow bridge read grants/projection contract
- DP-2 target DB/SQL sequence allocation after 28
- operational P2 assignment/admin approval path
- new Data fingerprint exact contract: `SC DECISION REQUIRED`
