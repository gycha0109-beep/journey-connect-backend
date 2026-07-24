# SC RCA-1B Allowed Query Boundary

## Scope

Define query families, allowlisting, parameterization, bounding and prohibited access. No query is implemented by SC-4.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; authoritative source classes and Data candidate contracts remain unchanged.

## Decision

Allowed query families:

- `P1_AUTHORITATIVE_REFERENCE_V1`;
- `P1_DATA_CANDIDATE_V1`;
- `P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1`;
- `P2_DATA_CANDIDATE_V1`;
- `SOURCE_CHECKPOINT_V1`;
- `SOURCE_LINEAGE_V1`;
- `BOUNDED_ROW_COUNT_V1`.

```text
QUERY_ALLOWLIST=REQUIRED
DYNAMIC_SQL=FORBIDDEN
UNREVIEWED_RAW_SQL=FORBIDDEN
PREPARED_STATEMENT=REQUIRED
PARAMETER_BINDING=REQUIRED
DETERMINISTIC_ORDER_BY=REQUIRED
EXPLICIT_ROW_LIMIT=REQUIRED
QUERY_FINGERPRINT=REQUIRED
```

Version-controlled static SQL text may exist only behind a registered query ID. It is not a canonical migration and may not be constructed from untrusted fragments.

## Rationale

A semantic allowlist prevents a read-only credential from becoming unrestricted data-export authority.

## Authority

Intelligence approves P1 queries; Reliability approves P2 queries; Data confirms candidate/checkpoint/lineage sources; Operations enforces connection limits; SC registers query IDs.

## Dependencies

Frozen physical object inventory and query fingerprint algorithm in the implementation PR.

## Execution Environment

Queries execute only in CI ephemeral PostgreSQL against deterministic synthetic data.

## DB Access Boundary

Forbidden statements include `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `TRUNCATE`, `ALTER`, `CREATE`, `DROP`, `GRANT`, `REVOKE`, `VACUUM`, `ANALYZE`, `REFRESH MATERIALIZED VIEW`, server-file `COPY`, unbounded scans and system credential catalog reads.

## Query Boundary

Production data, actual identity mapping, unrestricted event history, release evidence source, and P2 canonical dataset row extraction are prohibited. Counts must be bounded by fixture scope and snapshot identifiers.

## Identity/Privacy

Identity parameters are deterministic synthetic references only and are not emitted in evidence.

## Evidence

Store query ID and SHA-256 fingerprint, row counts and classifications. Do not store SQL parameters or raw result rows.

## DB/SQL Impact

No migration, table, view or canonical SQL allocation.

## Production Impact

None.

## Verification

SC-4 checks that an allowlist and prohibited-statement inventory exist. Query execution and fingerprint verification are `NOT_EXECUTED`.

## Risks

A syntactically read-only query can still be unbounded or privacy-invasive. Both semantic family and physical object list must be allowlisted.

## Exit Criteria

Every executed statement maps to one registered ID/fingerprint, uses parameters and deterministic ordering, and remains within limits.

## Handoff

The implementation PR must reject unknown IDs, changed fingerprints, missing limits and any non-SELECT operation before execution.