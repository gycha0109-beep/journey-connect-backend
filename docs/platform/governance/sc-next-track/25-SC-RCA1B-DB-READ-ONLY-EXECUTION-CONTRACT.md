# SC RCA-1B DB Read-only Execution Contract

## Scope

Define enforceable connection, transaction and resource boundaries. This document creates no role, grant, SQL or query implementation.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; current P1/P2 sources remain authoritative.

## Decision

```text
TRANSACTION_READ_ONLY=REQUIRED
TRANSACTION_ISOLATION=REPEATABLE_READ
AUTOCOMMIT=DISABLED
STATEMENT_TIMEOUT_MS=5000
LOCK_TIMEOUT_MS=1000
IDLE_IN_TRANSACTION_TIMEOUT_MS=5000
MAX_RESULT_ROWS_PER_QUERY=1000
MAX_RECONCILIATION_CASES=10000
MAX_EXECUTION_DURATION_SECONDS=900
CONNECTION_POOL=NOT_REQUIRED
MAX_RECONCILIATION_CONNECTIONS=2
PARALLEL_QUERY=DISABLED
CURSOR_FETCH_SIZE=100
RETRY_POLICY=NONE
```

The implementation must assert `transaction_read_only=on` before every query family. DDL, DML, `MERGE`, temporary objects, server-file `COPY`, function/trigger creation, schema changes, migrations, `VACUUM`, `ANALYZE`, materialized-view refresh and lock escalation are forbidden.

## Rationale

A declarative read-only flag is insufficient without server-visible assertions, least privilege and bounded execution.

## Authority

Operations owns connection/resource controls; SC owns the contract; lane owners approve semantic queries.

## Dependencies

Ephemeral read-only role, explicit transactions, bounded prepared statements and abort handler.

## Execution Environment

The contract applies independently to PostgreSQL 15 and 18 CI jobs.

## DB Access Boundary

Owner/superuser, `BYPASSRLS`, write privilege and production endpoints abort execution. Transaction failure causes rollback and connection close.

## Query Boundary

Every query includes deterministic ordering and explicit limit or a bounded single-row aggregate.

## Identity/Privacy

Actual identity tables and mapping ports are outside the allowlist.

## Evidence

Record asserted isolation, read-only status and configured limits, not credentials or connection metadata.

## DB/SQL Impact

No canonical DB change. Runtime `SET LOCAL` operations belong only to the future test transaction and are not migrations.

## Production Impact

None.

## Verification

SC-4 checks that all limits are finite and the contract is documented. Permission and write-attempt tests are `NOT_EXECUTED`.

## Risks

A bootstrap owner may still write during seed setup; reconciliation must use a separate login and begin only after ownership setup ends.

## Exit Criteria

Read-only assertions, blocked write attempts, timeout behavior, row limits and rollback/close behavior pass in both version jobs.

## Handoff

The implementation PR must include explicit negative tests for DDL/DML and read-only violations.