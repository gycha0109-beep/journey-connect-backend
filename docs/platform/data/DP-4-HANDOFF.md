# DP-4 Handoff

## Provisional result

`DP4_IMPLEMENTATION_COMPLETE_WITH_SQL_ASSIGNMENT_PENDING` after exact-head CI succeeds.

## Delivered

- protected Recommendation P0 source-to-Data shadow mapping contract;
- all 16 P0 event wires mapped to existing Data taxonomy candidates;
- explicit identity and general-exposure bindings;
- separate deterministic adapter output fingerprint;
- source fingerprint, P0 resolver and taxonomy compatibility regression;
- stable unsupported/quarantine classifications aligned to DP-3 reasons;
- golden/invalid fixtures, compatibility matrix and machine-readable evidence;
- no SQL, runtime consumer, scheduler, dual-write or production cutover.

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
