# Journey Connect System Contract V1

## 1. Document identity

| Field | Value |
|---|---|
| contract ID | `jc-system-contract-v1` |
| revision | `V1.5 / SC-4 RCA-1B ENTRY` |
| status | `ACTIVE / RCA1_COMPLETE / RCA1B_ENTRY_AUTHORIZED` |
| authoritative main | `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4` |
| RCA-1 exact-final-head | `38896b2a37180633870282e9d9e305d9c9fbbf8a` |
| historical SC-3 marker | `V1.4 / SC-3 RCA-1 ENTRY` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| SQL `53+` | `UNALLOCATED` |
| date | `2026-07-24` |

## 2. Authoritative state

- PR #25 is merged at `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`.
- RCA-1 exact-final-head `38896b2a37180633870282e9d9e305d9c9fbbf8a` is an ancestor and has an identical tree to the merge commit.
- `RCA1_OFFLINE_SHADOW_RECONCILIATION_COMPLETE`.
- `P1_RESULT=RECONCILED_WITH_EXPECTED_GAPS`.
- `P2_RESULT=RECONCILED_WITH_MIGRATION_GAPS`.
- `IDENTITY_MODE=SYNTHETIC_ONLY`.
- `DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE` and historical RCA-0 completion remain preserved.
- SQL `01..52` is protected; SQL `53+` is absent/unallocated.
- `PRODUCTION_ACTIVATION=NOT_AUTHORIZED`.
- `CURRENT_P1_P2_AUTHORITY_UNCHANGED` and `NO_AUTHORITY_TRANSFER`.

## 3. Track and authority boundary

| Area | Semantic owner | Restriction |
|---|---|---|
| Data candidate projection/checkpoint/lineage | Data | non-authoritative candidate only |
| P1 source/result and semantic acceptance | Intelligence | `RecommendationP1ProfileSource`; `recommendation_p1_profile_snapshot` protected |
| P2 exposure/outcome/metric/release | Reliability | `RecommendationP2ObservationSource`; P2 authority protected |
| CI DB environment/credential/resource | Operations | non-production ephemeral only |
| privacy/redaction/retention | Privacy/Security | synthetic-only; raw rows/IDs prohibited |
| registry, entry/exit, SQL allocation, authority transfer | System Coordination | approval authority |

`RCA` is Recommendation Consumer Adoption, a cross-track workstream. `RP` is reserved for Reliability Platform and never means Recommendation Platform.

## 4. Current Recommendation authority

```text
P1_SOURCE=RecommendationP1ProfileSource
P1_RESULT=recommendation_p1_profile_snapshot
P2_SOURCE=RecommendationP2ObservationSource
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
P2_DATASET=recommendation-evaluation-dataset-v1
P2_METRICS=engagement_rate,fallback_rate
```

General exposure, behavior impression and P2 experiment exposure are distinct. `view`, `hide`, and `report` are not P2 engagement events.

## 5. RCA sequence

```text
RCA-0 Recommendation Data Consumer Contract & Fixture Alignment [COMPLETE / MERGED]
RCA-1 Recommendation Data Shadow Reconciliation [COMPLETE / MODEL A]
RCA-1B Recommendation Data Non-production Read-only Reconciliation [ENTRY AUTHORIZED]
RCA-2 Controlled Runtime Dark Read [NOT AUTHORIZED]
```

Historical entry marker `RCA1_ENTRY_AUTHORIZED` remains true for the completed RCA-1 phase.

## 6. RCA-1B execution decision

```text
RCA1B_ENTRY_AUTHORIZED
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
POSTGRESQL_VERSION_MATRIX=15,18
RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE
IDENTITY_MODE=SYNTHETIC_ONLY
TRANSACTION_READ_ONLY=REQUIRED
DB_WRITE=FORBIDDEN
PRODUCTION_DB=FORBIDDEN
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

Environment B is deferred; production replica/derived environments are blocked.

## 7. PostgreSQL and execution contract

```text
POSTGRESQL_MINIMUM_VERSION=15
POSTGRESQL_VERSION_MATRIX=15,18
TRANSACTION_ISOLATION=REPEATABLE_READ
AUTOCOMMIT=DISABLED
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

Timezone is UTC; collation/ctype semantics are `C`; no PostGIS or extension is required. DDL, DML, temporary objects, server-file COPY, function/trigger creation, schema changes, migrations, lock escalation, owner/superuser use and write grants are prohibited.

## 8. Role, grant and SQL sequence

```text
RCA1B_ROLE_REQUIRED=YES_EPHEMERAL_TEST_ONLY
RCA1B_ROLE_NAME=rca1b_readonly
BYPASSRLS_ALLOWED=NO
CREATEDB_ALLOWED=NO
CREATEROLE_ALLOWED=NO
REPLICATION_ALLOWED=NO
SCHEMA_USAGE=EXPLICIT_ALLOWLIST_ONLY
TABLE_SELECT=EXPLICIT_ALLOWLIST_ONLY
WRITE_GRANT=FORBIDDEN
OWNER_ROLE_USE=FORBIDDEN
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
TEST_FIXTURE_SQL_REQUIRED=YES_NONCANONICAL_TEST_ONLY
```

The test-only role/grants and synthetic seed are destroyed with the container. They are not canonical migrations. SQL `01..52` remains immutable and SQL `53+` remains unallocated.

## 9. Query boundary

Only registered, version-controlled, prepared, parameterized, bounded, deterministically ordered and fingerprinted query IDs may run:

```text
P1_AUTHORITATIVE_REFERENCE_V1
P1_DATA_CANDIDATE_V1
P2_AUTHORITATIVE_EXPOSURE_OUTCOME_V1
P2_DATA_CANDIDATE_V1
SOURCE_CHECKPOINT_V1
SOURCE_LINEAGE_V1
BOUNDED_ROW_COUNT_V1
```

Dynamic/unreviewed SQL, unbounded scans, production data, actual identity mapping, canonical dataset rows, release evidence and credential catalogs are prohibited.

## 10. Dataset and identity

`RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE`. Canonical SQL `01..52` is replayed into an ephemeral database and followed by noncanonical test-only seed setup. Dataset B is deferred and production-derived Dataset C is blocked.

Identity remains synthetic-only:

```text
IDENTITY_MAPPING_OWNER=NOT_REQUIRED_FOR_SYNTHETIC_ONLY
IDENTITY_MAPPING_AUTHORITY=NONE
IDENTITY_MAPPING_STORAGE=EPHEMERAL_TEST_FIXTURE_ONLY
IDENTITY_MAPPING_RETENTION=EXECUTION_LIFETIME_ONLY
IDENTITY_MAPPING_DELETION=CONTAINER_DESTRUCTION
IDENTITY_MAPPING_AUDIT=HASHED_CASE_AND_CLASSIFICATION_ONLY
IDENTITY_MAPPING_PURPOSE_BINDING=RCA1B_NONPRODUCTION_RECONCILIATION_ONLY
IDENTITY_MAPPING_LOGGING_POLICY=NO_RAW_ID_OR_MAPPING_PAIR
IDENTITY_MAPPING_FAILURE_POLICY=FAIL_CLOSED
```

Actual identity mapping is blocked. Anonymous, nearest-user, alternate-subject and inferred-ID fallback are prohibited.

## 11. P1 database reconciliation

RCA-1 dimensions remain and DB-specific dimensions are separate: query result, checkpoint, snapshot isolation, row order, null semantics, numeric/timezone normalization, duplicate rows and source row count.

Comparable exact/shared, deterministic-derived and 7/30/90 windows use zero mismatch tolerance. Checkpoint/snapshot equality and lineage match are required for exact parity. SQL NULL and empty remain distinct. Aggregate-to-event fabrication is forbidden. Ordering, event grain, explicit preferences, transform policy and fingerprint semantics remain expected/protected gaps.

## 12. P2 database reconciliation

```text
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
OUTCOME_WINDOW_SECONDS=604800
WINDOW_BOUNDARY=EXPOSED_AT_INCLUSIVE__EXPOSED_AT_PLUS_604800_EXCLUSIVE
ENGAGEMENT_EVENTS=click,like,save,share
FALLBACK_SOURCE=BOUND_RECOMMENDATION_RUN_ONLY
ONE_OBSERVATION_KEY=experimentRef,experimentVersion,subjectRef
```

P2 DB dimensions include query/checkpoint parity, exposure/outcome uniqueness, duplicate observation, window SQL, event filter, fallback and assignment-version joins, and source row count. Stale-unexposed assignment and persisted dedupe remain `MIGRATION_REQUIRED`. Canonical dataset/hash and release evidence are excluded from query scope.

Required markers:

```text
P2_NON_PRODUCTION_RECONCILIATION_ONLY
CURRENT_P2_AUTHORITY_UNCHANGED
NO_AUTHORITY_TRANSFER
```

## 13. Checkpoint, lineage and evidence

```text
CHECKPOINT_REQUIRED=YES
CHECKPOINT_EQUALITY_REQUIRED=YES_FOR_EXACT_PARITY
MAX_ALLOWED_CHECKPOINT_LAG=0
STALE_SOURCE_THRESHOLD=0_FOR_DETERMINISTIC_FIXTURE
SNAPSHOT_CAPTURE_TIME=EXPLICIT_FIXTURE_TIMESTAMP
LINEAGE_FINGERPRINT_REQUIRED=YES
SYSTEM_CLOCK_DEPENDENCY=FORBIDDEN
CI_EVIDENCE_RETENTION_DAYS=90
DB_SNAPSHOT_RETENTION=EXECUTION_LIFETIME_ONLY
RAW_RESULT_RETENTION=NONE
```

Evidence stores query fingerprints, normalized/redacted values, checkpoints, lineage, row counts, PostgreSQL version, transaction controls, verifier version and exact tested SHA. Raw rows, IDs, credentials, hosts and connection strings are prohibited.

## 14. Failure and abort

Production endpoint detection, owner/superuser use, write privilege, read-only off, allowlist violation, missing timeout/limit, unbounded query, checkpoint inversion, lineage mismatch, unsupported version, schema mismatch, P2 authority/window/event/fallback mismatch, raw identity evidence, protected-data access or protected diff immediately aborts.

Abort behavior: transaction rollback, connection close, evidence-generation abort, execution FAIL, candidate result not approved, authority markers unchanged.

## 15. Completion and production gates

RCA-1B completion requires independent P1/P2 DB results, checkpoint/lineage/read-only/identity enforcement, Model A taxonomy alignment, unchanged authority, no production DB/traffic and no authority transfer.

Completion does not authorize runtime dark read, production DB reads, source replacement, production activation, authority transfer or automatic RCA-2 entry.

## 16. Absolute prohibitions

- canonical SQL rewrite or SQL `53+` allocation;
- persistent role/grant or production-derived data;
- production DB/network/credential/traffic;
- Java/Kotlin runtime wiring in the SC-4 PR;
- actual identity mapping or raw row evidence;
- P1/P2 source/core/dataset/hash/release changes;
- runtime dark read, feature flag, worker, scheduler or canary;
- main direct push or merge without explicit user approval.
