# DP-4 Handoff

## Result

`DP4_IMPLEMENTATION_COMPLETE_WITH_SQL_ASSIGNMENT_PENDING`

## Baseline

- work-start main: `233016060378cd368620cac274c64ca61ce812a7`
- DP-3 merge commit: `233016060378cd368620cac274c64ca61ce812a7`
- verified candidate HEAD: `74961fefe495438447f13543178ddca6e14718f9`

## Delivered

- protected Recommendation P0 source-to-Data shadow mapping contract;
- all 16 P0 event wires mapped to existing Data taxonomy candidates;
- explicit identity and general-exposure bindings;
- separate deterministic adapter output fingerprint;
- source fingerprint, P0 resolver and taxonomy compatibility regression;
- stable unsupported/quarantine classifications aligned to DP-3 reasons;
- golden/invalid fixtures, compatibility matrix and machine-readable evidence;
- no SQL, runtime consumer, scheduler, dual-write or production cutover.

## Verification

- Data Contract CI: PASS, run `29886994028`;
- Recommendation Java Core: PASS, run `29886994028`;
- Recommendation PostgreSQL 15/18: PASS, run `29886994045`;
- Backend/IP-12.5: PASS, run `29886994024`;
- SC Baseline Reconciliation: PASS, run `29886994036`;
- protected diff: PASS;
- SQL `01..34`: unchanged.

The PR is compacted after this evidence capture. Final exact-head CI must remain successful before merge.

## Blocked persistence item

SQL `35+` is unallocated. DP-4 does not insert into SQL `01..34` or Recommendation tables. A future persistence phase requires SC assignment for append-only adapter run/output/failure evidence and deterministic target event-ID registration.

## Protected state

- Recommendation P0 remains authoritative;
- general exposure remains separate from P2 experiment exposure;
- P1/P2 metrics and source authority unchanged;
- production worker absent and scheduler disabled;
- replay not authorized;
- production shadow disabled;
- kill switch enabled;
- sampling `0 BPS`;
- cohort empty;
- Search cutover not started.

## Next entry conditions

- merge the DP-4 PR and establish merged main SHA;
- decide whether DP-5 projection/snapshot work needs adapter persistence first;
- allocate SQL only if persistent shadow runs/outputs are required;
- approve deterministic adapter event-ID format before canonical event creation;
- keep identity mapping repository and production cutover outside DP-4.
