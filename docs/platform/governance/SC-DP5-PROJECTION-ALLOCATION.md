# SC DP-5 Projection and Snapshot Allocation

## Status

- Decision: `APPROVED`
- Implementation authority: `GRANTED AFTER MERGE INTO MAIN`
- Decision PR state: this document is non-authoritative until merged
- Baseline main: `de4e9f308130e10948edb69ceb1b2bba0eebcd2e`
- DP-4.5 prerequisite: `SATISFIED`
- Current DP-5 implementation state: `BLOCKED UNTIL THIS ALLOCATION IS MERGED`

## System outcome

DP-5 may build shadow-only, deterministic projections and immutable snapshots from the Data canonical event store and approved DP-4.5 adapter evidence.

Authorized direction:

```text
canonical Data events
+ approved DP-4.5 mapped shadow evidence
+ approved read-only P2 exposure facts
→ deterministic projection
→ immutable shadow snapshot
→ append-only lineage and validation evidence
```

This decision does not authorize production Recommendation input replacement, production Recommendation writes, a worker, scheduler, replay, backfill, Search projection, production shadow activation, cutover or traffic.

## SQL allocation

### SQL 38

`38_data_projection_snapshot_foundation.sql`

Responsibility:

- projection run evidence;
- append-only run-status evidence;
- immutable source checkpoint;
- common snapshot manifest;
- common lineage;
- validation and conflict evidence;
- constraints, indexes, retention metadata and append-only controls.

### SQL 39

`39_data_recommendation_profile_projection.sql`

Responsibility:

- `recommendation-profile-input-v1` shadow projection records;
- deterministic 7-day, 30-day and 90-day activity-window representation;
- bounded interaction, region, content, tag, positive and negative signal facts;
- profile record fingerprint and source-lineage binding;
- no production P1 source replacement.

### SQL 40

`40_data_experiment_outcome_projection.sql`

Responsibility:

- `experiment-outcome-input-v1` shadow projection records;
- exact binding to `recommendation_p2_experiment_exposure`;
- 7-day post-exposure click/like/save/share outcome facts;
- fallback observation bound to the exposed Recommendation run;
- no metric denominator, dataset, release or P2 authority replacement.

### SQL 41

`41_data_projection_persistence_roles.sql`

Responsibility:

- atomic `NEW / DUPLICATE / CONFLICT` snapshot persistence;
- advisory transaction lock and unique logical identity;
- completed-run checkpoint requirement;
- lineage completeness and source-fingerprint validation;
- least-privilege writer/reader/function-owner roles;
- privacy-safe aggregate reader view;
- PUBLIC denial and fixed `search_path`.

### SQL 42

`42_data_projection_snapshot_validation.sql`

Responsibility:

- PostgreSQL 15 and 18 rollback-only validation;
- profile and experiment outcome `NEW / DUPLICATE / CONFLICT`;
- checkpoint immutability;
- append-only run/status/snapshot/record/lineage evidence;
- source mismatch, identity ambiguity and exposure ambiguity fail-closed behavior;
- role/grant, safe-view, retention and protected-authority validation;
- multi-session same-identity concurrency verification.

SQL `01..37` remains protected. SQL `43+` remains unallocated.

## Approved physical boundary

The following object names are approved for implementation within SQL `38..42`.

| Object | Purpose |
|---|---|
| `data_projection_run_v1` | immutable logical projection-run definition |
| `data_projection_run_status_evidence_v1` | append-only started/completed/failed/conflicted status evidence |
| `data_source_checkpoint_v1` | immutable source selector, event/ingestion boundary and source-set fingerprint |
| `data_projection_snapshot_v1` | immutable shadow snapshot manifest |
| `data_recommendation_profile_input_projection_v1` | profile input projection records |
| `data_experiment_outcome_input_projection_v1` | P2-compatible outcome projection records |
| `data_projection_lineage_v1` | record-to-source event and adapter evidence lineage |
| `data_projection_validation_evidence_v1` | append-only validation result evidence |
| `data_projection_conflict_observation_v1` | append-only fingerprint conflict evidence |
| `persist_data_projection_snapshot_v1(...)` | atomic snapshot persistence and idempotency boundary |
| `data_projection_safe_metrics_v1` | aggregate-only reader view |

Object names may receive version-suffix corrections only through a subsequent SC decision. Existing SQL files must not be repurposed.

## Roles and ownership

### Writer

`jc_data_projection_writer`

- `NOLOGIN`, non-superuser;
- execute the approved atomic persistence function only;
- no direct table insert/update/delete;
- no canonical event mutation;
- no Recommendation, P1, P2, Search or Operations table write;
- no replay, retry release, purge or DDL authority.

### Reader

`jc_data_projection_reader`

- `NOLOGIN`, non-superuser;
- select the approved aggregate safe view only;
- no direct projection record, identity, lineage, source-reference or payload access;
- no write authority.

### Function owner

`jc_data_projection_function_owner`

- `NOLOGIN`, non-superuser;
- `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`, `NOBYPASSRLS`;
- fixed `search_path = pg_catalog, public, pg_temp`;
- PUBLIC execute revoked;
- explicit minimum table/function privileges only;
- no membership in application, Recommendation, Search, retry, replay or admin roles.

## Projection contracts

Approved target contracts:

```text
recommendation-profile-input-v1
experiment-outcome-input-v1
```

Approved shadow snapshot contract:

```text
data-projection-snapshot-v1
```

Approved fingerprint contracts:

```text
data-source-set-sha256-v1
data-projection-record-sha256-v1
data-projection-snapshot-sha256-v1
data-projection-lineage-sha256-v1
```

All fingerprints use SHA-256, lowercase hexadecimal, 64 characters and contract-defined canonical bytes. Build ID, execution timestamp, row ID, worker ID, random UUID, insertion order, locale and timezone are excluded from semantic fingerprints.

## Logical identity and idempotency

Projection snapshot logical identity:

```text
projection_name
+ projection_schema_version
+ projection_policy_version
+ source_checkpoint_ref
+ identity_binding_version
+ target_contract_version
```

Outcomes:

- same logical identity and same snapshot content fingerprint: `DUPLICATE`, return existing snapshot reference and create no new snapshot;
- same logical identity and different snapshot content fingerprint: `CONFLICT`, preserve existing snapshot, create no validated replacement and append `PROJECTION_SNAPSHOT_CONFLICT`;
- new logical identity: `NEW`, create one run definition, terminal status evidence, snapshot, projection records and complete lineage atomically.

A completed snapshot requires an immutable checkpoint and complete lineage. Run state is not updated in place; state transitions use append-only status evidence.

## Source authority

Allowed source authorities:

- `data_platform_event_v1` canonical Data event store;
- approved successful DP-4.5 mapped shadow output evidence;
- `recommendation_p2_experiment_exposure` through an explicit read-only P2 exposure contract;
- bound `recommendation_run.run_status` for fallback observation only.

Prohibited:

- production Recommendation profile/snapshot tables as profile projection source;
- Search tables as Data projection source;
- general Recommendation exposure as P2 experiment exposure;
- behavior impression as P2 denominator;
- rejected, unsupported, quarantined, duplicate-only or conflicted adapter evidence as successful mapped facts;
- guessed cross-namespace identity joins.

## Identity and exposure boundary

Allowed identity namespaces remain distinct:

```text
subject:<opaque-id>
user:<numeric-id>
```

No automatic conversion or inferred join is authorized. An approved, explicit identity binding version is required where source and target namespaces differ. Missing or ambiguous bindings fail closed.

P2 outcome projection requires exact assignment/exposure/run/subject/session/variant binding to `recommendation_p2_experiment_exposure`. Exposure-free behavior cannot become a P2 outcome. Existing engagement and fallback metric denominators remain unchanged.

## Time and aggregation

- all time boundaries use UTC;
- `as_of`, source event-time range and ingestion-time upper bound are explicit inputs;
- event-time lower bound is inclusive and upper bound is exclusive unless a target contract explicitly states otherwise;
- future events are excluded;
- late events do not mutate an existing checkpoint or snapshot;
- late-event inclusion requires a new checkpoint and new snapshot;
- stable ordering uses count descending, last occurrence descending, stable reference ascending;
- source duplicates are deduplicated by approved canonical/event and adapter evidence identity;
- no physical row ordering or current-time dependency is permitted.

Profile windows: 7, 30 and 90 days.

P2 outcome window: exposure time inclusive through exposure time plus 7 days exclusive, preserving the existing P2 contract's click/like/save/share engagement meaning.

## Failure contract

Minimum stable failures:

```text
unsupported_projection_schema
unsupported_source_schema
source_checkpoint_invalid
source_event_missing
source_fingerprint_mismatch
adapter_evidence_missing
adapter_evidence_conflicted
identity_binding_required
identity_binding_invalid
identity_namespace_conflict
exposure_binding_missing
projection_invariant_failed
snapshot_fingerprint_conflict
privacy_policy_violation
unclassified_projection_failure
```

Source mismatch, unsupported schema, identity ambiguity, exposure ambiguity, lineage omission, privacy violation and fingerprint conflict fail closed. No guessed correction is authorized.

## Safe aggregate reader view

Allowed dimensions:

- projection name;
- projection schema version;
- projection policy version;
- target contract version;
- run terminal status;
- validation failure code;
- aggregate counts;
- checkpoint age;
- oldest unvalidated snapshot age;
- latest validated snapshot time.

Forbidden dimensions:

- user or subject identity;
- session/request/source-event IDs;
- raw source or projection payload;
- source or record fingerprint;
- identity binding;
- unrestricted error or stack trace;
- token or credential.

PUBLIC access is prohibited.

## Retention and deletion

Approved technical metadata baseline:

- run and run-status evidence: 90 days;
- source checkpoint: 90 days;
- snapshot manifest and projection records: 90 days;
- lineage, validation and conflict evidence: 90 days.

Each object records `retention_class`, `retention_policy_version` and `expires_at`. No automatic purge, physical-delete executor, retention scheduler or deletion workflow is authorized.

## Validation gate

Implementation may begin only after this decision is merged into `main`.

Before implementation is classified complete:

1. SQL `01..37` remain unchanged;
2. only SQL `38..42` are added;
3. Java 21 and `-Xlint:all -Werror` pass;
4. deterministic profile/outcome golden fixtures pass;
5. PostgreSQL 15 and 18 pass;
6. concurrent same identity produces exactly one `NEW`;
7. DP-2, DP-3 and DP-4.5 protected regressions pass;
8. Recommendation P0/P1/P2 authority and metrics remain unchanged;
9. Backend and SC protected checks pass;
10. no production source, worker, scheduler, replay, backfill, cutover or traffic activation is introduced.
