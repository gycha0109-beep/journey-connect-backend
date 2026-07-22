# DP-5 Projection & Snapshot Foundation

## Status

`DP5_IMPLEMENTATION_COMPLETE`

## Purpose

DP-5 converts approved Data facts into deterministic, immutable shadow projections. It does not feed production Recommendation serving.

```text
canonical Data event
+ successful DP-4.5 mapped evidence
+ approved identity/P2 exposure binding
→ immutable source checkpoint
→ deterministic profile or experiment-outcome records
→ immutable validated snapshot
→ append-only lineage and validation evidence
```

## Implemented capability

### Recommendation profile input

`recommendation-profile-input-v1` produces shadow records for explicit 7, 30 and 90 day UTC windows. Each record contains deterministic event counts, stable region/content/tag rankings, positive/negative signals, source count, lineage fingerprint and record fingerprint.

Stable ranking order is:

```text
count DESC
last_occurred_at DESC
stable_reference ASC
```

### Experiment outcome input

`experiment-outcome-input-v1` requires the protected `recommendation_p2_experiment_exposure` authority. Assignment, run, user, session, variant and exposure time are verified against the existing P2 tables. Only click/like/save/share facts in `[exposed_at, exposed_at + 7 days)` and before `projection_as_of` are accepted. Fallback is read from the bound `recommendation_run.run_status`. Existing engagement and fallback metric denominators remain unchanged.

### Checkpoint

A checkpoint fixes event-time range, ingestion upper bound, exact source members, source count, source-set fingerprint and checkpoint-definition fingerprint. Checkpoints are append-only. A later event requires a new checkpoint and cannot mutate a prior snapshot.

Checkpoint persistence verifies caller-provided `occurredAt` and `ingestedAt` against the authoritative source row. Canonical events use event occurrence/receipt time, mapped adapter evidence uses mapped occurrence/evidence creation time, and P2 exposure uses exposure/evidence creation time. A caller cannot alter the source time boundary while retaining a valid source reference and fingerprint.

### Snapshot and idempotency

Logical identity:

```text
projection_name
+ projection_schema_version
+ projection_policy_version
+ source_checkpoint_ref
+ identity_binding_version
+ target_contract_version
```

- no existing identity: `NEW`;
- same identity and same content/lineage fingerprints: `DUPLICATE` and existing snapshot returned;
- same identity and different content: `CONFLICT`, existing snapshot preserved and `PROJECTION_SNAPSHOT_CONFLICT` appended.

Transaction-scoped advisory locks and unique constraints guarantee exactly one `NEW` under concurrent requests. Run, records, snapshot, lineage, validation and terminal status are inserted in one transaction.

## Deterministic boundary

Implemented fingerprint contracts:

- `data-source-set-sha256-v1`;
- `data-checkpoint-definition-sha256-v1`;
- `data-projection-record-sha256-v1`;
- `data-projection-snapshot-sha256-v1`;
- `data-projection-lineage-entry-sha256-v1`;
- `data-projection-lineage-sha256-v1`.

All use SHA-256 lowercase hexadecimal. Canonical object keys, source lists, record fingerprints and lineage fingerprints use stable lexical ordering. Build ID, execution time, row UUID, insertion order, locale, timezone and map iteration order do not affect semantic output.

## Identity boundary

Supported source namespaces remain distinct:

- `subject:<opaque-id>`;
- `user:<numeric-id>`.

A numeric user identity requires an explicit binding version, source, fingerprint and scope. Conflicting bindings for one source identity fail closed. No identity repository, inferred join or numeric-to-opaque conversion is implemented.

## Source and lineage validation

Each projection record requires lineage. Persistence verifies that every lineage entry is a member of the immutable checkpoint and that the referenced source exists with the same fingerprint:

- canonical event: `data_platform_event_v1`;
- mapped adapter evidence: `data_recommendation_adapter_output_v1` with `mapped_shadow` status;
- P2 exposure: `recommendation_p2_experiment_exposure`.

Persistence also verifies per-record lineage count and fingerprint, rejects orphan lineage, rejects source rows at or after `projection_as_of`, validates profile window membership and validates P2 outcome event authority, type, deduplication, window and boolean aggregation. Raw payloads are not copied into lineage.

## Database boundary

SQL `38..42` implements:

- checkpoint, run, status, snapshot, lineage, validation and conflict evidence;
- profile projection records;
- P2 outcome projection records;
- atomic checkpoint and snapshot persistence;
- writer/reader/function-owner separation;
- aggregate-only safe reader view;
- PostgreSQL 15/18 rollback-only validation.

SQL `01..37` remains unchanged. SQL `43+` remains unallocated.

## Access control

- `jc_data_projection_writer`: execute approved persistence functions only;
- `jc_data_projection_reader`: select aggregate safe view only;
- `jc_data_projection_function_owner`: hardened `NOLOGIN` function owner with fixed `search_path` and minimum grants;
- PUBLIC: no function execution or safe-view access.

All projection evidence tables are insert-only through both trigger and privilege controls.

## Retention

Checkpoint, run/status, projection records, snapshot, lineage, validation and conflict evidence carry `projection_evidence_90d`, `data-retention-policy-v1` and `expires_at`. No purge function, deletion scheduler or physical-delete workflow is introduced.

## Independent review corrections

The implementation was independently re-reviewed after the first successful candidate. The following defects were corrected and covered by regression tests:

- invalid source time ordering was previously accepted by the pure Java source contract;
- checkpoint ingestion upper bounds could precede the event range;
- conflicting identity bindings could escape as an unclassified exception;
- experiment outcomes after `projection_as_of` could enter the Java result;
- PostgreSQL checkpoint persistence trusted caller-supplied source timestamps without comparing them to authority rows;
- PostgreSQL snapshot persistence did not fully reconcile per-record lineage, profile windows and P2 outcome booleans against checkpoint sources;
- the SC protected regression gate still expected the pre-merge allocation wording.

All corrections remain within SQL `38..42`, pure Java Data contracts/tests and verification/governance files. Production Java/Kotlin, Recommendation source, Search source, Intelligence source and SQL `01..37` remain unchanged.

## Explicit non-responsibility

DP-5 does not implement a worker, scheduler, replay, backfill, identity repository, production consumer, Recommendation write, P2 exposure creation/update, Search projection, dashboard, alerting, purge, production shadow activation or cutover.

## Verification

Independently reviewed implementation code HEAD: `1dad0d84ffcfacfc56a880e1296ef9430c2d43ed`.

- Data PostgreSQL CI `29931366103`: PostgreSQL 15/18, SQL `01..42`, DP-2/DP-3/DP-4.5 concurrency regression and DP-5 exact-one-`NEW` concurrency PASS;
- Data Contract CI `29931366173`: Java 21, `-Xlint:all -Werror`, DP-5 golden/determinism/failure/boundary fixtures and Recommendation Java Core PASS;
- Recommendation P0 Database CI `29931367581`: Testcontainers PostgreSQL 15/18 PASS;
- Backend PR CI `29931366129`: Backend/IP-12.5 protected readiness PASS;
- SC Baseline Reconciliation `29931365762`: PASS.

The final documentation/evidence commit is verified separately by PR exact-head checks to avoid embedding a self-referential commit hash. These results authorize the DP-5 implementation verdict only. PR #16 remains unmerged, production activation remains prohibited and DP-6 must use the eventual PR #16 merge commit as its authoritative main baseline.
