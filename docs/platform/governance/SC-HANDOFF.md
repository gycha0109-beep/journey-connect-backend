# System Coordination Handoff

## Status

`DP6_IMPLEMENTATION_CANDIDATE / MAIN_MERGE_PENDING`

## Authoritative baseline

- DP-5 merge commit: `05a25771cd99d87891504fc00890ab918b970acf`;
- DP-6 allocation PR #17 merge commit: `c0f6b5dc8cc7089412a100989109b61315c062d0`;
- DP-6 implementation PR: `#18`;
- SQL `01..42`: protected;
- SQL `43..47`: allocated to DP-6 and implemented on PR #18;
- SQL `48+`: unallocated.

## DP-6 candidate capability

DP-6 adds validation-only reconciliation from canonical Data sources, DP-4.5 evidence and DP-5 immutable projection evidence to deterministic checks, metrics, rebuild comparisons and append-only `VALIDATED / REJECTED / INCONCLUSIVE` quality verdicts.

It does not mutate source/projection evidence or authorize production Recommendation input/write, worker, scheduler, replay, backfill, automatic rebuild, purge, Search projection, shadow activation, cutover or traffic.

## Approved contracts

- policy: `data-quality-policy-v1`;
- roles: `jc_data_quality_writer`, `jc_data_quality_reader`, `jc_data_quality_function_owner`;
- SQL: `43_data_quality_validation_foundation.sql` through `47_data_quality_validation.sql`;
- P2 exposure authority: `recommendation_p2_experiment_exposure`;
- identity boundary: `subject:<opaque-id> != user:<numeric-id>`;
- retention: `data_quality_evidence_90d`, automatic purge disabled.

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

## Integration gate

PR #18 must pass exact-head PostgreSQL 15/18, Java 21, Recommendation, Backend/IP-12.5, SC reconciliation and protected-diff gates. It must not be merged without explicit user approval. DP-7 starts only from the eventual DP-6 implementation merge commit.
