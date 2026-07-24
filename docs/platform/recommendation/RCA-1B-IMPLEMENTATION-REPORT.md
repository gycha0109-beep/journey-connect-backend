# RCA-1B Implementation Report

## Scope
Implements non-production read-only reconciliation only. No production DB, runtime dark read, source replacement, traffic, cutover, or authority transfer.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`. SC-4 exact head: `b345a47c68c0e89db325183dbab6113a6291f24e`. RCA-1 exact head: `38896b2a37180633870282e9d9e305d9c9fbbf8a`.

## Implementation
Test-only Java/Testcontainers database adapter, static query registry, deterministic seed, redacted evidence writer, independent verifier, PostgreSQL 15/18 matrix, and protected regressions. Canonical SQL `01..52` is executed unchanged; rollback-only historical validation compatibility and SQL 51 include staging are isolated in the test harness.

## Authority
P1 remains owned by Intelligence; P2 exposure/outcome semantics remain owned by Reliability. Data candidates remain non-authoritative. System Coordination controls exit and transfer.

## Dependencies
Java 21, existing Testcontainers dependencies, PostgreSQL JDBC, canonical SQL `01..52`, RCA-1 taxonomy and fixtures.

## Execution Environment
`CI_EPHEMERAL_POSTGRESQL`; PostgreSQL `15,18`; UTC; `C` locale; tmpfs; one isolated container per matrix job. PostgreSQL 18 uses its version-aware `/var/lib/postgresql` data-layout boundary; PostgreSQL 15 uses `/var/lib/postgresql/data`.

## DB Access Boundary
Owner bootstrap applies schema/seed only. Reconciliation reconnects as `rca1b_readonly` with server-visible read-only repeatable-read transaction and finite limits.

## Query Boundary
Exactly seven static prepared, parameterized, bounded, ordered, fingerprinted queries. Unknown ID or fingerprint fails before execution. PostgreSQL JSONB existence syntax is JDBC-escaped without dynamic SQL.

## Dataset
`DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE`; version-controlled, idempotent, duplicate-free baseline plus expected-negative inventory; 66 registered cases and a 1001-row limit probe; destroyed with container.

## Identity/Privacy
`SYNTHETIC_ONLY`; all negative identity states fail closed. No raw identity, mapping pair, query, row, endpoint, or credential in evidence.

## P1 Result
`RECONCILED_WITH_EXPECTED_GAPS`; DB-comparable fields use zero tolerance. Existing ordering, event-grain, explicit-preference, transform-policy and protected fingerprint gaps remain explicit.

## P2 Result
`RECONCILED_WITH_MIGRATION_GAPS`; exact P2 exposure/window/events/fallback semantics are preserved. Stale-unexposed assignment and persisted one-observation dedupe remain `MIGRATION_REQUIRED`.

## Checkpoint/Lineage
Equal zero-lag checkpoint, equal explicit snapshot time, and equal lineage fingerprint were enforced for exact parity.

## Evidence
Fixed-order JSON/TSV, counters, role/server state, permission-negative inventory, query inventory, canonical cross-version digest, and five review packages. Runtime evidence records the exact tested PR head; the Draft PR body records the final head and run IDs after the last documentation commit.

## Verification
PostgreSQL 15, PostgreSQL 18, per-version independent verifiers, normalized cross-version equivalence, RCA-0, RCA-1, Recommendation core, backend and IP-12.5 protected regressions passed on the pre-documentation validated head. The final documentation head must repeat the same full suite before completion is claimed.

## Compatibility
Proves deterministic non-production DB reconciliation on PostgreSQL 15/18 only. It does not establish production DB compatibility, production query-plan safety, runtime readiness, scale, latency, or credential safety.

## Risks
Synthetic distribution and isolated query plans do not represent production traffic, identity, credentials, scale, or latency. P1 protected semantic gaps and P2 migration-required dimensions remain unresolved by design.

## Exit Criteria
Both matrix jobs, cross-version equivalence, permission boundaries, P1/P2 classification, checkpoint/lineage, redaction, and all protected regressions must pass on the exact final PR head.

## Handoff
After explicit user review and merge, RCA-2 still requires separate System Coordination authorization. Blocking review packages remain `APPROVAL_STATUS=PENDING_USER_REVIEW` until external reviewers act.
