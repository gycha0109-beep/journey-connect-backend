# DP-4.5 Handoff

## Result

`DP45_IMPLEMENTATION_COMPLETE`

## Baseline

- authoritative work-start main: `9a5785448ff300063b7f320c9f1043ef1863741e`;
- blocker/design PR `#12`: merged as `1eb981fa2ab33e6b3870c6c1b76e547eeae48980`;
- SC allocation PR `#13`: merged as `9a5785448ff300063b7f320c9f1043ef1863741e`;
- implementation candidate HEAD before final evidence: `3279a5812716471568312efb10c2af156eeefbc0`;
- SQL `01..34`: protected and unchanged;
- SQL `35..37`: DP-4.5 allocated and implemented;
- SQL `38+`: unallocated.

## Delivered

- append-only adapter run, mapped-output, mapping-failure and conflict evidence;
- bounded duplicate aggregate counter;
- atomic `NEW / DUPLICATE / CONFLICT` persistence;
- existing run and evidence references returned for duplicates;
- `ADAPTER_EVIDENCE_CONFLICT` with no additional output on conflicts;
- DP-4 fingerprint-shape and exact version-boundary validation;
- mapped-payload size and privacy validation;
- writer execute-only and reader aggregate-view-only separation;
- dedicated NOLOGIN function owner with fixed `search_path` and PUBLIC revoke;
- privacy-safe aggregate metrics;
- 90-day retention metadata without purge or physical delete;
- PostgreSQL 15/18 rollback smoke and multi-session concurrency fixtures.

## Verified candidate

Candidate HEAD `3279a5812716471568312efb10c2af156eeefbc0` passed:

- Data PostgreSQL 15/18: run `29895786673`;
- same-identity concurrency: exactly one `NEW`, one `DUPLICATE`, one run and one output, run `29895786673`;
- DP-2 event-store/idempotency regression: PASS;
- DP-3 retry/quarantine regression: PASS;
- Data Contract and DP-4 adapter regression: run `29895786611`;
- Recommendation Java Core and PostgreSQL 15/18: run `29895786652`;
- Backend/IP-12.5: run `29895786618`;
- SC Baseline Reconciliation: run `29895786609`;
- protected diff: PASS.

The final evidence-only commit follows this candidate. Its exact-head CI must remain successful before merge.

## Self-review corrections

1. A static verifier initially required an explanatory phrase instead of executable append-only assertions. It was changed to inspect the actual UPDATE/DELETE denial tests.
2. The dedicated function owner initially lacked execution permission on existing Data validation helpers. Only the required helper functions were explicitly granted.
3. `SELECT ... FOR UPDATE` unnecessarily required table UPDATE privilege. The approved advisory transaction lock and unique constraint already serialize the logical identity, so the redundant row lock was removed rather than broadening privileges.
4. INSERT return expressions were qualified with row aliases to avoid PL/pgSQL output-column ambiguity.

## Protected state

- Recommendation P0 remains authoritative;
- DP-4 output remains Data shadow compatibility evidence;
- existing P0 and canonical Data fingerprints remain unchanged and are not reused;
- P1/P2 authority and metric meanings remain unchanged;
- general Recommendation exposure remains separate from P2 experiment exposure;
- production Recommendation write disabled;
- production worker not implemented;
- production scheduler disabled;
- replay and backfill not authorized;
- production shadow disabled;
- kill switch enabled;
- sampling `0 BPS`;
- cohort empty;
- Search cutover not started;
- production traffic not approved.

## Remaining risks

- no production consumer, worker or scheduler invokes the persistence function;
- application login-to-capability-role membership is outside this stage;
- legal/country-specific retention and erasure remain pending;
- automatic purge remains intentionally absent;
- DP-5 has not started.

## DP-5 entry

The DP-4.5 technical prerequisite is satisfied after this implementation PR is merged and its final exact-head PostgreSQL 15/18, Data, Recommendation, Backend and SC checks remain successful. DP-5 must start from the resulting `main` merge commit.
