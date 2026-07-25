# SC RCA-2 Exit Criteria and Authority Transfer Boundary

## Scope
Define future RCA-2 completion and the hard gate before any transfer review.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; current P1/P2 authority unchanged.

## Decision
Future exit candidates:
```text
RUNTIME_DARK_READ_EXECUTED
PRIMARY_RESULT_AUTHORITY_PRESERVED
SHADOW_RESULT_NOT_SERVED
FEATURE_FLAG_DEFAULT_OFF
TRAFFIC_BOUNDARY_ENFORCED
TIMEOUT_BOUNDARY_ENFORCED
FALLBACK_BOUNDARY_ENFORCED
CIRCUIT_BREAKER_ENFORCED
KILL_SWITCH_VERIFIED
P1_RUNTIME_RESULTS_CLASSIFIED
P2_RUNTIME_RESULTS_CLASSIFIED
CHECKPOINT_BOUNDARY_ENFORCED
LINEAGE_BOUNDARY_ENFORCED
IDENTITY_BOUNDARY_ENFORCED
OBSERVABILITY_ACTIVE
ROLLBACK_VERIFIED
NO_PRODUCTION_RESPONSE_MUTATION
NO_DATABASE_WRITE
NO_AUTHORITY_TRANSFER
```

## Rationale
Dark-read completion proves observation safety only, not adoption.

## Authority
SC decides exit and whether a later transfer review may begin; Intelligence/Reliability retain current lane authority.

## Dependencies
Exact-head runtime evidence and all blocking approvals.

## Runtime Environment
Exit applies to isolated non-production only unless a separate production entry exists.

## Runtime Model
Bounded post-response model must be executed and verified.

## Feature Flag
Default OFF and fail-closed behavior must remain after exit.

## Traffic Boundary
Only approved non-production stages; production remains 0.

## Primary/Shadow Authority
Primary preserved; shadow never served.

## Timeout/Fallback
Finite limits and keep-primary behavior verified.

## Credential/Network
Non-production access, revocation and no production route verified.

## Identity/Privacy
Synthetic/test-account boundary and redaction verified; actual identity not approved.

## P1 Result Boundary
P1 runtime results independently classified; expected gaps remain.

## P2 Result Boundary
P2 runtime results independently classified; migration/protected gaps remain.

## Checkpoint/Lineage
Measurement and explicit freshness decision required for exit; no invented threshold.

## Observability
Metrics, alerts, cardinality, retention and redaction active.

## Rollback
All seven levels verified.

## DB/SQL Impact
No database write or unapproved SQL.

## Production Impact
RCA-2 completion does not mean production activation, full rollout, candidate serving, cutover, source deprecation, authority transfer, actual identity approval, load completion or gap resolution.

## Verification
Every exit marker requires actual implementation evidence; SC-5 records all runtime markers `NOT_EXECUTED`.

## Risks
Authority-transfer language before a new SC review invalidates the boundary.

## Exit Criteria
All listed markers PASS on one exact head, with no critical incidents or unresolved blocking review.

## Handoff
A later authority-transfer assessment is a new phase with separate source, serving, fallback, migration and production contracts.