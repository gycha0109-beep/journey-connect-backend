# RCA-1B Deterministic Seed Report

## Scope
Defines the version-controlled synthetic DB dataset; it is not a migration or production snapshot.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; canonical SQL `01..52` remains unchanged.

## Implementation
`seed.sql` creates canonical P1/P2/Data rows, 66 registered scenarios, 1001 bounded-row probes and three duplicate-constraint assertions. It is applied twice and logical counts must remain unchanged.

## Authority
Data reviews candidate shape and lineage; Intelligence/Reliability review lane semantics; SC retains allocation authority.

## Dependencies
Canonical schema, synthetic account/region/post, recommendation run/exposure/behavior objects and Data projection objects.

## Execution Environment
Applied only by bootstrap owner inside disposable PostgreSQL 15/18 containers.

## DB Access Boundary
Seed execution ends before reconnecting as `rca1b_readonly`.

## Query Boundary
Seed rows are addressable only through registered synthetic case IDs and allowlisted objects.

## Dataset
`SEED_MODE=VERSION_CONTROLLED_TEST_ONLY`, idempotent, duplicate-free baseline, deterministic digest, no raw snapshot retention, teardown by container destruction.

## Identity/Privacy
Only synthetic references are inserted. No production-derived or persistent mapping is present.

## P1 Result
Includes exact/derived/window/null/empty/numeric/timezone/order/duplicate/count/checkpoint/lineage/snapshot and identity cases.

## P2 Result
Includes exposure/assignment/binding/boundaries/events/fallback/contamination/duplicates/mismatches/checkpoint/lineage/migration and identity cases.

## Checkpoint/Lineage
Uses one explicit zero-lag checkpoint and explicit snapshot timestamp; negative cases are scenario inventory, not baseline defects.

## Evidence
Seed ID/version/digest, case count, logical row count and database version are recorded; SQL and inserted rows are not copied to artifacts.

## Verification
Second application must not change logical row count; duplicate assertions must be blocked with integrity SQLSTATE.

## Compatibility
Seed digest and logical case count must match PostgreSQL 15 and 18.

## Risks
Synthetic distribution does not model production cardinality or skew.

## Exit Criteria
Digest, idempotency, duplicate assertions and version equivalence pass.

## Handoff
Any production-derived dataset requires a new privacy/SC decision.
