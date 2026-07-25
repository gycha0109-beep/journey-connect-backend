# SC RCA-2 DB and SQL Impact Decision

## Scope
Decide whether RCA-2 entry allocates persistent database assets.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; canonical SQL `01..52` protected and SQL `53+` unallocated.

## Decision
```text
DB_CHANGE_REQUIRED=NO
SQL_ALLOCATION_REQUIRED=NO
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=NO
NEW_GRANT_REQUIRED=NO
RUNTIME_QUERY_REGISTRY_REQUIRED=YES_APPLICATION_CONTRACT_ONLY
PERSISTED_EVIDENCE_REQUIRED=NO
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

## Rationale
RCA-2 can use isolated application orchestration and existing observability without persistent product-data evidence.

## Authority
SC controls SQL allocation; Operations and Data report implementation needs.

## Dependencies
No implementation may silently introduce persistent role/grant/object state.

## Runtime Environment
Isolated non-production runtime without production DB access.

## Runtime Model
Bounded application task only.

## Feature Flag
No DB-backed flag store is allocated by SC-5.

## Traffic Boundary
Initial 0%.

## Primary/Shadow Authority
No candidate or evidence writes.

## Timeout/Fallback
Dependency absence keeps primary.

## Credential/Network
No persistent DB credential or role.

## Identity/Privacy
No identity mapping table.

## P1 Result Boundary
No P1 authority object changes.

## P2 Result Boundary
No P2 dataset/hash/release access or changes.

## Checkpoint/Lineage
Metadata remains transient telemetry/evidence.

## Observability
Use existing infrastructure; no new evidence table.

## Rollback
Application/config rollback only; no migration rollback.

## DB/SQL Impact
If implementation discovers a table, view, persistent role, grant or migration requirement, stop with `RCA2_ENTRY_BLOCKED_BY_SQL_ALLOCATION`, list required objects and set `SQL_NOT_CREATED`.

## Production Impact
None.

## Verification
Verifier checks one SQL file for each `01..52`, no `53+`, and no database-path diff.

## Risks
A future implementation may reveal a legitimate persistent need; that requires a new SC allocation decision.

## Exit Criteria
No SQL/database/role/grant diff and no persistent evidence.

## Handoff
Do not write SQL or reuse the RCA-1B ephemeral role as a runtime role.