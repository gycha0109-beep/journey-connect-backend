# System Coordination Handoff

## Status

`RCA1_OFFLINE_SHADOW_RECONCILIATION_COMPLETE / RCA1B_ENTRY_AUTHORIZED`

Historical marker `RCA0_CONTRACT_AND_FIXTURE_COMPLETE / RCA1_ENTRY_AUTHORIZED` remains satisfied.

## Authoritative baseline

- repository: `gycha0109-beep/journey-connect-backend`;
- authoritative main/work-start: `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`;
- PR #25: merged;
- RCA-1 exact-final-head: `38896b2a37180633870282e9d9e305d9c9fbbf8a`;
- merge tree: identical to exact-final-head tree;
- P1: `RECONCILED_WITH_EXPECTED_GAPS`;
- P2: `RECONCILED_WITH_MIGRATION_GAPS`;
- identity: `SYNTHETIC_ONLY`;
- SQL `01..52`: protected;
- SQL `53+`: absent/unallocated;
- production activation: not authorized;
- current P1/P2 authority: unchanged;
- no runtime wiring or actual identity mapping.

## Official phase

```text
RCA-1B Recommendation Data Non-production Read-only Reconciliation
CLASSIFICATION=JOINT_INTELLIGENCE_RELIABILITY_ADOPTION
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
POSTGRESQL_VERSION_MATRIX=15,18
RCA1B_DATASET_MODE=DETERMINISTIC_SYNTHETIC_DATABASE_FIXTURE
IDENTITY_MODE=SYNTHETIC_ONLY
```

RCA is a cross-track workstream. `RP` is reserved for Reliability Platform.

## Purpose

Read current authoritative P1/P2 query results and Data candidate projection results from isolated ephemeral PostgreSQL, compare them using RCA-1 taxonomy, and classify checkpoint, lineage, field, semantic and authority differences per lane.

This does not prove production/runtime/cutover readiness, live latency, actual identity governance, source replacement or authority transfer.

## Current authority

```text
P1_SOURCE=RecommendationP1ProfileSource
P1_RESULT=recommendation_p1_profile_snapshot
P2_SOURCE=RecommendationP2ObservationSource
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
P2_DATASET=recommendation-evaluation-dataset-v1
P2_METRICS=engagement_rate,fallback_rate
CURRENT_P1_P2_AUTHORITY_UNCHANGED
```

## Environment and versions

- Environment A CI ephemeral PostgreSQL: approved;
- Environment B shared non-production DB: deferred;
- Environment C production replica/derived: blocked;
- PostgreSQL 15 minimum; 15 and 18 required;
- UTC, `C` collation/ctype, no extension/PostGIS;
- one database per job and teardown by container destruction.

## Read-only execution contract

```text
TRANSACTION_READ_ONLY=REQUIRED
TRANSACTION_ISOLATION=REPEATABLE_READ
AUTOCOMMIT=DISABLED
STATEMENT_TIMEOUT_MS=5000
LOCK_TIMEOUT_MS=1000
IDLE_IN_TRANSACTION_TIMEOUT_MS=5000
MAX_RESULT_ROWS_PER_QUERY=1000
MAX_RECONCILIATION_CASES=10000
MAX_EXECUTION_DURATION_SECONDS=900
PARALLEL_QUERY=DISABLED
RETRY_POLICY=NONE
```

An ephemeral `rca1b_readonly` login is required. Owner/superuser, write privilege, RLS bypass, DDL/DML, temp objects, migrations and unbounded queries are forbidden.

## Query and dataset boundary

Only registered/fingerprinted prepared queries for P1/P2 source/candidate, checkpoint, lineage and bounded counts are allowed. Canonical SQL `01..52` is replayed and a deterministic noncanonical test-only seed is applied before read-only execution. Raw SQL construction, production data, canonical dataset rows and release evidence are prohibited.

## P1 decision

P1 keeps RCA-1 expected gaps and adds DB query/checkpoint/snapshot/order/null/numeric/timezone/duplicate/row-count dimensions. Comparable dimensions use zero tolerance. Checkpoint/snapshot equality and lineage match are mandatory. Aggregate-to-event fabrication remains prohibited.

## P2 decision

P2 protects `recommendation_p2_experiment_exposure`, exact assignment/version/binding, inclusive-lower/exclusive-upper 604800-second window, click/like/save/share and bound-run fallback. Duplicate observations and unsupported sources/events fail. Stale assignment and persisted dedupe remain migration-required. Dataset hash and release evidence remain outside query scope.

Required marker:

```text
P2_NON_PRODUCTION_RECONCILIATION_ONLY
CURRENT_P2_AUTHORITY_UNCHANGED
NO_AUTHORITY_TRANSFER
```

## Checkpoint, identity and evidence

Exact parity uses zero-lag explicit fixture checkpoints and matching lineage. Identity is synthetic-only and every invalid state fails closed. Evidence stores hashes, normalized values, query fingerprints, checkpoints, row counts and DB version; never raw rows, IDs, credentials, endpoints or connection strings. CI retention is 90 days; DB/raw result retention is execution lifetime/none.

## DB and SQL impact

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=YES_EPHEMERAL_TEST_ONLY
NEW_GRANT_REQUIRED=YES_EPHEMERAL_TEST_ONLY
TEST_FIXTURE_SQL_REQUIRED=YES_NONCANONICAL_TEST_ONLY
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
```

No SQL is created by SC-4 and SQL `53+` remains unallocated.

## Prerequisites

| Role | Status |
|---|---|
| Intelligence | `BLOCKING_APPROVAL` for P1 query/result/exit |
| Reliability | `BLOCKING_APPROVAL` for P2 query/migration/evidence |
| Data | `REQUIRED` for candidate/checkpoint/lineage/seed |
| Operations | `BLOCKING_APPROVAL` for DB/credential/network/role/resource/retention |
| Privacy/Security | `BLOCKING_APPROVAL` for identity/redaction/secret/retention |
| SC | `BLOCKING_APPROVAL` for entry/exit/registry/SQL/authority |

## Verification truth

SC-4 verifies governance, baseline, merge tree, RCA-1 contract/fixture inventory, decision uniqueness, finite limits, query/role/dimension policies, SQL/source/config protection and governance-only diff.

SC-4 does not execute PostgreSQL reconciliation, role permission tests, actual queries, runtime dark read, canary, load, replay, production validation or actual identity mapping; these are not PASS.

## Documents

- [SC-4 master](sc-next-track/SC-4-RCA-1B-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md)
- [execution environment](sc-next-track/23-SC-RCA1B-EXECUTION-ENVIRONMENT-DECISION.md)
- [PostgreSQL versions](sc-next-track/24-SC-RCA1B-POSTGRESQL-VERSION-AND-COMPATIBILITY-DECISION.md)
- [read-only contract](sc-next-track/25-SC-RCA1B-DB-READ-ONLY-EXECUTION-CONTRACT.md)
- [role/grant and SQL](sc-next-track/26-SC-RCA1B-ROLE-GRANT-AND-SQL-ALLOCATION-DECISION.md)
- [query boundary](sc-next-track/27-SC-RCA1B-ALLOWED-QUERY-BOUNDARY.md)
- [dataset](sc-next-track/28-SC-RCA1B-REPRODUCIBLE-DATASET-DECISION.md)
- [identity/privacy](sc-next-track/29-SC-RCA1B-IDENTITY-PRIVACY-DECISION.md)
- [P1 DB](sc-next-track/30-SC-RCA1B-P1-DB-RECONCILIATION-AUTHORITY-AND-EQUIVALENCE.md)
- [P2 DB](sc-next-track/31-SC-RCA1B-P2-DB-RECONCILIATION-AUTHORITY-AND-EQUIVALENCE.md)
- [checkpoint/lineage](sc-next-track/32-SC-RCA1B-CHECKPOINT-FRESHNESS-LINEAGE-DECISION.md)
- [evidence policy](sc-next-track/33-SC-RCA1B-EVIDENCE-REDACTION-RETENTION-POLICY.md)
- [prerequisite matrix](sc-next-track/34-SC-RCA1B-OPERATIONS-RELIABILITY-PREREQUISITE-MATRIX.md)
- [verification plan](sc-next-track/35-SC-RCA1B-VERIFICATION-PLAN.md)
- [exit/RCA-2 boundary](sc-next-track/36-SC-RCA1B-EXIT-CRITERIA-AND-RCA2-HANDOFF.md)
- [implementation prompt](sc-next-track/37-RCA-1B-IMPLEMENTATION-HANDOFF-PROMPT.md)

## Follow-up order

1. merge SC-4 only after explicit user approval;
2. implement RCA-1B in a separate Draft PR;
3. collect exact-head blocking approvals and execute PostgreSQL 15/18 read-only tests;
4. evaluate lane-separated RCA-1B exit;
5. propose RCA-2 separately if runtime evidence is still required.

## Current gate

```text
RCA1B_ENTRY_AUTHORIZED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

SC-4 is governance-only. Do not mark ready or merge without explicit user approval.
