# SC RCA-1B P1 DB Reconciliation Authority and Equivalence

## Scope

Define the P1 database lane without changing `RecommendationP1ProfileSource`, P1 snapshots or Data candidate authority.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; `P1_RESULT=RECONCILED_WITH_EXPECTED_GAPS` in RCA-1 Model A.

## Decision

RCA-1 P1 dimensions remain mandatory and the following DB dimensions are `APPROVED`:

```text
QUERY_RESULT_PARITY
CHECKPOINT_PARITY
SNAPSHOT_ISOLATION_PARITY
ROW_ORDER_PARITY
NULL_SEMANTICS_PARITY
NUMERIC_NORMALIZATION_PARITY
TIMEZONE_NORMALIZATION_PARITY
DUPLICATE_ROW_DETECTION
SOURCE_ROW_COUNT_PARITY
```

The authoritative reference is the current semantics and bounded SQL behavior of `RecommendationP1ProfileSource`, including explicit preferences and ordered behavior events. The candidate source is the existing `recommendation-profile-input-v1` Data projection under its protected checkpoint and lineage contract. Physical object names must be frozen from canonical schema `01..52`; no new view is authorized.

## Rationale

Database reconciliation must test SQL null/order/time/count behavior while preserving the Model A distinction between comparable aggregates and non-comparable event semantics.

## Authority

Intelligence is accountable for P1 queries and acceptance; Data confirms candidate/checkpoint/lineage; SC protects source authority.

## Dependencies

Explicit snapshot capture time, matching checkpoint, lineage fingerprint, 7/30/90 boundaries and deterministic normalized output.

## Execution Environment

CI ephemeral PostgreSQL 15/18 only.

## DB Access Boundary

Reference and candidate queries are read-only and bounded to synthetic fixture subjects and snapshot time.

## Query Boundary

- exact/shared fields: zero tolerance;
- deterministic derived values: zero tolerance;
- 7/30/90 windows: explicit inclusive lower/exclusive upper boundaries relative to captured UTC time;
- timestamps: UTC `Instant` canonicalization;
- SQL `NULL` and empty string remain distinct tagged values;
- duplicate logical keys: blocking mismatch;
- canonical sorting by registered keys before comparison.

Checkpoint equality and snapshot time equality are required for exact parity. Missing, inverted, stale or unequal checkpoints produce fail-closed/inconclusive results. Maximum lag is zero in the deterministic dataset.

## Identity/Privacy

Synthetic-only binding. Aggregate counts must never be expanded into fabricated event rows.

## Evidence

Store normalized values, row counts, query fingerprints and categorical gaps; never raw behavior rows.

## DB/SQL Impact

No source change, table, view, role persistence or canonical SQL allocation.

## Production Impact

None.

## Verification

SC-4 verifies the decision only. P1 query execution is `NOT_EXECUTED`.

## Risks

Ordering, event grain, explicit preference availability, transform/decay/saturation and current fingerprint semantics remain expected/protected gaps. A DB mismatch against Model A is a blocker when the same normalized comparable dimension and checkpoint are used; otherwise it is inconclusive until inputs align.

## Exit Criteria

`P1_DATABASE_RESULTS_CLASSIFIED` with no baseline unexpected mismatch on comparable dimensions, explicit expected gaps, matching checkpoint/lineage and unchanged P1 authority.

## Handoff

Implementation must emit a P1-only verdict and mismatch inventory. It may not use a combined P1/P2 PASS.