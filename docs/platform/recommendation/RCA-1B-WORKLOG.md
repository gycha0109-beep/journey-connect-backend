# RCA-1B Worklog

## Scope
Cumulative implementation and verification record for RCA-1B.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; PR #26/SC-4 and PR #25/RCA-1 merge/tree baselines verified before changes.

## Implementation
Phase 1: repository and authority investigation. Phase 2: seven-query registry/fingerprints. Phase 3: bootstrap role/seed. Phase 4: Testcontainers runner/evidence. Phase 5: independent verifier/cross-version CI. Phase 6: documents and review packages.

## Authority
No production authority or source ownership changed.

## Dependencies
GitHub repository, Java 21, Gradle, Testcontainers, PostgreSQL 15/18, Python 3.13.

## Execution Environment
Local work performed static syntax/review only; actual database execution is reserved for exact-head CI.

## DB Access Boundary
Implemented ephemeral owner bootstrap and readonly reconnect; no production credentials or routes.

## Query Boundary
Exactly seven static resources with SHA-256 inventory and fail-closed lookup.

## Dataset
Version-controlled synthetic seed with 66 scenarios, 1001-row probe, idempotency and duplicate assertions.

## Identity/Privacy
Synthetic-only, fail-closed states, hashed/redacted evidence.

## P1 Result
Expected baseline: reconciled with existing semantic gaps; actual result pending CI at document creation.

## P2 Result
Expected baseline: reconciled with migration gaps; actual result pending CI at document creation.

## Checkpoint/Lineage
Zero-lag explicit fixture timestamps and lineage fingerprints implemented.

## Evidence
JSON/TSV writer, counters, role/server state, negative tests, review packages and exact-head verifier implemented.

## Verification
Commands: targeted Gradle test for each matrix, per-version verifier, cross-version comparator, RCA-0/RCA-1 runners, core check, `verifyIp125`. Results are recorded only after CI.

## Compatibility
No production compatibility claim.

## Risks
Canonical schema constraints, privilege semantics and SQLSTATE differences must be validated by CI and corrected on a new exact head if needed.

## Exit Criteria
All required exact-head CI succeeds and self-review finds no protected boundary violation.

## Handoff
Every correction requires all matrix and protected regressions to rerun; no prior-head evidence is final.
