# DP-6 Handoff

## Status

`IMPLEMENTATION CANDIDATE / EXACT-HEAD CI PENDING`

## Baseline

- authoritative main and allocation merge: `c0f6b5dc8cc7089412a100989109b61315c062d0`;
- allocation PR: `#17`, merged;
- implementation PR: `#18`;
- implementation branch: `codex/dp-6-data-quality-validation`;
- SQL `01..42`: protected;
- SQL `43..47`: implemented on PR #18;
- SQL `48+`: unallocated.

## Implemented

- pure Java quality contracts, deterministic fingerprints and full validation coordinator;
- source, projection, snapshot, lineage, identity, P2 exposure and rebuild validators;
- explicit zero-denominator metrics and fail-closed verdict evaluation;
- append-only run/status/check/metric/anomaly/verdict/late/rebuild/conflict persistence;
- atomic `NEW / DUPLICATE / CONFLICT` with exact-one-NEW concurrency design;
- writer/reader/function-owner least privilege;
- privacy-safe aggregate view;
- PostgreSQL 15/18 rollback fixture and concurrency harness;
- protected Java, Recommendation, Backend and SC gates.

## Independent review corrections

- conflicting identity binding contracts now fail closed;
- source/check identities and fingerprint domains were deduplicated and strengthened;
- ratio metric bounds and Java/PostgreSQL decimal agreement were hardened;
- orphan versus missing lineage classifications were separated;
- out-of-checkpoint adapter evidence no longer creates a false failure;
- `VALIDATED` persistence now requires authoritative database reconciliation, required evidence, exact thresholds and fingerprint/count verification;
- safe-view latest validated time now uses snapshot as-of.

## Verification

Local Java 21, `-Xlint:all -Werror`, DP-5 contract/boundary and DP-6 fixtures pass. PostgreSQL 15/18, concurrency, roles/grants, Recommendation, Backend and SC exact-head CI are pending until the candidate is pushed. No pending check is reported as PASS.

## Protected state

Canonical Data events, DP-4.5 evidence, DP-5 checkpoint/projection/snapshot/lineage, Recommendation P0/P1/P2 source and P2 denominators remain unchanged. Production Recommendation writes, worker, scheduler, replay, backfill, automatic rebuild, purge, production shadow, traffic and Search cutover remain disabled or unauthorized.

## DP-7 entry

DP-7 remains blocked until PR #18 achieves exact-head Java/PostgreSQL/protected-regression success and is explicitly merged to `main`. The eventual PR #18 merge commit becomes the next Data Platform baseline.
