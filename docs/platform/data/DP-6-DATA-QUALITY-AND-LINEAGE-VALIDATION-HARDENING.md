# DP-6 Data Quality & Lineage Validation Hardening

## Status

`DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

This document is implementation-ready design only. SQL `43+`, Data quality roles and physical persistence are not authoritative until the associated SC allocation PR is merged. No Java or SQL implementation is included in this allocation PR.

## Baseline

- authoritative main: `05a25771cd99d87891504fc00890ab918b970acf`;
- DP-5 PR #16: merged;
- SQL `01..42`: protected;
- SQL `43+`: unallocated on the authoritative base;
- production Recommendation write: disabled;
- worker: not implemented;
- scheduler: disabled;
- replay/backfill: unauthorized;
- production shadow: disabled;
- kill switch: enabled;
- effective sampling: `0 BPS`;
- cohort: empty;
- Search cutover: not started.

## Purpose

DP-6 validates whether DP-5 shadow projections and snapshots are complete, internally consistent, traceable to authoritative source evidence and reproducible from the same semantic inputs.

```text
canonical source
+ approved DP-4.5 adapter evidence
+ immutable DP-5 checkpoint
+ projection records
+ snapshot
+ lineage
+ explicit identity/P2 exposure evidence
→ deterministic quality validation
→ append-only checks, metrics, anomalies and verdict
```

DP-6 never repairs, updates or replaces the source, checkpoint, projection, snapshot or lineage under validation.

## Validation scopes

### Source completeness

Reconcile the immutable checkpoint with the authoritative source rows and the checkpoint member set:

- source count;
- source-set fingerprint;
- stable last-source reference;
- event-time interval `[event_time_from, event_time_to)`;
- ingestion upper bound;
- source stream, contract and schema version;
- authoritative source existence, fingerprint and timestamps;
- late-arriving source detection without checkpoint mutation.

### Projection completeness

Reconcile policy-eligible checkpoint sources with projection records and record lineage:

- expected records exist;
- unexpected records do not exist;
- record source counts equal distinct bound sources;
- 7/30/90-day profile windows are exact;
- experiment outcomes use `[exposed_at, exposed_at + 7 days)` and remain before `projection_as_of`;
- duplicate source/evidence is counted once according to DP-2/DP-4.5 identity;
- rejected, conflicted and unsupported adapter evidence is absent.

### Snapshot consistency

Recompute and compare:

- record count;
- distinct subject count;
- distinct source count;
- projection name/schema/policy/checkpoint binding;
- ordered projection-record fingerprint set;
- snapshot content fingerprint;
- snapshot lineage fingerprint.

### Lineage integrity

Require:

- every projection record has one or more lineage entries;
- no lineage references a missing projection record;
- every lineage source exists in the checkpoint and authoritative source table;
- source fingerprint, checkpoint, adapter evidence and mapping policy match;
- duplicate lineage identity is rejected;
- raw payload and unrestricted identifiers are never copied into quality evidence.

### Identity integrity

Preserve the namespace boundary:

```text
subject:<opaque-id> != user:<numeric-id>
```

Validation requires the DP-5 binding version, source, fingerprint and scope where numeric/opaque compatibility is used. It never infers identity, creates a mapping repository or falls back to another identity.

### Exposure integrity

`experiment-outcome-input-v1` requires exact protected authority from `recommendation_p2_experiment_exposure` and its bound assignment/run facts. Validation compares experiment, version, exposure, subject/user, session, variant and exposure time. General exposure and behavior impression cannot substitute for P2 exposure.

### Deterministic rebuild

A pure validator rebuilds the expected semantic result from explicit inputs only:

- checkpoint;
- projection as-of;
- schema/policy/feature versions;
- identity binding version/evidence;
- target contract version;
- canonical source set and approved adapter evidence;
- P2 exposure evidence where required.

It compares record counts, subject/source counts, ordered record fingerprints, snapshot fingerprint, lineage fingerprint and stable ordering. Execution time, build ID, UUID, locale, timezone, map iteration and input insertion order cannot affect the result.

## Pure Java contract planned after allocation

Default module: `jc-data-contracts` under `com.jc.data.contract.v1.quality`.

Planned immutable contracts:

- `DataQualityValidationDefinition`;
- `DataQualityValidationRun`;
- `DataQualityCheckResult`;
- `DataQualityMetric`;
- `DataQualityThreshold`;
- `DataQualityPolicy`;
- `DataQualityAnomaly`;
- `SnapshotQualityVerdict`;
- `RebuildComparison`;
- `LateArrivalObservation`;
- `DataQualityFailure`;
- `DataQualityPersistenceOutcome`.

Planned validators:

- `SourceCompletenessValidator`;
- `ProjectionCompletenessValidator`;
- `SnapshotConsistencyValidator`;
- `LineageIntegrityValidator`;
- `IdentityIntegrityValidator`;
- `ExposureIntegrityValidator`;
- `DeterministicRebuildValidator`.

The Java boundary remains pure: no Spring, JPA, JDBC, network, worker, scheduler, system clock, random ID, mutable global state, locale-sensitive or timezone-sensitive logic.

## Validation run contract

Logical fields:

- explicit validation run ID;
- validation scope;
- snapshot/checkpoint/projection bindings;
- validator version;
- quality policy version;
- explicit started/completed/created instants;
- retention metadata.

Run state is append-only evidence: `STARTED`, `COMPLETED`, `FAILED`, `CONFLICTED`. A `FULL` run requires an existing snapshot and actual execution of all required checks. Unexecuted checks cannot be recorded as `PASS`.

## Check result contract

Check status: `PASS`, `FAIL`, `SKIPPED`, `NOT_APPLICABLE`.

Severity: `INFO`, `WARNING`, `ERROR`, `BLOCKER`.

A skipped required check requires a stable reason code and makes the composite verdict `INCONCLUSIVE`. A blocker makes it `REJECTED`. Warnings remain warnings and are not silently promoted or ignored.

## Quality metric contract

Required metrics:

- source completeness rate;
- projection coverage rate;
- lineage completeness/orphan/duplicate rates;
- snapshot record/subject reconciliation rates;
- fingerprint match rate;
- identity and exposure binding validity rates;
- late-arrival and conflict rates;
- rebuild match rate.

Each metric stores numerator, denominator, value, unit, policy threshold/operator/result and metric version. A zero denominator is explicitly `NOT_APPLICABLE`, `UNDEFINED` or `POLICY_DEFINED_ZERO_CASE`; it is never implicitly converted to `100%`.

## Snapshot quality verdict

Append-only verdicts are limited to:

- `VALIDATED`;
- `REJECTED`;
- `INCONCLUSIVE`.

Forbidden values include `PRODUCTION_READY`, `SERVING_READY`, `CUTOVER_READY`, `ACTIVE` and `AUTHORITATIVE`.

The verdict records blocker/error/warning/check counts, quality score and deterministic verdict fingerprint. `VALIDATED` means only that the versioned Data quality policy passed.

## Late arrival

A late event has event time inside the checkpoint event range but authoritative ingestion after the checkpoint ingestion upper bound. DP-6 records append-only observation with affected checkpoint/snapshot, event and ingestion times, lateness and policy class:

- `WITHIN_TOLERANCE`;
- `REBUILD_RECOMMENDED`;
- `REBUILD_REQUIRED`;
- `IGNORED_BY_POLICY`.

DP-6 does not mutate the checkpoint/snapshot and does not trigger replay, backfill or rebuild.

## Stable failure taxonomy

Wire values follow existing lower snake-case convention:

- `unsupported_validation_scope`;
- `unsupported_validator_version`;
- `unsupported_quality_policy_version`;
- `source_count_mismatch`;
- `source_set_fingerprint_mismatch`;
- `source_range_mismatch`;
- `source_event_missing`;
- `source_event_out_of_range`;
- `source_schema_mismatch`;
- `projection_record_missing`;
- `projection_record_unexpected`;
- `projection_source_count_mismatch`;
- `projection_window_violation`;
- `projection_duplicate_aggregation`;
- `invalid_adapter_evidence_included`;
- `snapshot_record_count_mismatch`;
- `snapshot_subject_count_mismatch`;
- `snapshot_source_count_mismatch`;
- `snapshot_content_fingerprint_mismatch`;
- `snapshot_lineage_fingerprint_mismatch`;
- `snapshot_contract_mismatch`;
- `lineage_missing`;
- `lineage_orphan`;
- `lineage_duplicate`;
- `lineage_source_missing`;
- `lineage_source_fingerprint_mismatch`;
- `lineage_adapter_evidence_missing`;
- `lineage_checkpoint_mismatch`;
- `lineage_policy_version_mismatch`;
- `identity_binding_missing`;
- `identity_binding_invalid`;
- `identity_namespace_conflict`;
- `exposure_binding_missing`;
- `exposure_binding_invalid`;
- `exposure_subject_mismatch`;
- `exposure_variant_mismatch`;
- `exposure_time_mismatch`;
- `general_exposure_used_as_p2`;
- `rebuild_record_mismatch`;
- `rebuild_subject_count_mismatch`;
- `rebuild_projection_fingerprint_mismatch`;
- `rebuild_snapshot_fingerprint_mismatch`;
- `rebuild_lineage_fingerprint_mismatch`;
- `non_deterministic_output`;
- `quality_threshold_failed`;
- `quality_verdict_conflict`;
- `privacy_policy_violation`;
- `unclassified_quality_failure`.

## Fingerprint design

Proposed semantic domains:

- `data-quality-validation-input-sha256-v1`;
- `data-quality-check-evidence-sha256-v1`;
- `data-quality-metric-sha256-v1`;
- `data-quality-verdict-sha256-v1`;
- `data-quality-rebuild-comparison-sha256-v1`.

Existing DP-2/DP-4/DP-4.5/DP-5 fingerprints remain lineage inputs and are not redefined.

## Proposed persistence and access boundary

The SC proposal allocates SQL `43..47` and roles:

- `jc_data_quality_writer` — approved function execution only;
- `jc_data_quality_reader` — aggregate safe-view read only;
- `jc_data_quality_function_owner` — hardened `NOLOGIN` owner.

All validation evidence is insert-only. Atomic logical identity is snapshot + scope + validator + quality policy. Same verdict is `DUPLICATE`; a different verdict is `CONFLICT / QUALITY_VERDICT_CONFLICT`.

## Safe aggregate observability

The future reader view may expose aggregate validation counts, verdict/scope/failure/severity counts, average quality rates, policy-version counts, oldest inconclusive age and latest validated time. It must not expose user/subject/session/request/source/exposure IDs, raw payload, raw lineage, source fingerprints, identity binding contents, tokens, stack traces or unrestricted errors. PUBLIC access is prohibited.

## Retention

Proposed validation evidence uses `data_quality_evidence_90d`, `data-retention-policy-v1`, explicit `expires_at` and a 90-day technical baseline. Automatic purge, physical deletion and retention scheduler remain out of scope.

## Protected non-responsibility

This phase does not modify canonical events, adapter evidence, checkpoint, projection, snapshot or lineage. It does not implement Recommendation input/write/results, serving profile, feature store, consumer, worker, scheduler, replay, backfill, automatic rebuild, identity repository/join, P2 exposure mutation, metric changes, Search projection, Operations UI, alerts, purge, traffic, production shadow or cutover.

## Allocation gate

Implementation may begin only after the SC allocation PR is merged to `main`. Until then:

- SQL `01..42` remains unchanged;
- SQL `43+` remains absent;
- Data quality roles remain absent;
- no Java quality implementation is claimed;
- final verdict remains `DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`.