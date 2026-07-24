# SC RCA-1B PostgreSQL Version and Compatibility Decision

## Scope

Define supported PostgreSQL versions and deterministic database settings for RCA-1B.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`. Existing protected CI already executes Testcontainers against PostgreSQL 15 and 18.

## Decision

```text
POSTGRESQL_MINIMUM_VERSION=15
POSTGRESQL_VERSION_MATRIX=15,18
VERSION_RESULT_EQUIVALENCE=REQUIRED
POSTGIS=NOT_REQUIRED
EXTENSIONS=NONE_REQUIRED
DATABASE_TIMEZONE=UTC
LC_COLLATE=C
LC_CTYPE=C
```

PostgreSQL 15 and 18 must produce identical normalized lane classifications, counters and redacted evidence except the explicit `databaseVersion` field.

## Rationale

The matrix matches existing protected regression coverage. The reconciliation contract uses ordinary SQL semantics and does not require PostGIS or another extension.

## Authority

Operations owns image selection; Data confirms canonical schema compatibility; Intelligence and Reliability approve semantic equivalence; SC controls version changes.

## Dependencies

Published PostgreSQL container images, canonical schema, fixed seed and query allowlist.

## Execution Environment

Each version runs in an independent ephemeral job with the same seed, timezone, collation, query fingerprints and captured timestamps.

## DB Access Boundary

Unsupported versions abort before schema replay or reconciliation login creation.

## Query Boundary

No version-specific query branch is allowed unless SC classifies it as a contract change.

## Identity/Privacy

No version job may use actual identity data.

## Evidence

Record exact server version and normalized result digest; do not record server endpoint.

## DB/SQL Impact

No migration or extension installation.

## Production Impact

None.

## Verification

SC-4 validates that the current CI matrix contains 15 and 18. Query result equivalence remains `NOT_EXECUTED` until RCA-1B implementation.

## Risks

Planner differences may affect timing but must not change bounded normalized results. Locale drift can alter ordering and therefore fails closed.

## Exit Criteria

Both versions pass the same query inventory, read-only assertions and lane results.

## Handoff

Implementation must expose per-version evidence and an explicit cross-version equivalence assertion.