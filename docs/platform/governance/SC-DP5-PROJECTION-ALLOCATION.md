# SC DP-5 Projection and Snapshot Allocation

## Status

- Decision: `APPROVED / MERGED`
- Implementation authority: `GRANTED`
- Allocation PR: `#15`
- Authoritative allocation main: `67a9b7515dbfd41360160c8059ac387e74cbdf6b`
- DP-4.5 prerequisite: `SATISFIED`
- Implementation PR: `#16`
- Independently reviewed implementation code HEAD: `1dad0d84ffcfacfc56a880e1296ef9430c2d43ed`
- Current DP-5 implementation state: `DP5_IMPLEMENTATION_COMPLETE / MAIN MERGE PENDING`

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
- authoritative source timestamp and fingerprint reconciliation;
- per-record lineage completeness and source-fingerprint validation;
- `projection_as_of`, profile window and P2 outcome boundary validation;
- writer/reader/function-owner role hardening;
- aggregate-only safe view.

### SQL 42

`42_data_projection_snapshot_validation.sql`

Responsibility:

- PostgreSQL 15 and 18 validation;
- checkpoint, snapshot, lineage, concurrency and role/grant checks;
- source timestamp tamper rejection;
- protected DP-2, DP-3, DP-4.5 and Recommendation regression.

## Role allocation

- `jc_data_projection_writer`: approved function execution only;
- `jc_data_projection_reader`: aggregate safe-view SELECT only;
- `jc_data_projection_function_owner`: `NOLOGIN`, non-superuser, fixed-search-path function owner.

## Protected boundary

- SQL `01..37` remains protected;
- SQL `43+` remains unallocated;
- Recommendation P0/P1/P2 source and authority remain unchanged;
- P2 exposure authority remains `recommendation_p2_experiment_exposure`;
- production worker/scheduler/replay/backfill/shadow/cutover remain unauthorized;
- main merge requires explicit user approval.
