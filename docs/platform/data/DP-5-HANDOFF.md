# DP-5 Handoff

## Status

`IMPLEMENTED / EXACT-HEAD CI PENDING`

## Baseline

- authoritative implementation base: `67a9b7515dbfd41360160c8059ac387e74cbdf6b`;
- allocation PR #15: merged;
- implementation PR: #16;
- SQL `01..37`: protected;
- SQL `38..42`: implemented;
- SQL `43+`: unallocated.

## Completed

- pure Java projection contracts and deterministic engines;
- profile 7/30/90 day projection;
- P2 exposure-bound outcome projection;
- immutable source checkpoint;
- append-only run/status/snapshot/lineage/validation/conflict evidence;
- deterministic source, record, snapshot and lineage fingerprints;
- atomic `NEW / DUPLICATE / CONFLICT` persistence;
- exact-one-NEW concurrency verifier;
- hardened writer/reader/function-owner roles;
- aggregate-only safe view;
- 90-day retention metadata without purge;
- PostgreSQL 15/18 validation and protected regression wiring;
- machine-readable evidence.

## Protected state

- Recommendation P0/P1/P2 authority unchanged;
- P2 exposure authority remains `recommendation_p2_experiment_exposure`;
- engagement/fallback denominators unchanged;
- canonical events and DP-4.5 evidence append-only;
- production Recommendation write absent;
- worker absent; scheduler disabled;
- replay/backfill unauthorized;
- production shadow disabled;
- kill switch enabled;
- sampling `0 BPS`;
- cohort empty;
- Search cutover not started.

## Remaining gate

Exact-head GitHub Actions must pass before the verdict changes to `DP5_IMPLEMENTATION_COMPLETE`. Do not merge PR #16 without explicit user approval.

## DP-6 entry

DP-6 remains blocked until PR #16 exact-head PostgreSQL 15/18, Data Contract, Recommendation, Backend and SC gates pass and DP-5 is merged into main.
