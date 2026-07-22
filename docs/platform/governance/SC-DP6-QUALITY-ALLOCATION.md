# SC DP-6 Data Quality and Lineage Validation Allocation

## Status

- Decision: `PROPOSED / NON-AUTHORITATIVE UNTIL MERGED`
- Implementation authority: `BLOCKED UNTIL THIS ALLOCATION IS MERGED`
- Proposed allocation PR: `#17`
- Authoritative proposal base: `05a25771cd99d87891504fc00890ab918b970acf`
- DP-5 prerequisite: `SATISFIED / PR #16 MERGED`
- Current DP-6 state: `DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

## System outcome

After this decision is merged, DP-6 may validate—without mutating—the integrity and quality of DP-5 checkpoints, projection records, snapshots and lineage against canonical Data events, approved DP-4.5 adapter evidence, explicit identity binding evidence and protected P2 exposure authority.

Authorized direction after merge:

```text
canonical source + adapter evidence + checkpoint + projection + snapshot + lineage
→ deterministic validation checks
→ versioned quality metrics
→ append-only snapshot quality verdict
```

This allocation does not authorize source, projection, snapshot or lineage mutation; production Recommendation input/write; worker; scheduler; replay; backfill; automatic rebuild; purge; Search projection; production shadow activation; cutover or traffic.

## SQL allocation proposed

### SQL 43

`43_data_quality_validation_foundation.sql`

Responsibility:

- validation run and append-only status evidence;
- validation check results;
- anomaly evidence;
- stable scope/status/severity/failure constraints;
- retention metadata and append-only controls.

### SQL 44

`44_data_quality_metrics_and_verdict.sql`

Responsibility:

- versioned quality metrics and threshold results;
- append-only snapshot quality verdicts;
- late-arrival observations;
- `VALIDATED / REJECTED / INCONCLUSIVE` only;
- no production-readiness state.

### SQL 45

`45_data_quality_persistence_and_roles.sql`

Responsibility:

- atomic `NEW / DUPLICATE / CONFLICT` persistence;
- validation logical-identity locking and uniqueness;
- `QUALITY_VERDICT_CONFLICT` evidence;
- writer/reader/function-owner role hardening;
- fixed `search_path`, PUBLIC revoke and minimum grants.

### SQL 46

`46_data_quality_rebuild_and_safe_views.sql`

Responsibility:

- deterministic rebuild-comparison evidence;
- source/checkpoint/projection/snapshot/lineage reconciliation helpers;
- aggregate-only quality observability views;
- prohibited identity and raw-lineage dimensions excluded.

### SQL 47

`47_data_quality_validation.sql`

Responsibility:

- PostgreSQL 15 and 18 rollback-only validation;
- source, projection, snapshot, lineage, identity, exposure and rebuild checks;
- threshold/verdict/late-arrival checks;
- concurrency, append-only, role/grant, retention and protected-regression checks.

## Role allocation proposed

- `jc_data_quality_writer`: execute approved validation/verdict persistence functions only;
- `jc_data_quality_reader`: aggregate safe-view `SELECT` only;
- `jc_data_quality_function_owner`: `NOLOGIN`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`, `NOBYPASSRLS`; fixed-search-path function owner.

No role receives direct canonical event, adapter evidence, projection, snapshot, lineage, Recommendation or P2 exposure writes.

## Quality policy allocation proposed

Policy: `data-quality-policy-v1`

Required validation scopes:

- `SOURCE_COMPLETENESS`;
- `PROJECTION_COMPLETENESS`;
- `SNAPSHOT_CONSISTENCY`;
- `LINEAGE_INTEGRITY`;
- `IDENTITY_INTEGRITY`;
- `EXPOSURE_INTEGRITY` for experiment-outcome snapshots;
- `DETERMINISTIC_REBUILD`;
- `FULL` as the required composite verdict scope.

Required thresholds:

- source completeness: `100%`;
- projection coverage: `100%` for policy-eligible sources;
- lineage completeness: `100%`;
- orphan lineage: `0%`;
- snapshot record/subject/source reconciliation: `100%`;
- fingerprint match: `100%`;
- identity binding validity: `100%` where identity binding is required;
- P2 exposure binding validity: `100%` for experiment-outcome snapshots;
- rebuild match: `100%`;
- conflicted or rejected adapter evidence included: `0`;
- zero denominators: explicit `NOT_APPLICABLE`, `UNDEFINED` or `POLICY_DEFINED_ZERO_CASE`; never implicit `100%`.

Verdict rule:

- any `BLOCKER` → `REJECTED`;
- required check skipped or evidence insufficient → `INCONCLUSIVE`;
- all required checks pass and thresholds are met → `VALIDATED`.

DP-6 `VALIDATED` means data-quality validation only. It is not production, serving, release or cutover approval.

## Fingerprint allocation proposed

New semantic domains:

- `data-quality-validation-input-sha256-v1`;
- `data-quality-check-evidence-sha256-v1`;
- `data-quality-metric-sha256-v1`;
- `data-quality-verdict-sha256-v1`;
- `data-quality-rebuild-comparison-sha256-v1`.

All produce lowercase 64-character SHA-256 hexadecimal from canonical, stably ordered semantic content. Execution time, DB row ID, random UUID, worker/build ID, locale, timezone, insertion order and physical row order are excluded.

## Persistence identity proposed

```text
snapshot_ref
+ validation_scope
+ validator_version
+ quality_policy_version
```

- new identity → `NEW`;
- same identity and same verdict fingerprint → `DUPLICATE`;
- same identity and different verdict fingerprint → `CONFLICT / QUALITY_VERDICT_CONFLICT`.

## Retention proposed

Validation run/status, check result, metric, anomaly, verdict, late-arrival observation, rebuild comparison and conflict evidence use:

- retention class: `data_quality_evidence_90d`;
- policy version: `data-retention-policy-v1`;
- technical baseline: `90 days`;
- automatic purge and physical deletion: disabled.

## Protected boundary

- SQL `01..42` remains protected and unchanged;
- SQL `48+` remains unallocated;
- no SQL `43..47` may be created before this allocation is merged;
- DP-2 canonical events, DP-4.5 evidence and DP-5 checkpoint/projection/snapshot/lineage remain immutable;
- Recommendation P0/P1/P2 authority and metric denominators remain unchanged;
- P2 exposure authority remains `recommendation_p2_experiment_exposure`;
- identity namespaces remain distinct and automatic join remains prohibited;
- production worker/scheduler/replay/backfill/rebuild/purge/shadow/cutover remain unauthorized;
- implementation and main merge require separate user approval.