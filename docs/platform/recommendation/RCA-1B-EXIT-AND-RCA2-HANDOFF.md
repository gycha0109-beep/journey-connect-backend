# RCA-1B Exit and RCA-2 Handoff

## Scope
Defines RCA-1B completion and the hard boundary before RCA-2.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; production activation and runtime wiring remain unauthorized.

## Implementation
RCA-1B supplies non-production DB evidence only.

## Authority
SC controls exit and RCA-2 entry; lane owners review P1/P2; Operations and Privacy/Security review environment/evidence.

## Dependencies
All exact-head RCA-1B artifacts and review packages.

## Execution Environment
CI ephemeral PostgreSQL only.

## DB Access Boundary
No persistent DB object, role, grant, credential or route survives completion.

## Query Boundary
Seven RCA-1B queries cannot be treated as production runtime queries.

## Dataset
Synthetic deterministic fixture only.

## Identity/Privacy
Actual identity mapping remains unimplemented and unauthorized.

## P1 Result
Required: database results classified independently, current P1 authority unchanged.

## P2 Result
Required: database results classified independently with `P2_NON_PRODUCTION_RECONCILIATION_ONLY`, `CURRENT_P2_AUTHORITY_UNCHANGED`, `NO_AUTHORITY_TRANSFER`.

## Checkpoint/Lineage
Boundaries must be enforced on both versions.

## Evidence
Read-only, query, seed, lane, cross-version, redaction and regression evidence must be exact-head bound.

## Verification
Completion markers: `NON_PRODUCTION_DB_RECONCILIATION_EXECUTED`, `P1_DATABASE_RESULTS_CLASSIFIED`, `P2_DATABASE_RESULTS_CLASSIFIED`, `CHECKPOINT_BOUNDARY_ENFORCED`, `LINEAGE_BOUNDARY_ENFORCED`, `READ_ONLY_BOUNDARY_ENFORCED`, `IDENTITY_BOUNDARY_ENFORCED`, `MODEL_A_AND_MODEL_B_TAXONOMY_ALIGNED`, `POSTGRESQL_15_18_RESULTS_EQUIVALENT`, `PROTECTED_AUTHORITY_UNCHANGED`, `NO_PRODUCTION_DATABASE`, `NO_PRODUCTION_TRAFFIC`, `NO_AUTHORITY_TRANSFER`.

## Compatibility
Completion does not imply runtime, production, cutover, credential, identity, scale or latency readiness.

## Risks
Using RCA-1B evidence to authorize runtime behavior would violate the phase boundary.

## Exit Criteria
All technical checks pass and Draft PR remains pending explicit user review/merge.

## Handoff
RCA-2 requires a separate SC authorization covering runtime dark read, credentials, traffic, timeout/fallback, observability, rollback and actual identity governance.
