# SC RCA-1B P2 DB Reconciliation Authority and Equivalence

## Scope

Define the P2 database lane without changing exposure, outcome, metric, dataset, hash, release or authority semantics.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; `P2_RESULT=RECONCILED_WITH_MIGRATION_GAPS` in RCA-1 Model A.

## Decision

RCA-1 P2 dimensions remain mandatory and the following DB dimensions are `APPROVED`:

```text
QUERY_RESULT_PARITY
CHECKPOINT_PARITY
EXPOSURE_ROW_UNIQUENESS
OUTCOME_ROW_UNIQUENESS
DUPLICATE_OBSERVATION_DETECTION
WINDOW_BOUNDARY_SQL_PARITY
EVENT_TYPE_FILTER_PARITY
FALLBACK_JOIN_PARITY
ASSIGNMENT_VERSION_JOIN_PARITY
SOURCE_ROW_COUNT_PARITY
```

Protected rules:

```text
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
OUTCOME_WINDOW_SECONDS=604800
WINDOW_BOUNDARY=EXPOSED_AT_INCLUSIVE__EXPOSED_AT_PLUS_604800_EXCLUSIVE
ENGAGEMENT_EVENTS=click,like,save,share
FALLBACK_SOURCE=BOUND_RECOMMENDATION_RUN_ONLY
ONE_OBSERVATION_KEY=experimentRef,experimentVersion,subjectRef
```

The authority row is identified through the protected assignment-to-`recommendation_p2_experiment_exposure` relation and exact exposure/run/session consistency. General exposure and behavior impression objects are outside the query allowlist.

## Rationale

The current source query already enforces P2 exposure, a seven-day exclusive upper boundary, valid events and bound fallback joins. RCA-1B must prove equivalent database behavior without rewriting canonical dataset or release evidence.

## Authority

Reliability is accountable for P2 query semantics, mismatch acceptance and evidence integrity; Intelligence may implement shared comparison code; Data confirms candidate projection/checkpoint/lineage; SC protects authority.

## Dependencies

Synthetic assignments/exposures/outcomes, exact experiment/version/variant, checkpoint, lineage and deterministic captured time.

## Execution Environment

CI ephemeral PostgreSQL 15/18 only.

## DB Access Boundary

Read-only access is restricted to allowlisted synthetic P2 source/candidate/checkpoint/lineage objects. Canonical dataset and release-evidence objects receive no grant.

## Query Boundary

Unsupported exposure kinds and `view`, `hide`, `report` events are blocking mismatches. Fallback must join the exposure's bound run. Duplicate observation key or inconsistent exposure/run/session is blocking. Stale-unexposed assignment is detection-only and remains `MIGRATION_REQUIRED`. Persisted dedupe equivalence remains `MIGRATION_REQUIRED`.

## Identity/Privacy

Synthetic subject/session/run/exposure identifiers only; evidence uses hashes and safe normalized values.

## Evidence

Required markers:

```text
P2_NON_PRODUCTION_RECONCILIATION_ONLY
CURRENT_P2_AUTHORITY_UNCHANGED
NO_AUTHORITY_TRANSFER
```

Canonical dataset bytes/hash are not read or recalculated. Release evidence is excluded from query and evidence scope.

## DB/SQL Impact

No P2 table/query source change, migration, view or canonical SQL allocation.

## Production Impact

None.

## Verification

SC-4 verifies the policy only. P2 query execution and uniqueness tests are `NOT_EXECUTED`.

## Risks

Stale assignment and persisted dedupe remain migration dimensions. A query result cannot be promoted to canonical dataset or release evidence.

## Exit Criteria

`P2_DATABASE_RESULTS_CLASSIFIED` with exact protected dimensions, separately reported migration gaps, no authority mismatch and required non-production/no-transfer markers.

## Handoff

Implementation must emit a P2-only verdict and mismatch inventory and must fail if P2 is hidden behind a combined lane result.