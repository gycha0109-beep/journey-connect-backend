# RCA-1B Query Registry and Fingerprint Report

## Scope
Defines the only SQL queries executable by RCA-1B.

## Current Baseline
Work-start: `d07091bff54a3bfdae10d8fb6f3008923d69d455`; SC-4 authorized exactly seven query IDs.

## Implementation
Static UTF-8 resources use LF normalization, trailing-whitespace removal, one terminal newline and SHA-256. Query ID and fingerprint are one-to-one.

## Authority
P1 query semantics require Intelligence review; P2 semantics require Reliability review; registry changes require SC review.

## Dependencies
PreparedStatement, positional binding, query inventory TSV and test-resource SQL.

## Execution Environment
The same query bytes execute on PostgreSQL 15 and 18.

## DB Access Boundary
Each definition records allowed and prohibited objects; unknown ID or fingerprint fails before DB execution.

## Query Boundary
`P1_AUTHORITATIVE_REFERENCE_V1`, `P1_DATA_CANDIDATE_V1`, `P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1`, `P2_DATA_CANDIDATE_V1`, `SOURCE_CHECKPOINT_V1`, `SOURCE_LINEAGE_V1`, `BOUNDED_ROW_COUNT_V1` only. Every query has deterministic `ORDER BY`, explicit `LIMIT ?`, max rows 1000 and parameter metadata.

## Dataset
Queries are bounded to synthetic case identifiers or explicit fixture references.

## Identity/Privacy
Raw SQL, parameters and rows are excluded from evidence; only ID and fingerprint are retained.

## P1 Result
Two P1 query definitions compare authoritative snapshot semantics with Data candidate projection.

## P2 Result
Two P2 definitions preserve exposure authority, exact 604800-second window, allowed events and bound fallback.

## Checkpoint/Lineage
Dedicated checkpoint and lineage queries are separately fingerprinted.

## Evidence
Runtime query inventory includes ID, lane, resource, fingerprint, max rows, order key and parameter names.

## Verification
Static verifier recalculates all fingerprints and rejects duplicate fingerprints, unbounded queries and missing order.

## Compatibility
Query inventory and fingerprints must be identical across versions.

## Risks
Any query text change intentionally invalidates the inventory until explicitly reviewed.

## Exit Criteria
Exactly seven entries pass static and runtime registry checks.

## Handoff
RCA-2 may not reuse this registry for production without separate authorization.
