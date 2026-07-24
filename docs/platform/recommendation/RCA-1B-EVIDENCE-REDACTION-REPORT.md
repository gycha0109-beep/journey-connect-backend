# RCA-1B Evidence and Redaction Report

## Scope
Defines deterministic database evidence without raw DB material.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; RCA-1 evidence meaning remains unchanged and is only extended by DB-specific records.

## Implementation
Fixed-order JSON/TSV records, duplicate-key rejection, counters, role/server state, query inventory, seed summary, canonical digest and review packages.

## Authority
Reliability reviews integrity; Privacy/Security reviews redaction and retention; approval remains pending.

## Dependencies
SHA-256, fixed explicit evidence timestamp and exact tested SHA.

## Execution Environment
Generated separately per PostgreSQL version; retained 90 days in CI artifacts.

## DB Access Boundary
No credentials, endpoints, host, port, database name, username or JDBC URL.

## Query Boundary
Only query ID and fingerprint are retained; SQL text and parameters are excluded.

## Dataset
Seed ID/version/digest and case count are recorded; raw SQL and rows are not copied.

## Identity/Privacy
No raw user, subject, session, run, exposure or mapping pair. Case IDs and checkpoints are hashed.

## P1 Result
P1 records contain lane, dimension, classification, normalized safe values and row counts.

## P2 Result
P2 records additionally preserve protected/migration classifications without reading dataset/release rows.

## Checkpoint/Lineage
Hashed checkpoint values and lineage fingerprints only.

## Evidence
Duplicate key is `(hashedCaseId,lane,comparisonDimension,queryId,databaseVersion)`. Raw result retention and credential retention are none.

## Verification
Static/runtime redaction scans and deliberate duplicate evidence rejection must pass.

## Compatibility
Canonical digest excludes database-version metadata only.

## Risks
Hashes are verification references, not anonymization suitable for production data.

## Exit Criteria
Schema order, duplicates, redaction, retention markers and exact-head binding pass.

## Handoff
Any production evidence schema requires separate Privacy/Security and SC authorization.
