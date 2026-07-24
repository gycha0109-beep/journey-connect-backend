# RCA-1B PostgreSQL 15/18 Compatibility Report

## Scope
Defines cross-version reconciliation equivalence, not production compatibility.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; approved matrix: `15,18`; minimum: `15`.

## Implementation
The same canonical SQL, seed, query bytes, role contract, Java normalizer and verifier execute independently on both versions.

## Authority
SC approved the version matrix. Operations and Reliability review the resulting package; approval remains pending user review.

## Dependencies
Official PostgreSQL Alpine images and the repository's existing PostgreSQL JDBC/Testcontainers stack.

## Execution Environment
Timezone `UTC`, locale/ctype `C`, no PostGIS, no RCA-1B extension-specific semantics.

## DB Access Boundary
Role attributes and server-visible transaction state must be equivalent on both versions.

## Query Boundary
Query inventory and SHA-256 fingerprints must be byte-identical.

## Dataset
Seed ID, version, digest, logical case count and idempotency result must be identical.

## Identity/Privacy
Both versions use the same synthetic-only identity cases and redaction policy.

## P1 Result
Normalized P1 rows, dimensions, verdict and mismatch inventory must be equal.

## P2 Result
Normalized P2 rows, dimensions, verdict, migration inventory and authority markers must be equal.

## Checkpoint/Lineage
Checkpoint ordering and lineage results must be identical.

## Evidence
A canonical evidence digest excludes only database version metadata. JSON/TSV payloads are otherwise compared.

## Verification
`compare_rca1b_versions.py` compares canonical result, query inventory, counters, negative results and normalized evidence.

## Compatibility
`CROSS_VERSION_RESULT_EQUIVALENCE=REQUIRED`; divergence blocks completion.

## Risks
PostgreSQL patch-level strings may differ and are retained only as version-specific metadata.

## Exit Criteria
Both version artifacts exist exactly once and canonical comparison returns `PASS`.

## Handoff
Production-version selection and production query-plan validation remain outside RCA-1B.
