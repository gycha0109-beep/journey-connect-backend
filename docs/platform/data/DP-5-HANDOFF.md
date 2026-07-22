# DP-5 Handoff

## Result

`DP5_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

## Baseline

- authoritative main: `de4e9f308130e10948edb69ceb1b2bba0eebcd2e`;
- DP-4.5 PR `#14`: merged;
- DP-4.5 merge commit: `de4e9f308130e10948edb69ceb1b2bba0eebcd2e`;
- SQL `01..37`: protected;
- SQL `38+`: absent on main;
- DP-5 roles: unallocated on main.

## Completed

- verified the actual allocation blocker;
- fixed the profile and experiment outcome projection matrix;
- defined immutable checkpoint, append-only run-status, snapshot and lineage contracts;
- fixed deterministic source-set, record, snapshot and lineage fingerprint meanings;
- fixed identity and P2 exposure fail-closed boundaries;
- proposed SQL `38..42`;
- proposed writer, reader and NOLOGIN function-owner roles;
- documented aggregate safe-view and 90-day retention boundaries;
- created machine-readable blocker and design evidence;
- created an SC decision PR.

## Not implemented

- pure Java DP-5 contracts or projection engine;
- profile or outcome golden fixtures;
- SQL `38..42`;
- projection tables, functions, roles, grants or views;
- PostgreSQL 15/18 DP-5 validation;
- worker, scheduler, replay, backfill, purge, production Recommendation input or cutover.

## Protected state

- Recommendation P0/P1/P2 source and metric authority unchanged;
- P2 exposure authority remains `recommendation_p2_experiment_exposure`;
- general exposure and behavior impression are not P2 denominators;
- canonical Data event and DP-4.5 evidence unchanged;
- production Recommendation write absent;
- worker absent; scheduler disabled;
- production shadow disabled;
- kill switch enabled;
- sampling `0 BPS`;
- cohort empty;
- Search cutover not started;
- production traffic not approved.

## Allocation PR verification

- Data Contract CI run `29901862224`: PASS;
- SC Baseline Reconciliation run `29901862138`: PASS;
- Java Data contracts DP-1 through DP-4: PASS;
- Recommendation Java Core regression: PASS;
- DP-4 and DP-4.5 protected artifact diff: PASS;
- DP-5 allocation/static blocker gate: PASS;
- DP-5 Java implementation and PostgreSQL 15/18: not executed because allocation is not yet authoritative.

## Resume point

Merge the SC allocation PR only with explicit user approval. Then start a new DP-5 implementation branch from the latest `main`, implement only SQL `38..42`, and run the full Java, PostgreSQL 15/18, determinism, lineage, role/grant and protected regression gates.

Do not treat this branch as DP-5 implementation completion.
