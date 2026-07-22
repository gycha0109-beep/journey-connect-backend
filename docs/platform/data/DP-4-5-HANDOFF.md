# DP-4.5 Handoff

## Result

`DP45_IMPLEMENTATION_BLOCKED_BY_SQL_ASSIGNMENT`

## Baseline

- authoritative main: `c1649c3647e8b640bd95853fdcb645d1571f54c3`;
- DP-4 PR `#11`: merged;
- DP-4 implementation HEAD: `5d9ce9693f5492d08b5e0c545e1f2e18427fcf5f`;
- DP-4 merge commit: `c1649c3647e8b640bd95853fdcb645d1571f54c3`;
- SQL `01..34`: protected;
- SQL `35+`: unallocated;
- adapter evidence roles: unassigned.

## Completed in this stage

- confirmed the actual SQL and role blocker from the SC Decision Register, Platform Registry and SC Handoff;
- preserved the authoritative `Recommendation P0 source -> Data shadow candidate` direction;
- documented the required run, mapped-output, failure, duplicate/conflict and aggregate-view objects;
- fixed the proposed atomic `NEW/DUPLICATE/CONFLICT` behavior;
- documented output-fingerprint, privacy, append-only, role/grant and retention constraints;
- produced machine-readable blocker and protected-diff evidence;
- added no SQL, Java contract, fake repository, worker, scheduler, runtime connection or production wiring.

## Verification

Candidate-head protected regression completed successfully:

- Data Contract CI: PASS, run `29891906984`;
- DP-1 through DP-4 contract regression: PASS;
- DP-4.5 blocker verifier: PASS;
- Recommendation PostgreSQL 15/18: PASS, run `29891907012`;
- Backend/IP-12.5: PASS, run `29891906962`;
- SC Baseline Reconciliation: PASS, run `29891907017`;
- SQL `01..34`: unchanged;
- SQL `35+`: absent;
- Java, Recommendation, Search/Intelligence and production configuration: unchanged.

Final exact-head CI must remain successful before merge.

## Not implemented

- adapter evidence tables;
- persistence function;
- writer/reader roles or grants;
- safe view;
- PostgreSQL fixtures or smoke SQL;
- runtime consumer/worker/scheduler;
- replay, backfill or purge;
- Recommendation or canonical Data event write.

These items require explicit SC SQL and role allocation.

## Protected state

- Recommendation P0 remains authoritative;
- DP-4 output remains shadow-only compatibility evidence;
- existing P0 fingerprint unchanged and not reused;
- P1/P2 authority and metrics unchanged;
- general Recommendation exposure remains separate from P2 experiment exposure;
- production Recommendation write disabled;
- production worker not implemented;
- production scheduler disabled;
- replay not authorized;
- production shadow disabled;
- kill switch enabled;
- sampling `0 BPS`;
- cohort empty;
- Search cutover not started;
- production traffic not approved.

## Required SC gate

Before implementation resumes, SC must merge a decision that:

1. assigns an exact SQL range after `34`;
2. registers exact migration responsibilities;
3. assigns or rejects adapter evidence writer/reader roles;
4. confirms the function owner and grant model;
5. approves object names, retention class and evidence ID formats;
6. decides whether DP-4.5 persistence blocks DP-5 entry.

## Resume point

After the SC decision merge, re-read current `main`, use only the assigned SQL range, implement PostgreSQL 15/18 persistence and role tests, then run Data, Recommendation, Backend and SC protected regression. Do not reuse this blocker branch as implementation authority without rebasing onto the then-current `main`.
