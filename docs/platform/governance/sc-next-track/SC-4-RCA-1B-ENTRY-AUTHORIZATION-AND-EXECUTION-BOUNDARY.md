# SC-4 RCA-1B Non-production Read-only Reconciliation Entry Authorization & Execution Boundary

## Scope

Authorize governance entry for `RCA-1B Recommendation Data Non-production Read-only Reconciliation`. This document does not implement a DB query, role, grant, SQL, seed, Java/Kotlin source, runtime wiring, production access, or identity mapping.

## Current Baseline

- authoritative main/work-start: `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`
- PR #25 merged; RCA-1 exact-final-head: `38896b2a37180633870282e9d9e305d9c9fbbf8a`
- merge tree equals exact-final-head tree
- `RCA1_OFFLINE_SHADOW_RECONCILIATION_COMPLETE`
- `P1_RESULT=RECONCILED_WITH_EXPECTED_GAPS`
- `P2_RESULT=RECONCILED_WITH_MIGRATION_GAPS`
- `IDENTITY_MODE=SYNTHETIC_ONLY`
- SQL `01..52` protected; SQL `53+` absent and unallocated
- production activation not authorized; current P1/P2 authority unchanged

## Decision

```text
RCA1B_ENTRY_AUTHORIZED
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
POSTGRESQL_VERSION_MATRIX=15,18
RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE
IDENTITY_MODE=SYNTHETIC_ONLY
TRANSACTION_READ_ONLY=REQUIRED
DB_WRITE=FORBIDDEN
PRODUCTION_DB=FORBIDDEN
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

Environment A is `APPROVED`. Environment B is `DEFERRED` to separate SC/Operations/Privacy approval. Environment C is `BLOCKED`.

## Rationale

The repository already validates PostgreSQL 15 and 18 through isolated Testcontainers. An ephemeral database can replay canonical SQL `01..52`, seed synthetic deterministic data, create a test-only least-privilege role, execute bounded read-only comparisons, and be destroyed without persistent DB change. This adds database-query evidence while preserving Model A taxonomy and all current authority.

## Authority

- Intelligence: P1 query semantics and acceptance.
- Reliability: P2 exposure/outcome/metric semantics and evidence integrity.
- Data: candidate projection schema, checkpoint, lineage and fixture interpretation.
- Operations: ephemeral environment, credentials, network isolation and resource limits.
- Privacy/Security: synthetic-only identity, redaction and retention.
- System Coordination: entry/exit, registry, breaking changes, SQL allocation and prohibition of authority transfer.

## Dependencies

RCA-1 contracts and comparators, canonical schema `01..52`, existing PostgreSQL 15/18 test infrastructure, deterministic synthetic seed, explicit captured timestamps, frozen query IDs and fingerprints, and lane-specific acceptance.

## Execution Environment

- CI ephemeral PostgreSQL only.
- production network routes and endpoints prohibited.
- PostgreSQL 15 is minimum; 15 and 18 are required matrix targets.
- UTC timezone, `C` collation/ctype semantics, deterministic ordering, no PostGIS or other extension.
- one isolated database per matrix job; teardown at job end.

## DB Access Boundary

```text
TRANSACTION_ISOLATION=REPEATABLE_READ
AUTOCOMMIT=DISABLED
STATEMENT_TIMEOUT_MS=5000
LOCK_TIMEOUT_MS=1000
IDLE_IN_TRANSACTION_TIMEOUT_MS=5000
MAX_RESULT_ROWS_PER_QUERY=1000
MAX_RECONCILIATION_CASES=10000
MAX_EXECUTION_DURATION_SECONDS=900
PARALLEL_QUERY=DISABLED
CURSOR_FETCH_SIZE=100
RETRY_POLICY=NONE
```

DDL, DML, temporary objects, server-file COPY, function/trigger creation, schema changes, migration, lock escalation, owner/superuser use and write grants are forbidden. Both session default and each reconciliation transaction must assert read-only.

## Query Boundary

Only version-controlled query IDs in an explicit allowlist may execute. Queries must be prepared/parameterized, bounded, deterministically ordered and fingerprinted. Allowed families are P1/P2 authoritative reference, Data candidate projection, checkpoint, lineage and bounded counts. Dynamic or unreviewed raw SQL, production data, actual identity mapping, canonical dataset rows and release evidence are forbidden.

## Identity/Privacy

`SYNTHETIC_ONLY` remains mandatory. Test identities have no connection to actual users. All absent, invalid, expired, deleted, mismatched, unauthorized-purpose and unauthorized-caller conditions fail closed. Raw identifiers, mapping pairs, connection strings, hosts, credentials and row dumps are prohibited in evidence.

## Evidence

Evidence extends RCA-1 with query fingerprint, source/candidate checkpoint, row counts, PostgreSQL version, environment, isolation and timeout. It stores normalized/redacted comparison values only. CI evidence retention is 90 days; database and raw results exist only for the execution lifetime.

## DB/SQL Impact

```text
DB_CHANGE_REQUIRED=NO
SQL_ALLOCATION_REQUIRED=NO
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=YES_EPHEMERAL_TEST_ONLY
NEW_GRANT_REQUIRED=YES_EPHEMERAL_TEST_ONLY
TEST_FIXTURE_SQL_REQUIRED=YES_NONCANONICAL_TEST_ONLY
SQL_53_PLUS=UNALLOCATED
```

The ephemeral role/grant and seed belong only to test setup and are not canonical migrations. No SQL is created by SC-4.

## Production Impact

None. No production DB read, credential, deployment, traffic, dark read, feature flag, scheduler, alert, dashboard, canary or authority transfer is authorized.

## Verification

SC-4 verifies baseline and merge facts, decision uniqueness, limits, query/identity/evidence policy, governance-only diff, protected sources/config/SQL and historical evidence immutability. It does not execute PostgreSQL reconciliation, role permission tests, queries, runtime, canary, load, replay, production or actual identity mapping.

## Risks

- ephemeral fixtures do not prove persistent non-production contamination control or production distributions;
- physical Data projection object inventory must be frozen before implementation;
- query plans may differ by PostgreSQL version even when normalized results match;
- P1 expected gaps and P2 migration-required dimensions remain unresolved;
- role/grant enforcement is not proven until RCA-1B implementation CI executes it.

## Exit Criteria

`NON_PRODUCTION_DB_RECONCILIATION_EXECUTED`, `P1_DATABASE_RESULTS_CLASSIFIED`, `P2_DATABASE_RESULTS_CLASSIFIED`, `CHECKPOINT_BOUNDARY_ENFORCED`, `LINEAGE_BOUNDARY_ENFORCED`, `READ_ONLY_BOUNDARY_ENFORCED`, `IDENTITY_BOUNDARY_ENFORCED`, `MODEL_A_AND_MODEL_B_TAXONOMY_ALIGNED`, `PROTECTED_AUTHORITY_UNCHANGED`, `NO_PRODUCTION_DATABASE`, `NO_PRODUCTION_TRAFFIC`, `NO_AUTHORITY_TRANSFER`.

## Handoff

Implementation requires the separate prompt `37-RCA-1B-IMPLEMENTATION-HANDOFF-PROMPT.md`, a new branch and Draft PR. RCA-2 runtime dark read remains separately gated and is not automatically authorized by RCA-1B completion.