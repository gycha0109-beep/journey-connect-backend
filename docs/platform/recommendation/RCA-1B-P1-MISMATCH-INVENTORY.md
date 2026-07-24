# RCA-1B P1 Mismatch Inventory

## Scope
Separates expected P1 gaps and negative fixtures from baseline defects.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; RCA-1 P1 result remains `RECONCILED_WITH_EXPECTED_GAPS`.

## Implementation
Scenario registry records role, dimension and expected classification independently from baseline query output.

## Authority
Intelligence decides whether expected semantic gaps remain acceptable. SC prevents those gaps being mislabeled as exact equivalence.

## Dependencies
P1 DB dimension inventory and RCA-1 classification taxonomy.

## Execution Environment
Synthetic ephemeral PostgreSQL only.

## DB Access Boundary
Inventory is read-only evidence; no source or candidate rows are modified.

## Query Boundary
Mismatches can originate only from registered P1 queries, checkpoint or lineage queries.

## Dataset
Negative cases include exact/derived mismatch, duplicate logical row, row-count mismatch, checkpoint mismatch/staleness, lineage mismatch, snapshot mismatch and identity failures.

## Identity/Privacy
Identity failures are fail-closed and never fall back to alternate users or subjects.

## P1 Result
Expected/protected gaps: ordering not comparable, event grain missing, explicit preference missing, transform policy missing and fingerprint semantics protected.

## P2 Result
Not combined with P1.

## Checkpoint/Lineage
Unequal or stale checkpoints and lineage mismatch block exact parity.

## Evidence
Baseline mismatch count and expected-negative counts are reported separately.

## Verification
No expected-negative case may change the baseline lane verdict; no baseline defect may be hidden as an expected gap.

## Compatibility
The inventory extends RCA-1 with DB dimensions without changing RCA-1 classifications.

## Risks
A future transform policy may reclassify currently protected dimensions and requires separate review.

## Exit Criteria
Baseline unexpected mismatch count is zero and every negative fixture is classified as designed.

## Handoff
P1 exit recommendation remains pending Intelligence/user review.
