# RCA-1B Cross-version Equivalence Report

## Scope
Defines the completion gate for PostgreSQL 15/18 normalized equality.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; `CROSS_VERSION_RESULT_EQUIVALENCE=REQUIRED`.

## Implementation
Matrix artifacts are downloaded into an isolated comparison job and compared by the independent Python comparator.

## Authority
SC defines the gate; Operations and lane owners review version-specific results.

## Dependencies
Both successful matrix artifacts and fixed evidence formats.

## Execution Environment
Comparison itself does not connect to a database.

## DB Access Boundary
Only redacted artifacts are read.

## Query Boundary
Query inventory and fingerprints must match exactly.

## Dataset
Seed digest and logical counts must match.

## Identity/Privacy
Synthetic-only and redaction results must match.

## P1 Result
Normalized P1 records, verdict and mismatch inventory must match.

## P2 Result
Normalized P2 records, verdict, migration inventory and authority markers must match.

## Checkpoint/Lineage
Canonical checkpoint and lineage evidence must match.

## Evidence
Canonical result, counters, permission-negative inventory and evidence stripped only of DB version metadata are compared.

## Verification
Any difference produces `FAIL` with explicit difference text and blocks protected regressions.

## Compatibility
Database version strings may differ; semantic output may not.

## Risks
Patch-level or SQLSTATE divergence may expose a real compatibility boundary requiring review.

## Exit Criteria
Comparator returns `PASS` with empty differences.

## Handoff
Cross-version PASS is not production-version approval.
