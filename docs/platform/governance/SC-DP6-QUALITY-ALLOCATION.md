# SC DP-6 Data Quality and Lineage Validation Allocation

## Status

- Decision: `APPROVED / MERGED`
- Implementation authority: `GRANTED`
- Allocation PR: `#17`
- Allocation HEAD: `5399d7a136a2a6961292ec0eacbe6c47b7b407e0`
- Allocation merge commit: `c0f6b5dc8cc7089412a100989109b61315c062d0`
- Authoritative implementation base: `c0f6b5dc8cc7089412a100989109b61315c062d0`
- Implementation PR: `#18`
- DP-5 prerequisite: `SATISFIED / MAIN INTEGRATED`
- Current DP-6 state: `IMPLEMENTATION CANDIDATE / MAIN MERGE PENDING`

## Authorized system outcome

DP-6 may validate, without mutating, whether DP-5 checkpoints, projection records, snapshots and lineage agree with canonical Data events, approved DP-4.5 adapter evidence, explicit identity binding evidence and protected P2 exposure authority.

```text
canonical source + adapter evidence + checkpoint + projection + snapshot + lineage
→ deterministic validation checks
→ versioned quality metrics
→ append-only snapshot quality verdict
```

This authority does not permit source/projection/snapshot/lineage mutation, production Recommendation input or write, worker, scheduler, replay, backfill, automatic rebuild, purge, Search projection, production shadow activation, cutover or traffic.

## SQL allocation

- SQL 43: `43_data_quality_validation_foundation.sql` — validation run/status, check result, anomaly evidence and append-only controls.
- SQL 44: `44_data_quality_metrics_and_verdict.sql` — versioned policy, metrics, snapshot verdict and late-arrival observation.
- SQL 45: `45_data_quality_persistence_and_roles.sql` — atomic `NEW / DUPLICATE / CONFLICT`, authoritative reconciliation, roles and hardened functions.
- SQL 46: `46_data_quality_rebuild_and_safe_views.sql` — rebuild comparison, conflict evidence and aggregate-only safe view.
- SQL 47: `47_data_quality_validation.sql` — PostgreSQL 15/18 rollback-only validation.

## Role allocation

- `jc_data_quality_writer`: execute approved quality persistence function only; no direct table or cross-track write.
- `jc_data_quality_reader`: select aggregate quality safe view only.
- `jc_data_quality_function_owner`: `NOLOGIN`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`, `NOBYPASSRLS`, fixed-search-path function owner.

## Quality policy

Authoritative policy: `data-quality-policy-v1`.

Required thresholds:

- source completeness, projection coverage, lineage completeness, snapshot record/subject/source reconciliation, fingerprint match, required identity validity, P2 exposure validity and deterministic rebuild match: `100%`;
- orphan lineage, duplicate lineage, conflicted/rejected adapter inclusion and invalid P2 exposure inclusion: `0`;
- zero denominators: explicit `NOT_APPLICABLE`, `UNDEFINED` or `POLICY_DEFINED_ZERO_CASE`; never implicit `100%`.

Verdict boundary:

- blocker or required failure → `REJECTED`;
- required skipped check or incomplete evidence → `INCONCLUSIVE`;
- all required checks and thresholds pass → `VALIDATED`.

`VALIDATED` is Data quality evidence only, not production, serving, experiment release or cutover approval.

## Fingerprint domains

- `data-quality-validation-input-sha256-v1`;
- `data-quality-check-evidence-sha256-v1`;
- `data-quality-metric-sha256-v1`;
- `data-quality-verdict-sha256-v1`;
- `data-quality-rebuild-comparison-sha256-v1`;
- `data-quality-late-arrival-observation-sha256-v1`.

All are deterministic SHA-256 lowercase hexadecimal over canonical semantic inputs. Execution time, DB row ID, UUID, worker/build ID, locale, timezone, insertion order and physical row order are excluded.

## Persistence identity

```text
snapshot_ref
+ validation_scope
+ validator_version
+ quality_policy_version
```

- no existing identity → `NEW`;
- same identity, validation-input fingerprint and verdict fingerprint → `DUPLICATE`;
- same identity with different input or verdict fingerprint → `CONFLICT / QUALITY_VERDICT_CONFLICT`.

## Retention

Validation run/status, check, metric, anomaly, verdict, late arrival, rebuild comparison and conflict evidence use `data_quality_evidence_90d`, `data-retention-policy-v1`, explicit `expires_at`, 90-day technical metadata and no automatic purge or physical delete.

## Protected boundary

- SQL `01..42` remains protected and unchanged;
- SQL `43..47` is allocated only to DP-6;
- SQL `48+` remains unallocated;
- DP-2 canonical events, DP-4.5 evidence and DP-5 checkpoint/projection/snapshot/lineage remain immutable;
- Recommendation P0/P1/P2 authority and engagement/fallback denominators remain unchanged;
- P2 exposure authority remains `recommendation_p2_experiment_exposure`;
- identity namespaces remain distinct; automatic join and identity repository remain prohibited;
- production worker/scheduler/replay/backfill/rebuild/purge/shadow/cutover remain unauthorized;
- main merge of implementation PR #18 requires explicit user approval.
