# System Coordination Handoff

## 상태

`DP5_IMPLEMENTATION_COMPLETE / MAIN_MERGE_PENDING`

## 기준

- authoritative implementation base: `67a9b7515dbfd41360160c8059ac387e74cbdf6b`
- DP-4.5 implementation PR: `#14`
- DP-4.5 implementation HEAD: `8880dd8a86703df0f988ff03b22e84bac92f674b`
- DP-4.5 merge commit: `de4e9f308130e10948edb69ceb1b2bba0eebcd2e`
- DP-4.5 result: `DP45_IMPLEMENTATION_COMPLETE`
- SQL `01..37`: protected
- SQL `38..42`: implemented on PR #16
- SQL `43+`: unallocated

## 완료

- DP-1 through DP-4.5 are integrated into `main`.
- canonical event, idempotency, retry/quarantine, P0 adapter and adapter shadow evidence are protected authority.
- DP-5 implements deterministic shadow projections, immutable checkpoints/snapshots and append-only lineage evidence without runtime cutover.
- DP-5 independent review strengthened source-time authority, identity, as-of, profile-window, lineage and P2 outcome boundaries.
- production worker, scheduler, replay, backfill and production shadow remain disabled or unauthorized.

## DP-5 implementation

### Scope

DP-5 implements deterministic, shadow-only `recommendation-profile-input-v1` and `experiment-outcome-input-v1` projections, immutable source checkpoints and snapshots, append-only lineage/validation/conflict evidence and aggregate-only observability.

It does not replace the current P1/P2 runtime source, change P2 metrics or exposure authority, write Recommendation/Search/Operations tables, activate a worker or scheduler, execute replay/backfill, purge data, cut over traffic or activate production shadow.

### SQL

- SQL `38`: run/checkpoint/snapshot/lineage/validation/conflict foundation
- SQL `39`: Recommendation profile input projection
- SQL `40`: experiment outcome input projection
- SQL `41`: atomic `NEW/DUPLICATE/CONFLICT`, source authority reconciliation, roles/grants and safe view
- SQL `42`: PostgreSQL 15/18 validation
- SQL `43+`: unallocated

### Roles

- `jc_data_projection_writer`
- `jc_data_projection_reader`
- `jc_data_projection_function_owner` (`NOLOGIN`)

### Source authority

- profile facts: Data canonical event and successful DP-4.5 mapped evidence
- P2 experiment exposure: `recommendation_p2_experiment_exposure`
- fallback: bound exposed `recommendation_run.run_status`
- identity namespaces remain distinct and require explicit binding
- caller-supplied checkpoint times must match authoritative source rows

### Retention

Projection run/status, checkpoint, snapshot, record, lineage, validation and conflict evidence use 90-day technical retention metadata. Automatic purge and physical deletion remain disabled.

### Verified implementation evidence

- independently reviewed implementation code HEAD: `1dad0d84ffcfacfc56a880e1296ef9430c2d43ed`;
- Data PostgreSQL CI `29931366103`: PASS on PostgreSQL 15/18;
- Data Contract CI `29931366173`: PASS;
- Recommendation P0 Database CI `29931367581`: PASS on PostgreSQL 15/18;
- Backend PR CI `29931366129`: PASS;
- SC Baseline Reconciliation `29931365762`: PASS.

The final documentation/evidence commit is covered by PR exact-head checks and is intentionally not embedded as a self-referential hash.

## DP-5 entry

```text
DP-4.5: MAIN INTEGRATED
DP-5 IMPLEMENTATION: COMPLETE
```

PR #16 implements only the authorized DP-5 boundary and remains unmerged. Main merge remains prohibited without explicit user approval. DP-6 starts only from the eventual PR #16 merge commit.

## Remaining unresolved/outside DP-5

- identity mapping physical repository and deletion workflow;
- production worker/scheduler deployment and activation;
- replay/backfill execution;
- production consumer and P1/P2 cutover;
- Operations dashboard/alert routing;
- legal/country-specific retention and erasure;
- SQL `43+` allocation.

## Protected state

```text
Production shadow: DISABLED
Kill switch: true
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
Production traffic: NOT APPROVED
Go/No-Go: NO_GO_FOR_TRAFFIC
```
