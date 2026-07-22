# DP-6 Handoff

## Status

`DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

## Baseline

- authoritative main: `05a25771cd99d87891504fc00890ab918b970acf`;
- DP-5 implementation PR #16: merged;
- DP-5 merge commit: `05a25771cd99d87891504fc00890ab918b970acf`;
- SQL `01..42`: protected and present exactly once;
- SQL `43+`: unallocated and absent;
- Data quality roles: unallocated and absent.

## Completed in this decision stage

- repository and DP-5 baseline reconciliation;
- quality-validation scope and invariant design;
- quality matrix with severity, metric, threshold and verdict impact;
- stable failure taxonomy;
- deterministic fingerprint domains;
- quality policy `data-quality-policy-v1` proposal;
- SQL `43..47` proposal;
- quality writer/reader/function-owner role proposal;
- append-only evidence and retention design;
- protected-diff verifier and machine-readable allocation evidence;
- SC decision PR.

## Not implemented

Because SC allocation is not yet authoritative, this PR intentionally does not add:

- SQL `43..47`;
- Java quality contracts or validators;
- database roles, functions, tables or views;
- PostgreSQL DP-6 runtime fixtures;
- quality persistence, concurrency or verdict execution.

No unexecuted runtime validation is reported as PASS.

## Proposed implementation after allocation merge

1. Pure Java quality contracts in `jc-data-contracts`.
2. Source, projection, snapshot, lineage, identity and exposure validators.
3. Deterministic rebuild validator and golden fixtures.
4. SQL `43..47` append-only persistence, metrics, verdicts, late-arrival evidence, roles and safe views.
5. PostgreSQL 15/18 NEW/DUPLICATE/CONFLICT, concurrency and permissions validation.
6. DP-2 through DP-5, Recommendation, Backend/IP-12.5 and SC protected regression.

## Protected state

- canonical Data events unchanged;
- DP-4.5 adapter evidence unchanged;
- DP-5 checkpoints, projection records, snapshots and lineage unchanged;
- Recommendation P0/P1/P2 authority unchanged;
- P2 exposure authority remains `recommendation_p2_experiment_exposure`;
- engagement and fallback metric denominators unchanged;
- identity namespaces remain separate;
- production Recommendation write remains disabled;
- worker remains unimplemented;
- scheduler remains disabled;
- replay/backfill/automatic rebuild remain unauthorized;
- production shadow remains disabled;
- kill switch remains enabled;
- sampling remains `0 BPS`;
- cohort remains empty;
- Search cutover remains not started.

## Required decision

Merge of the SC allocation PR is required before DP-6 implementation starts. The allocation merge commit becomes the authoritative DP-6 implementation base.

## DP-7 entry

DP-7 is not available. It requires:

1. this SC allocation merged;
2. a separate DP-6 implementation PR;
3. DP-6 Java and PostgreSQL 15/18 validation complete;
4. exact-head protected regressions complete;
5. DP-6 implementation merged to `main`.

Current outcome: `DP6_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`.