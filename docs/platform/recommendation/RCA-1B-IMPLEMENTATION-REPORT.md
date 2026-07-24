# RCA-1B Implementation Report

## Scope
Implements non-production read-only reconciliation only. No production DB, runtime dark read, source replacement, traffic, cutover, or authority transfer.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`. SC-4 exact head: `b345a47c68c0e89db325183dbab6113a6291f24e`. RCA-1 exact head: `38896b2a37180633870282e9d9e305d9c9fbbf8a`.

## Implementation
Test-only Java/Testcontainers database adapter, static query registry, deterministic seed, redacted evidence writer, independent verifier, PostgreSQL 15/18 matrix, and protected regressions.

## Authority
P1 remains owned by Intelligence; P2 exposure/outcome semantics remain owned by Reliability. Data candidates remain non-authoritative. System Coordination controls exit and transfer.

## Dependencies
Java 21, existing Testcontainers dependencies, PostgreSQL JDBC, canonical SQL `01..52`, RCA-1 taxonomy and fixtures.

## Execution Environment
`CI_EPHEMERAL_POSTGRESQL`; PostgreSQL `15,18`; UTC; `C` locale; tmpfs; one isolated container per matrix job.

## DB Access Boundary
Owner bootstrap applies schema/seed only. Reconciliation reconnects as `rca1b_readonly` with server-visible read-only repeatable-read transaction and finite limits.

## Query Boundary
Exactly seven static prepared, parameterized, bounded, ordered, fingerprinted queries. Unknown ID or fingerprint fails before execution.

## Dataset
`DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE`; version-controlled, idempotent, duplicate-free baseline plus expected-negative inventory; destroyed with container.

## Identity/Privacy
`SYNTHETIC_ONLY`; all negative identity states fail closed. No raw identity, mapping pair, query, row, endpoint, or credential in evidence.

## P1 Result
Target result: `RECONCILED_WITH_EXPECTED_GAPS`; DB-comparable fields use zero tolerance. Existing semantic gaps remain explicit.

## P2 Result
Target result: `RECONCILED_WITH_MIGRATION_GAPS`; exact P2 exposure/window/events/fallback preserved. Stale assignment and persisted dedupe remain migration-required.

## Checkpoint/Lineage
Exact parity requires equal zero-lag checkpoint, equal explicit snapshot time, and equal lineage fingerprint.

## Evidence
Fixed-order JSON/TSV, counters, role/server state, negative-test inventory, query inventory, canonical cross-version digest, and five review packages.

## Verification
Executed only by exact-head CI. Unexecuted production/runtime checks are never recorded as PASS.

## Compatibility
Proves only deterministic non-production DB reconciliation on PostgreSQL 15/18.

## Risks
Synthetic distribution and isolated query plans do not represent production traffic, identity, credentials, scale, or latency.

## Exit Criteria
Both matrix jobs, cross-version equivalence, permission boundaries, P1/P2 classification, checkpoint/lineage, redaction, and protected regressions must pass.

## Handoff
After explicit user review and merge, RCA-2 still requires separate SC authorization.
