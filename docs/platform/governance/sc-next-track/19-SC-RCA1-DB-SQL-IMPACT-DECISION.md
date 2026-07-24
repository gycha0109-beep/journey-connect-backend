# SC RCA-1 DB and SQL Impact Decision

## Scope

Determine whether RCA-1 needs database allocation from authoritative main `f802a105e46a62718616acaa7a3db6c172e7ed10`.

## Current Baseline

Canonical SQL `01..52` is protected. SQL `53+` is absent and unallocated.

## Decision

```text
DB_CHANGE_REQUIRED=NO
SQL_ALLOCATION_REQUIRED=NO
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=NO
NEW_GRANT_REQUIRED=NO
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

## Rationale

Model A uses recorded snapshots and fixtures. Persistence and query behavior are not in scope.

## Authority

SC owns DB sequence. No track receives new DB authority.

## Dependencies

Existing fixture and contract files only.

## Allowed Changes

None to DB/SQL.

## Forbidden Changes

Migration, table, view, function, role, grant, Flyway activation or repository query.

## Identity/Privacy

No identity store.

## DB/SQL Impact

None. SQL `53+` remains unallocated.

If implementation discovers an unavoidable DB object, stop with:

```text
RCA1_ENTRY_BLOCKED_BY_SQL_ALLOCATION
```

and submit a separate allocation proposal without writing SQL.

## Production Impact

None.

## Verification

Require exactly one SQL file for each `01..52`, no `53+`, and zero SQL diff.

## Risks

A developer may introduce a convenience persistence table. The protected-diff gate must reject it.

## Exit Criteria

No DB/SQL diff and no database dependency in implementation scope.

## Handoff

Model B requires a separate proposal defining read-only role, transaction read-only, statement timeout, row limit, no lock escalation, no write privilege, no migration, no production DB and reproducible test data.
