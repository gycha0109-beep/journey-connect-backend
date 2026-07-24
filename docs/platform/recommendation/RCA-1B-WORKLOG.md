# RCA-1B Worklog

## Scope
Cumulative implementation, correction, verification and self-review record for RCA-1B.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; PR #26/SC-4 and PR #25/RCA-1 merge/tree baselines were verified before changes. Canonical SQL `01..52` remained protected and SQL `53+` remained absent.

## Implementation
1. Repository and authority investigation.
2. Seven-query registry, canonical byte policy and fingerprint inventory.
3. Ephemeral bootstrap role/grant and deterministic synthetic seed.
4. Testcontainers PostgreSQL 15/18 runner, read-only transaction enforcement, permission-negative suite and evidence writer.
5. Independent verifier, cross-version comparator and protected regression workflow.
6. Nineteen implementation/result/handoff documents and five blocking-review packages.
7. Exact-head CI correction and final self-review.

## Authority
No production authority, source ownership, runtime wiring, DB writer, P1/P2 semantics or release authority changed.

## Dependencies
GitHub repository, Java 21, Gradle, Testcontainers, PostgreSQL 15/18, Python 3.13 and existing canonical SQL/verification fragments.

## Execution Environment
Each matrix job starts one isolated ephemeral container, initializes UTC/C locale, applies canonical SQL `01..52`, seeds deterministic fixtures, reconnects through `rca1b_readonly`, generates evidence and destroys the container. PostgreSQL 15 and 18 use version-appropriate tmpfs data paths.

## DB Access Boundary
The bootstrap owner is limited to schema/role/seed setup. Reconciliation never uses the owner connection. Catalog attributes, explicit allowlist, no write/sequence/privileged-function rights and server-visible read-only repeatable-read state are verified.

## Query Boundary
Exactly seven static resources with unique SHA-256 inventory, prepared parameters, deterministic order, SQL/JDBC/application row limits and fail-closed ID/fingerprint lookup. PostgreSQL JSONB `?` is JDBC-escaped as `??`; no dynamic SQL was introduced.

## Dataset
Version-controlled synthetic seed with 66 scenarios and a 1001-row probe. The seed is idempotent and verifies duplicate exposure, duplicate outcome and duplicate P1-row constraints.

## Identity/Privacy
Synthetic-only, fail-closed identity states, redacted deterministic references and evidence scans for raw identity/query/row/credential material.

## P1 Result
`RECONCILED_WITH_EXPECTED_GAPS`; comparable fields and 7/30/90 windows reconcile with zero baseline mismatch. Ordering, event grain, explicit preference, transform policy and fingerprint semantics remain expected/protected gaps.

## P2 Result
`RECONCILED_WITH_MIGRATION_GAPS`; authoritative experiment exposure, assignment/version/variant, subject/session/run binding, 604800-second boundary, engagement event allowlist and bound fallback reconcile. Stale-unexposed assignment and persisted one-observation dedupe remain migration-required.

## Checkpoint/Lineage
Zero-lag explicit fixture timestamps, monotonic checkpoint ordering, equal snapshot capture and equal lineage fingerprints were enforced.

## Evidence
Fixed-order JSON/TSV evidence, counters, role/server state, permission-negative results, query inventory, review package and teardown evidence. Runtime evidence records the exact tested head. CI retention is 90 days; DB state exists only for the container lifetime.

## Verification
Executed commands and stages:
- targeted Gradle database test for PostgreSQL 15 and 18;
- per-version independent verifier;
- normalized cross-version comparator;
- immutable RCA-0 and RCA-1 fixture runners;
- Recommendation core check;
- backend/IP-12.5 protected readiness;
- standalone Recommendation P0, RCA-0 and Backend PR workflows.

Pre-documentation validation run `30120132626` passed PostgreSQL 15, PostgreSQL 18, cross-version equivalence and the full protected-regression job. Standalone runs `30120132640`, `30120132722` and `30120132672` also passed. Because this worklog update changes the head, the final documentation head must rerun the complete suite and only that result is final evidence.

## Corrections and Self-review
- Scoped the targeted Gradle test to the backend root project.
- Kept Testcontainers on the repository-provided dependency/classpath.
- Added PostgreSQL 18 data-layout handling without persistent volume.
- Isolated historical rollback-only SQL 28 and SQL 42 validation conflicts in the test harness without modifying canonical SQL.
- Staged SQL 51 implementation fragments at their original relative paths.
- Corrected the deterministic seed user-column reference.
- Escaped the PostgreSQL JSONB existence operator for JDBC and synchronized its fingerprint.
- Bound the exact PR head into the forked test JVM.
- Confirmed no production source/config, canonical SQL, RCA-0/RCA-1 asset or Recommendation core change.

## Compatibility
PostgreSQL 15/18 normalized evidence equivalence is required and was demonstrated on the pre-documentation validation head. No production compatibility, runtime readiness or source-replacement claim is made.

## Risks
Production query plans, traffic, credentials, latency, load, actual identity mapping and operational approval remain unexecuted. P1 expected semantic gaps and P2 migration-required dimensions remain open by design.

## Exit Criteria
The exact final documentation head must pass both matrix jobs, both per-version verifiers, cross-version equivalence, RCA-0/RCA-1/Core/Backend/IP-12.5 regressions and standalone protection workflows. Review packages remain `PENDING_USER_REVIEW`.

## Handoff
Do not mark Ready or merge without explicit user approval. RCA-2 requires separate System Coordination authorization. Final PR head and final workflow IDs are recorded in the Draft PR body after the last exact-head run.
