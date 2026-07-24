# RCA-1B Verification Summary

## Scope
Summarizes required exact-head verification without converting out-of-scope checks into PASS.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; SC-4 entry and RCA-1 implementation are merged and authoritative.

## Implementation
Independent static/runtime verifier, PostgreSQL 15/18 matrix, cross-version comparator, permission-negative suite and protected regression job.

## Authority
Machine verification does not replace Intelligence, Reliability, Operations, Privacy/Security or System Coordination review; all five review packages remain `APPROVAL_STATUS=PENDING_USER_REVIEW`.

## Dependencies
Exact PR head, complete artifacts, full Git history, Java 21, Python 3.13, Testcontainers and PostgreSQL images.

## Execution Environment
Two independent ephemeral PostgreSQL jobs, UTC/C locale, tmpfs-only data storage, no persistent volume, no production route and no shared container state.

## DB Access Boundary
`rca1b_readonly` catalog attributes, explicit schema/table allowlist, absent write/sequence/privileged-function rights, server-visible read-only state and post-negative recovery are verified.

## Query Boundary
Exactly seven static query IDs and unique SHA-256 fingerprints; prepared parameters, deterministic order, SQL/JDBC/application row bounds, unknown-ID rejection and pre-execution fingerprint rejection are verified.

## Dataset
Seed ID `rca1b-deterministic-synthetic-database-fixture-v1`; 66 scenario cases; idempotent second application; duplicate exposure/outcome/P1 assertions; no raw seed copy in evidence.

## Identity/Privacy
`SYNTHETIC_ONLY`; raw identity, query text, parameter values, raw rows, connection details and credentials are excluded by the redaction verifier.

## P1 Result
`RECONCILED_WITH_EXPECTED_GAPS`; zero baseline query mismatch. Protected non-comparable semantics remain explicit and do not become authority-transfer evidence.

## P2 Result
`RECONCILED_WITH_MIGRATION_GAPS`; zero baseline query mismatch. Stale-unexposed assignment and persisted observation dedupe remain `MIGRATION_REQUIRED`.

## Checkpoint/Lineage
Checkpoint zero-lag, explicit fixture capture time, monotonic ordering and lineage equality are enforced for exact parity.

## Evidence
Per-version JSON/TSV evidence, role/server state, counters, permission-negative inventory, query inventory, review package and teardown evidence are retained for 90 days. Exact final-head SHA is bound into runtime evidence and independently checked.

## Verification
The PostgreSQL 15/18 runtime tests, per-version independent verifiers, normalized cross-version comparator, static verifier, RCA-0 fixture regression, RCA-1 fixture regression, Recommendation core, backend and IP-12.5 protected readiness passed on the pre-documentation validated head. The final documentation head must rerun all required workflows; previous-head artifacts are not final evidence.

Runtime dark read is `NOT_APPLICABLE`. Production DB validation, production traffic, canary, load, replay, actual identity mapping, production credential validation and production activation are `NOT_EXECUTED`.

## Compatibility
Normalized query inventory, fingerprints, seed digest, lane results, mismatch inventory, counters and redacted JSON/TSV evidence are required to be equal between PostgreSQL 15 and 18 after excluding allowed version metadata.

## Risks
Production plan stability, latency SLO, scale, real credentials, actual identity mapping and source replacement remain unproven. A changed PR head invalidates every previous exact-head result.

## Exit Criteria
All required jobs and verifier assertions pass on the exact final PR head with empty failure lists, while the PR remains Draft and unmerged pending user review.

## Handoff
The Draft PR body records final head, workflow run IDs, technical exit recommendation and pending approval state. RCA-2 requires separate System Coordination authorization.
