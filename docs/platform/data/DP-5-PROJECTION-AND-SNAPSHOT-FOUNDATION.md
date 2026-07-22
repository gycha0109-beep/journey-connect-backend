# DP-5 Projection and Snapshot Foundation

## Result

`DP5_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

## Preflight result

Authoritative repository state checked before design:

- `main`: `de4e9f308130e10948edb69ceb1b2bba0eebcd2e`;
- PR `#14`: merged;
- DP-4.5: `DP45_IMPLEMENTATION_COMPLETE`;
- SQL `01..37`: present and protected;
- SQL `38+`: absent and unallocated on authoritative `main`;
- DP-5 projection writer/reader/function-owner roles: absent and unallocated;
- production worker: not implemented;
- scheduler, replay, backfill and production shadow: disabled or unauthorized.

Because SQL and role authority is absent, this branch does not implement Java projection contracts, SQL, database objects, grants, fixtures, workers or runtime wiring. It supplies the implementation-ready contract and the SC allocation decision required to resume.

## Purpose

DP-5 will establish deterministic, shadow-only projections and immutable snapshots from approved Data sources.

```text
fixed source checkpoint
+ canonical Data events
+ approved DP-4.5 mapped evidence
+ exact P2 exposure facts where required
→ deterministic projection records
→ immutable snapshot
→ append-only lineage and validation evidence
```

The output is compatibility/evaluation evidence. It is not a production Recommendation input and cannot change recommendation results.

## Source boundary

Allowed sources:

- canonical Data event store from DP-2;
- successful mapped DP-4.5 output evidence;
- exact read-only P2 experiment exposure and bound run status for the experiment outcome projection.

Rejected sources:

- production P1 profile snapshots as the source of `recommendation-profile-input-v1`;
- Search tables;
- general exposure substituted for P2 exposure;
- unsupported, quarantined or conflicted adapter evidence;
- guessed identity joins.

## Projection definitions

### Recommendation profile input

Contract:

```text
recommendation-profile-input-v1
```

Projection record minimum meaning:

```text
projection_subject_ref
projection_as_of
source_checkpoint_ref
profile_schema_version
projection_policy_version
activity_windows
interaction_counts
recent_regions
recent_content_refs
recent_tag_refs
engagement_signals
negative_signals
source_event_count
source_lineage_fingerprint
projection_record_fingerprint
```

Rules:

- shadow-only compatibility input;
- explicit 7, 30 and 90 day UTC windows;
- duplicate source facts counted once;
- stable ranking: count descending, latest occurrence descending, reference ascending;
- bounded lists and counts;
- `subject:<opaque-id>` and `user:<numeric-id>` remain distinct;
- profile feature semantics remain Data facts, not Intelligence-owned scoring, decay, saturation, segment or ranking policy.

### Experiment outcome input

Contract:

```text
experiment-outcome-input-v1
```

Projection record minimum meaning:

```text
experiment_ref
variant_ref
exposure_ref
subject_ref
exposed_at
outcome_window
clicked
liked
saved
shared
fallback_observed
outcome_event_refs
source_checkpoint_ref
projection_record_fingerprint
```

Rules:

- exposure authority is `recommendation_p2_experiment_exposure`;
- assignment, exposure, run, subject, session and variant bindings must match;
- click/like/save/share are observed only within the existing seven-day exposure window;
- fallback uses the exposed `recommendation_run.run_status`;
- exposure-free behavior is not emitted;
- existing `engagement_rate` and `fallback_rate` denominators, dataset, evaluation and release evidence are unchanged.

## Source checkpoint contract

Minimum meaning:

```text
checkpoint_id
source_stream
source_contract_version
source_schema_version
event_time_from
event_time_to
ingested_at_upper_bound
last_source_event_ref
source_event_count
source_set_fingerprint
created_at
```

Checkpoint rules:

- immutable after creation;
- event time and ingestion time remain distinct;
- source definition canonicalization is versioned;
- same definition produces the same checkpoint fingerprint;
- late events require a new checkpoint;
- completed projection run without checkpoint is invalid.

## Run and status contract

Run definition minimum meaning:

```text
projection_run_id
projection_name
projection_schema_version
projection_policy_version
source_contract_version
source_checkpoint_ref
source_from
source_to
as_of
identity_binding_version
target_contract_version
producer_build_id
started_at
created_at
```

Run status is separate append-only evidence:

```text
projection_run_ref
status
observed_at
failure_code
validation_ref
created_at
```

Status values:

```text
started
completed
failed
conflicted
```

The run definition is never updated to overwrite state.

## Snapshot contract

Minimum meaning:

```text
snapshot_id
projection_run_ref
projection_name
projection_schema_version
projection_policy_version
source_checkpoint_ref
snapshot_as_of
record_count
subject_count
source_event_count
content_fingerprint
lineage_fingerprint
snapshot_status
created_at
retention_class
retention_policy_version
expires_at
```

Status values:

```text
created
validated
rejected
```

Serving, active, production-ready and cutover-ready semantics are prohibited.

## Lineage contract

Each projection record has at least one valid lineage relation.

```text
snapshot_ref
projection_record_ref
source_event_ref
source_fingerprint
adapter_evidence_ref
source_checkpoint_ref
projection_policy_version
mapping_policy_version
created_at
```

Rules:

- no projection record without lineage;
- every source reference must exist;
- source fingerprint must match its source authority;
- adapter evidence reference is required when the record depends on DP-4.5 mapping;
- no raw payload is copied into lineage;
- identity and source reference are not safe-view metric dimensions;
- bounded lineage summary is permitted only when exact traceability remains possible.

## Deterministic fingerprint contract

Fingerprint versions:

```text
data-source-set-sha256-v1
data-projection-record-sha256-v1
data-projection-snapshot-sha256-v1
data-projection-lineage-sha256-v1
```

All outputs are SHA-256 lowercase hexadecimal, 64 characters.

Semantic inclusion:

- source references and source fingerprints in stable order;
- source checkpoint fingerprint;
- projection name, schema and policy version;
- identity binding version;
- target contract version;
- canonical projection fields;
- canonical lineage relations.

Excluded:

- run/snapshot/row UUID;
- producer build ID;
- worker ID;
- database insertion order;
- execution or creation time not part of `as_of`;
- locale, timezone and process environment.

Same checkpoint, source set, version boundary and `as_of` must reproduce the same record, snapshot and lineage fingerprints.

## Idempotency

Logical identity:

```text
projection_name
+ projection_schema_version
+ projection_policy_version
+ source_checkpoint_ref
+ identity_binding_version
+ target_contract_version
```

Behavior:

| Disposition | Required behavior |
|---|---|
| `NEW` | persist one immutable snapshot and complete lineage |
| `DUPLICATE` | return the existing snapshot reference; create no snapshot |
| `CONFLICT` | preserve the existing snapshot; append conflict evidence; return `PROJECTION_SNAPSHOT_CONFLICT` |

Atomic persistence requires a transaction-scoped advisory lock plus a matching unique constraint.

## Failure and privacy boundary

Stable failure codes are fixed by the SC allocation document. Identity, exposure, source, fingerprint, schema, lineage and privacy ambiguity fail closed.

No unrestricted user text, raw query, exact GPS, token, credential, raw provider payload, canonical event bytes, full idempotency key, raw numeric identity copied into opaque identity, stack trace or unrestricted error text may be persisted.

## Proposed database boundary

Physical objects and SQL responsibilities are specified in:

- `../governance/SC-DP5-PROJECTION-ALLOCATION.md`.

No object in that document exists on this branch. All are `PROPOSED_NOT_IMPLEMENTED` until the allocation PR is merged and a new implementation branch starts from the then-current `main`.

## Verification performed in this blocked stage

Performed:

- current main and PR #14 merge state inspection;
- SQL `38+` allocation inspection;
- source authority and P1/P2 compatibility review;
- projection matrix consistency review;
- documentation and machine-readable evidence validation;
- protected-diff validation through CI.

Not performed:

- Java compilation for new DP-5 classes;
- profile/outcome golden fixtures;
- PostgreSQL 15/18 DP-5 fixtures;
- concurrency, role/grant or runtime persistence validation.

Unexecuted checks are not reported as PASS.

## Resume gate

After the SC allocation PR is merged:

1. re-read the latest `main`;
2. use only SQL `38..42`;
3. implement pure Java contracts and deterministic fixtures;
4. implement the approved PostgreSQL objects and roles;
5. run PostgreSQL 15/18 and protected regressions;
6. open a separate DP-5 implementation PR;
7. do not merge without explicit user approval.
