# SC Existing P1/P2 Authority Protection Decision

## Scope

Freeze current P1/P2 source, evidence and metric authority throughout RCA-0.

## Current Baseline

### P1

`recommendation_behavior_event` plus content facts and explicit preferences are read by `RecommendationP1ProfileSource`, then transformed into `recommendation_p1_profile_snapshot` and P1 policy selection.

### P2

`recommendation_p2_experiment_assignment` and `recommendation_p2_experiment_exposure` are bound to recommendation runs, behavior outcomes and the latest eligible P1 snapshot by `RecommendationP2ObservationSource`, producing `recommendation-evaluation-dataset-v1` and append-only evaluation/release evidence.

## Contract Impact

No current authority changes. Data profile and outcome projections remain non-authoritative candidate inputs.

## Authority

| Meaning | Authority |
|---|---|
| P1 source | current `RecommendationP1ProfileSource` path |
| P1 result | `recommendation_p1_profile_snapshot` |
| P2 assignment | `recommendation_p2_experiment_assignment` |
| P2 exposure | `recommendation_p2_experiment_exposure` |
| P2 dataset | `recommendation-evaluation-dataset-v1` |
| engagement | exposed eligible subject with click/like/save/share within seven days |
| fallback | bound exposed run with `run_status=fallback` |
| release evidence | existing append-only P2 evidence |

## Dependencies

Any future replacement requires a new source/dataset version, reconciliation, replay plan, full Recommendation regression, Reliability approval and SC approval.

## Allowed Changes

Read-only fixture extraction and semantic comparison.

## Forbidden Changes

- source class replacement;
- P1 snapshot rewrite;
- P2 assignment/exposure/dataset/evaluation/gate/release write change;
- metric, denominator, attribution, engagement event or fallback change;
- canonical bytes/hash or row identity reinterpretation;
- exposure-source aggregation.

## Verification

Protected source files, SQL objects, dataset versions, metric constants and release evidence paths must be unchanged by RCA-0.

## Compatibility

Data projections may be classified as compatible, conditionally compatible, incompatible or migration-required. No classification changes authority.

## Risks

A consumer adapter can accidentally become an alternate source if registered as a Spring bean or repository. RCA-0 must not register runtime components.

## Handoff

Every RCA-0 test report must state `CURRENT_P1_P2_AUTHORITY_UNCHANGED`.
