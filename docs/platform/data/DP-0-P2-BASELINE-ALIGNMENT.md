# DP-0 P2 Baseline Alignment

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `dp-0-p2-baseline-alignment-v1` |
| 상태 | `ACTIVE AMENDMENT` |
| 소유 트랙 | Data Platform |
| 원 계약 | `jc-data-platform-contract-foundation-v1` |
| 기준 추천 | `P1 CLOSED / P2 TECHNICAL CLOSED / PRODUCTION HOLD` |
| 기준 DB | `journey-connect-db-v2.7/01..28` |

## 2. 목적

현재 P1/P2 runtime, source authority, identity, metric, canonical evidence를 Data Platform이 대체하거나 재해석하지 않도록 DP-0을 보정한다.

## 3. P1 current path

```text
recommendation_behavior_event
+ posts / regions / post_tags / tags
+ recommendation_user_preference
→ RecommendationP1ProfileSource
→ deterministic profile builder
→ recommendation_p1_profile_snapshot
→ P1 policy selection
```

`recommendation-profile-input-v1`은 future shadow bridge이며 current P1 authoritative source가 아니다.

## 4. P2 current path

```text
recommendation_p2_experiment_assignment
→ recommendation_p2_experiment_exposure
+ recommendation_run
+ recommendation_behavior_event
+ latest eligible recommendation_p1_profile_snapshot
→ recommendation-evaluation-dataset-v1
→ evaluation / Gate A..E / release evidence
```

보호 계약:

- assignment subject: `user:<numeric-id>`
- experiment exposure authority: `recommendation_p2_experiment_exposure`
- dataset: `recommendation-evaluation-dataset-v1`
- metric: `recommendation-metrics-v1`
- evaluation: `recommendation-evaluation-policy-v1`
- `engagement_rate`: valid bound exposure 이후 7일 내 click/like/save/share가 있는 exposed eligible subject 비율
- `fallback_rate`: bound exposed distinct run 중 `run_status=fallback` 비율
- evidence는 append-only

## 5. Data non-intervention

Data는 다음을 하지 않는다.

- P1 source/profile snapshot rewrite 또는 immediate replacement
- P2 assignment/exposure/dataset/evaluation/gate/release table write
- metric numerator/denominator/window/state 변경
- 기존 P2 canonical bytes/hash/evidence rewrite
- runtime에 Data dependency/cutover 추가

## 6. Shadow bridges

### P1

```text
validated behavior stream
→ user behavior aggregate
→ recommendation-profile-input-v1 [shadow]
```

Cutover requires exact coverage/order/dedupe reconciliation, region/tag facts contract, P1/P0 regression, replay/fingerprint preservation, new source/schema/consumer version, Intelligence+SC approval.

### P2

```text
assignment read
+ authoritative P2 exposure
+ canonical outcomes
+ run/profile refs
→ experiment-outcome-input-v1 [shadow]
```

Cutover requires exact binding/window/stale-assignment exclusion/engagement/fallback/dedupe, canonical hash reproducibility, P2 full regression, release evidence preservation, Reliability+SC approval.

## 7. Authority matrix

| 의미 | authoritative source |
|---|---|
| P0/P1 behavior | `recommendation_behavior_event` |
| general recommendation exposure | `recommendation_exposure_event` + candidate rows |
| behavior impression | behavior fact only; not automatic denominator |
| P2 experiment exposure | `recommendation_p2_experiment_exposure` |
| P2 assignment | `recommendation_p2_experiment_assignment` |
| P1 profile | `recommendation_p1_profile_snapshot` |
| P2 fallback | bound `recommendation_run.run_status` |
| P2 evaluation/release | protected P2 append-only tables |
| Data P1/P2 inputs | shadow-only until approval |

## 8. Identity

```text
subject:<opaque-id> != user:<numeric-id>
```

기존 P2 row를 rewrite하거나 opaque subject로 재해석하지 않는다. 실제 연결은 approved restricted mapping port가 필요하며 DP-1 범위가 아니다.

## 9. SQL 27/28

- SQL 27: Search-owned rebuildable derived projection + Operations-owned fail-closed eligibility
- SQL 28: smoke test
- 둘은 Data migration/Data authority가 아니다.
- DP-1은 SQL을 변경·추가하지 않는다.
- DP-2 이후 SQL은 SC가 28 이후 배정한다.

## 10. DP-1 boundary

DP-1은 P1/P2 identity/version/source-authority fixture를 검증하되 runtime code, source, consumer, SQL을 변경하지 않는다. `jc-data-contracts` / `com.jc.data.contract`는 예약됐지만 이번 reconciliation에서 구현하지 않는다.
