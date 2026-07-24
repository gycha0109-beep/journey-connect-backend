# RCA-1B Protected Authority Report

## Scope
Proves the implementation adds test-only DB evidence without changing source authority.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; P1/P2 sources, RCA-0/1, core, SQL and production configuration are protected.

## Implementation
Changes are limited to RCA-1B test Java/resources, verifier/evidence, documentation and dedicated CI.

## Authority
`RecommendationP1ProfileSource`, `recommendation_p1_profile_snapshot`, `RecommendationP2ObservationSource`, `recommendation_p2_experiment_exposure`, dataset and metrics remain authoritative as before.

## Dependencies
Git diff boundary, historical verifier assets and canonical SQL inventory.

## Execution Environment
Non-production ephemeral only.

## DB Access Boundary
No persistent table, view, role, grant or migration is introduced.

## Query Boundary
Data projections are candidate reads only; dataset/hash/release objects remain protected.

## Dataset
Synthetic fixture is test-only and cannot become an authority source.

## Identity/Privacy
No actual identity mapping implementation or owner is introduced.

## P1 Result
P1 reconciliation does not replace or deprecate the current source.

## P2 Result
P2 markers explicitly retain current authority and no transfer.

## Checkpoint/Lineage
Comparison metadata does not confer authority.

## Evidence
Exact-head verifier records protected diff and SQL inventory.

## Verification
RCA-0/RCA-1 runners, Recommendation core, backend and IP-12.5 gates execute after DB equivalence.

## Compatibility
Completion is not production, runtime, cutover or source-replacement readiness.

## Risks
Future implementation may incorrectly infer transfer from match evidence; markers must remain mandatory.

## Exit Criteria
No protected path change and all protected regressions pass.

## Handoff
RCA-2 requires separate SC approval; authority transfer remains forbidden.
