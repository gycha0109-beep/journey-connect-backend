# SC RCA-2 Runtime Query Boundary

## Scope
Separate RCA-1B test queries from future runtime contracts.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical.

## Decision
The seven RCA-1B queries remain `TEST_ONLY`. Allocate application contract `recommendation-runtime-dark-read-query-registry-v1`; it initially contains no production query and may contain only versioned, prepared, bounded, fingerprinted non-production query/adapter definitions approved per lane.

## Rationale
Successful ephemeral PostgreSQL reconciliation does not establish runtime plan, index, identity or privacy safety.

## Authority
Data owns candidate dependencies; Intelligence/Reliability approve lane semantics; Operations approves plans/resources; SC approves registry versions.

## Dependencies
Per-query object inventory, index/plan evidence, finite timeout/rows, parameter provenance, identity/checkpoint/lineage and exposure-risk review.

## Runtime Environment
Isolated non-production only.

## Runtime Model
Queries run inside bounded post-response tasks only.

## Feature Flag
OFF blocks registry invocation.

## Traffic Boundary
Initial 0%; no production execution.

## Primary/Shadow Authority
Query output is comparison evidence only.

## Timeout/Fallback
Every query must fit the 500ms total task budget; no retry.

## Credential/Network
Explicit endpoint/object allowlist; no owner/write/raw identity access.

## Identity/Privacy
Parameters may use synthetic/test-account references only and are never logged.

## P1 Result Boundary
P1 runtime definitions are independently versioned and cannot fabricate events from aggregates.

## P2 Result Boundary
P2 definitions preserve authoritative exposure/window/event/fallback and cannot access canonical dataset/hash/release evidence.

## Checkpoint/Lineage
Checkpoint and lineage fields are mandatory query outputs or adapter metadata.

## Observability
Emit query ID/version/fingerprint and bounded classification, not SQL text or parameters.

## Rollback
Disable registry version through flag/config and pin prior artifact.

## DB/SQL Impact
No migration, table, view, role or grant. `RUNTIME_QUERY_REGISTRY_REQUIRED=YES_APPLICATION_CONTRACT_ONLY`.

## Production Impact
None.

## Verification
Dynamic SQL, unbounded rows, unknown fingerprints and protected object access must fail before execution; SC-5 does not execute runtime queries.

## Risks
Approved runtime query inventory is intentionally empty until implementation review.

## Exit Criteria
Every used definition has exact version, fingerprint, plan/resource and lane approvals.

## Handoff
Create the application registry in a separate PR without reclassifying RCA-1B assets as production-ready.