# RCA-1B P2 Mismatch Inventory

## Scope
Separates exact P2 blockers, expected-negative fixtures and migration-required gaps.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; RCA-1 P2 result remains `RECONCILED_WITH_MIGRATION_GAPS`.

## Implementation
Scenario registry and evidence classify each P2 DB dimension independently.

## Authority
Reliability owns acceptance; SC forbids authority transfer inferred from reconciliation.

## Dependencies
P2 query pair, exact exposure authority, event set, window and fallback binding.

## Execution Environment
Synthetic ephemeral PostgreSQL only.

## DB Access Boundary
No canonical dataset row or release evidence access.

## Query Boundary
Only registered P2/checkpoint/lineage queries can produce mismatch evidence.

## Dataset
Negative inventory covers exposure contamination, impression/view/hide/report, unbound fallback, duplicate exposure/outcome/observation key, assignment/version/subject/session/run/exposure mismatch and identity failures.

## Identity/Privacy
Identity mismatch and missing states fail closed.

## P1 Result
Not combined with P2.

## P2 Result
Exact blockers use existing RCA-1 classifications. Stale-unexposed assignment and persisted dedupe are `MIGRATION_REQUIRED`; hash/release remain protected.

## Checkpoint/Lineage
Checkpoint mismatch/staleness and lineage mismatch block exact parity.

## Evidence
Authority mismatch, migration gap and baseline mismatch counts are separate.

## Verification
Expected-negative outcomes must be reproduced without altering baseline lane result.

## Compatibility
No new comparison classification is introduced.

## Risks
Persisted dedupe equivalence remains unresolved without a separately allocated migration.

## Exit Criteria
Zero unexpected baseline authority mismatch; all migration gaps remain explicit.

## Handoff
P2 exit recommendation remains pending Reliability/user review.
