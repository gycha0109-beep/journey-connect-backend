# SC RCA-1B Role, Grant and SQL Allocation Decision

## Scope

Decide whether RCA-1B needs a database role, grants or canonical SQL allocation. No SQL is authored here.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; SQL `01..52` is immutable; SQL `53+` is absent and unallocated.

## Decision

```text
RCA1B_ROLE_REQUIRED=YES_EPHEMERAL_TEST_ONLY
RCA1B_ROLE_NAME=rca1b_readonly
LOGIN_ALLOWED=YES_EPHEMERAL_ONLY
INHERIT_ALLOWED=NO
BYPASSRLS_ALLOWED=NO
CREATEDB_ALLOWED=NO
CREATEROLE_ALLOWED=NO
REPLICATION_ALLOWED=NO
SCHEMA_USAGE=EXPLICIT_ALLOWLIST_ONLY
TABLE_SELECT=EXPLICIT_ALLOWLIST_ONLY
SEQUENCE_SELECT=NO
FUNCTION_EXECUTE=NO_PRIVILEGED_FUNCTIONS
DEFAULT_PRIVILEGES=NONE
WRITE_GRANT=FORBIDDEN
OWNER_ROLE_USE=FORBIDDEN
SQL_ALLOCATION=NOT_REQUIRED
TEST_FIXTURE_SQL_REQUIRED=YES_NONCANONICAL_TEST_ONLY
```

The bootstrap owner may create the ephemeral login and explicit grants inside the disposable container. Those statements are test setup, not canonical migrations, and are destroyed with the job.

## Rationale

A separate login proves least privilege more strongly than relying only on transaction flags. Because the role exists only in a disposable test database, no persistent schema or SQL sequence allocation is required.

## Authority

Operations approves credentials and role properties; Data identifies candidate objects; Intelligence/Reliability approve lane object access; SC controls SQL allocation.

## Dependencies

Frozen object inventory, bootstrap/reconciliation credential separation and permission-negative tests.

## Execution Environment

Role creation is permitted only during CI ephemeral bootstrap before reconciliation begins.

## DB Access Boundary

No blanket schema/table grants, no ownership, no RLS bypass, no default privilege expansion and no access to system credential catalogs.

## Query Boundary

Select privileges are limited to objects used by registered query IDs. Functions must use built-ins only and no privileged user-defined function.

## Identity/Privacy

No grant to an actual identity mapping object is allowed.

## Evidence

Record role-policy booleans and permission-test classifications only; never record password or connection string.

## DB/SQL Impact

```text
DB_CHANGE_REQUIRED=NO
SQL_ALLOCATION_REQUIRED=NO
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=YES_EPHEMERAL_TEST_ONLY
NEW_GRANT_REQUIRED=YES_EPHEMERAL_TEST_ONLY
SQL_53_PLUS=UNALLOCATED
```

## Production Impact

None.

## Verification

SC-4 validates the decision. Actual role creation and permission tests are `NOT_EXECUTED`.

## Risks

Object-grant drift can silently expand read scope. The implementation must compare the actual grants with the committed allowlist and fail closed.

## Exit Criteria

The ephemeral login cannot write, cannot bypass RLS, cannot access non-allowlisted objects and disappears at teardown.

## Handoff

If persistent/shared non-production roles become necessary, stop and request a new SC/Operations/Privacy allocation; do not use SQL `53+` automatically.