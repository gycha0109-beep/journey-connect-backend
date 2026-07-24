# RCA-1B Implementation Handoff Prompt

## Scope

Implement `RCA-1B Recommendation Data Non-production Read-only Reconciliation` only after the SC-4 authorization PR is explicitly approved and merged. Query actual GitHub `main` before changing files. SC-4 work-start is `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 exact-final-head is `38896b2a37180633870282e9d9e305d9c9fbbf8a`. If SC-4 is not merged, stop with `RCA1B_ENTRY_BLOCKED_BY_SC4_MERGE`.

## Current Baseline

```text
RCA1_OFFLINE_SHADOW_RECONCILIATION_COMPLETE
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
IDENTITY_MODE=SYNTHETIC_ONLY
P1_RESULT=RECONCILED_WITH_EXPECTED_GAPS
P2_RESULT=RECONCILED_WITH_MIGRATION_GAPS
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
NO_AUTHORITY_TRANSFER
```

Current authority:

```text
P1_SOURCE=RecommendationP1ProfileSource
P1_RESULT=recommendation_p1_profile_snapshot
P2_SOURCE=RecommendationP2ObservationSource
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
P2_DATASET=recommendation-evaluation-dataset-v1
P2_METRICS=engagement_rate,fallback_rate
```

## Decision

Implement only:

```text
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
POSTGRESQL_VERSION_MATRIX=15,18
RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE
IDENTITY_MODE=SYNTHETIC_ONLY
TRANSACTION_READ_ONLY=REQUIRED
DB_WRITE=FORBIDDEN
PRODUCTION_DB=FORBIDDEN
CLASSIFICATION=JOINT_INTELLIGENCE_RELIABILITY_ADOPTION
```

Environment B remains deferred. Production replica/derived data and runtime dark read are blocked.

## Rationale

Prove bounded PostgreSQL query behavior and read-only enforcement while reusing RCA-1 taxonomy and preserving every current authority boundary.

## Authority

- Intelligence: P1 query and semantic acceptance.
- Reliability: P2 query, exposure/window/event/fallback semantics, migration gaps and evidence integrity.
- Data: candidate object inventory, checkpoint, lineage and fixture interpretation.
- Operations: container, credentials, network, role, timeout/resource and teardown.
- Privacy/Security: synthetic identity, redaction, secret handling and retention.
- SC: phase entry/exit, query registry, breaking changes, SQL allocation and no-transfer decision.

## Dependencies

Reuse RCA-1 contracts/comparators/taxonomy without changing their behavior. Use canonical SQL `01..52`, current P1/P2 source semantics, Data candidate contracts, explicit captured timestamps and existing Testcontainers infrastructure.

## Allowed Changes

- test-only PostgreSQL 15/18 reconciliation harness;
- version-controlled allowlisted prepared queries and query fingerprints;
- deterministic synthetic database seed and seed digest;
- ephemeral bootstrap/read-only role setup restricted to test resources;
- P1/P2 DB-specific normalized views and lane comparators that reuse RCA-1 taxonomy;
- checkpoint/lineage/row-count/duplicate checks;
- permission-negative, timeout, row-limit and cross-version tests;
- redacted deterministic JSON/TSV evidence, counters, verifier, docs and minimal CI.

## Forbidden Changes

- Java/Kotlin production/runtime source or Spring bean wiring;
- `RecommendationP1ProfileSource`, `RecommendationP2ObservationSource`, RCA-0/RCA-1 behavior or fixtures;
- `jc-recommendation-core` behavior;
- canonical SQL/migration, SQL `53+`, persistent role/grant, new table/view;
- production config, endpoint, credential, network or traffic;
- actual/pseudonymized identity mapping without new approval;
- unbounded/dynamic/unreviewed SQL;
- aggregate-to-event fabrication;
- P2 canonical dataset/hash read/recalculation/rewrite or release-evidence access;
- runtime dark read, feature flag, worker, scheduler, listener, retry or canary;
- authority transfer, main direct push or automatic merge.

## Execution Environment

- one isolated Testcontainers PostgreSQL per matrix job;
- versions 15 and 18, minimum 15;
- UTC timezone, `C` collation/ctype semantics, no extensions/PostGIS;
- no production route or persistent volume;
- canonical schema replay then deterministic seed;
- teardown by container destruction.

## DB Access Boundary

Create test-only `rca1b_readonly` during bootstrap with:

```text
LOGIN=YES_EPHEMERAL_ONLY
INHERIT=NO
BYPASSRLS=NO
CREATEDB=NO
CREATEROLE=NO
REPLICATION=NO
SCHEMA_USAGE=EXPLICIT_ALLOWLIST_ONLY
TABLE_SELECT=EXPLICIT_ALLOWLIST_ONLY
SEQUENCE_SELECT=NO
FUNCTION_EXECUTE=NO_PRIVILEGED_FUNCTIONS
DEFAULT_PRIVILEGES=NONE
WRITE_GRANT=NONE
```

Reconciliation must not use bootstrap owner/superuser.

```text
TRANSACTION_ISOLATION=REPEATABLE_READ
AUTOCOMMIT=DISABLED
TRANSACTION_READ_ONLY=REQUIRED
STATEMENT_TIMEOUT_MS=5000
LOCK_TIMEOUT_MS=1000
IDLE_IN_TRANSACTION_TIMEOUT_MS=5000
MAX_RESULT_ROWS_PER_QUERY=1000
MAX_RECONCILIATION_CASES=10000
MAX_EXECUTION_DURATION_SECONDS=900
MAX_RECONCILIATION_CONNECTIONS=2
PARALLEL_QUERY=DISABLED
CURSOR_FETCH_SIZE=100
RETRY_POLICY=NONE
```

Assert server-visible read-only state before queries. Test that DDL/DML, temp objects and non-allowlisted reads fail. Failure means rollback, connection close, evidence abort and authority unchanged.

## Query Boundary

Register only:

```text
P1_AUTHORITATIVE_REFERENCE_V1
P1_DATA_CANDIDATE_V1
P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1
P2_DATA_CANDIDATE_V1
SOURCE_CHECKPOINT_V1
SOURCE_LINEAGE_V1
BOUNDED_ROW_COUNT_V1
```

Static query text must be version controlled, prepared, parameterized, explicitly bounded, deterministically ordered and SHA-256 fingerprinted. Reject unknown IDs/fingerprints. Never store query parameters or raw rows.

## Reproducible Dataset

Replay canonical SQL `01..52` and apply a noncanonical test-only deterministic seed containing synthetic P1 reference/candidate, P2 assignment/exposure/outcome/candidate, checkpoint/lineage, valid, negative, stale, mismatch and migration-required cases. Seed must be idempotent, duplicate-free, digest-bound and equivalent on 15/18. Do not create a migration.

## Identity/Privacy

Synthetic only. Support valid, absent, invalid, expired, deleted, mismatched, unauthorized-purpose and unauthorized-caller. All failures fail closed. No actual identity object grant, lookup, inference or fallback. Redact subject/user/session/run/exposure before evidence construction.

## P1 Lane

Preserve RCA-1 dimensions and add:

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

Use current `RecommendationP1ProfileSource` semantics as authoritative reference and existing `recommendation-profile-input-v1` projection as candidate. Freeze physical objects from current schema; add no view. Compare exact/shared and deterministic-derived values at zero tolerance; explicit 7/30/90 UTC windows; preserve SQL NULL versus empty; canonicalize ordering. Checkpoint/snapshot equality and lineage match are required. Keep ordering/event-grain/preferences/transform/fingerprint expected/protected gaps. Never fabricate events. Emit independent P1 verdict and inventory.

## P2 Lane

Preserve RCA-1 dimensions and add:

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

Require:

```text
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
OUTCOME_WINDOW_SECONDS=604800
WINDOW_BOUNDARY=EXPOSED_AT_INCLUSIVE__EXPOSED_AT_PLUS_604800_EXCLUSIVE
ENGAGEMENT_EVENTS=click,like,save,share
FALLBACK_SOURCE=BOUND_RECOMMENDATION_RUN_ONLY
ONE_OBSERVATION_KEY=experimentRef,experimentVersion,subjectRef
```

Block general exposure, behavior impression, unsupported events, unbound fallback, duplicate key and exposure/run/session inconsistency. Stale-unexposed and persisted dedupe remain `MIGRATION_REQUIRED`. Do not grant/query canonical dataset or release evidence. Emit independent P2 verdict/inventory and markers:

```text
P2_NON_PRODUCTION_RECONCILIATION_ONLY
CURRENT_P2_AUTHORITY_UNCHANGED
NO_AUTHORITY_TRANSFER
```

## Checkpoint and Lineage

```text
CHECKPOINT_REQUIRED=YES
CHECKPOINT_EQUALITY_REQUIRED=YES_FOR_EXACT_PARITY
MAX_ALLOWED_CHECKPOINT_LAG=0
STALE_SOURCE_THRESHOLD=0_FOR_DETERMINISTIC_FIXTURE
SNAPSHOT_CAPTURE_TIME=EXPLICIT_FIXTURE_TIMESTAMP
LINEAGE_FINGERPRINT_REQUIRED=YES
SYSTEM_CLOCK_DEPENDENCY=FORBIDDEN
```

Missing, inverted, unequal or lineage-mismatched inputs fail closed/inconclusive; no exact parity across different snapshots.

## Evidence

Extend RCA-1 evidence with query ID/fingerprint, source/candidate checkpoint, row counts, database version, environment, isolation, read-only status and timeout. Fixed JSON/TSV ordering, duplicate rejection and exact tested SHA are required. Retention: CI 90 days; DB/raw result/credential none beyond execution.

Add verification counters:

```text
database_query_count
database_query_failure_count
database_write_attempt_blocked_count
result_row_limit_exceeded_count
transaction_read_only_violation_count
p1_query_result_mismatch_count
p2_query_result_mismatch_count
duplicate_row_count
stale_checkpoint_count
timeout_count
```

Counters are verification data, not production SLOs.

## DB/SQL Impact

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE=NO
NEW_VIEW=NO
NEW_ROLE=YES_EPHEMERAL_TEST_ONLY
NEW_GRANT=YES_EPHEMERAL_TEST_ONLY
TEST_FIXTURE_SQL=YES_NONCANONICAL_TEST_ONLY
SQL_53_PLUS=UNALLOCATED
```

If a persistent DB object/role/grant or canonical migration is needed, stop with `RCA1B_ENTRY_BLOCKED_BY_SQL_ALLOCATION`; do not write SQL.

## Production Impact

None. No production DB, runtime wiring, deployment, credentials, traffic, dark read, monitoring or activation.

## Verification

Independent verifier must record exact work-start/SC-4 merge, unchanged RCA history/source/core/SQL/config, version matrix, role/permission boundaries, query inventory/fingerprints, dataset digest, P1/P2 lane execution, checkpoint/lineage, redaction, counters, cross-version equivalence, core/backend/protected regressions and exact final PR head.

Statuses: `PASS`, `FAIL`, `NOT_EXECUTED`, `NOT_APPLICABLE`. Runtime dark read, canary, load, replay, production and actual identity mapping are never PASS in RCA-1B.

## Risks

Report synthetic-distribution limits, query-plan differences, unresolved P1 semantic gaps, P2 migration dimensions, actual identity governance and non-production-only evidence.

## Exit Criteria

Only complete when every SC-4 exit criterion is met separately for P1 and P2, all blocking approvals are exact-head bound, read-only boundaries pass on PostgreSQL 15/18, no production DB/traffic exists and no authority transfer occurs.

## Handoff

Create a separate branch and Draft PR. Do not mark ready or merge without explicit user approval. RCA-2 requires separate SC authorization after RCA-1B completion.