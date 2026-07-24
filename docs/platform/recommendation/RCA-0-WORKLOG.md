# RCA-0 Worklog

## Stage 1 — Baseline and entry gate

- Purpose: verify actual GitHub `main`, PR #22 merge, SC-2 documents, RCA/RP naming, SQL protection, and production hold.
- Work-start SHA: `a89dd336cfdd20f650eac4aee8dd2db8de8f3c04`.
- Result: entry authorized; no blocker classification triggered.
- Residual risk: historical documents contain phase-time SHAs, so RCA-0 records the exact work-start separately.

## Stage 2 — Source and contract review

- Reviewed current `RecommendationP1ProfileSource`, `RecommendationP2ObservationSource`, Data `RecommendationProfileInputProjection`, Data `ExperimentOutcomeInputProjection`, `ExperimentExposureBinding`, and P2 evaluation variant/metric contracts.
- Confirmed P1 event/fact grain and explicit preference dependency.
- Confirmed P2 exposure authority, seven-day engagement set, bound-run fallback, and stale-assignment filtering.

## Stage 3 — Implementation

- Added pure Java immutable contract taxonomy, deterministic TSV reader, P1/P2 validators, compatibility matrices, synthetic identity port contract, fixtures, and dependency-free contract runner.
- No Spring/JPA/HTTP/DB/environment/system-clock dependency.

## Stage 4 — Local verification and corrections

- Compiled with Java 21, `-Xlint:all -Werror`.
- Executed 12 P1 and 21 P2 fixtures.
- Corrected empty P2 outcome-list handling and identity fail-closed probe data.
- Final local contract runner result: PASS.

## Stage 5 — Independent verification design

- Added protected diff, SQL inventory, source-field equivalence, fixture uniqueness, forbidden dependency, document structure, exact SHA, core regression, backend regression, and execution-state evidence checks.
- PostgreSQL/shadow/canary/load/replay/production remain `NOT_APPLICABLE` in the RCA-0 verifier.

## Stage 6 — Existing protected CI compatibility correction

- The exact PR head exposed that `Recommendation P0 Database CI` ran the historical DP-5 and Data Platform closure diff verifiers for every backend source change.
- The P0 Java static suite itself passed, but those completed-phase allowlists rejected the approved RCA package before PostgreSQL execution.
- Corrected only workflow dispatch logic: the DP-5 verifier now runs when DP-5/Data projection artifacts change, and the Data closure verifier runs when Data closure artifacts change. Both report `NOT_APPLICABLE` for unrelated RCA diffs.
- P0 static and PostgreSQL 15/18 regression execution remain mandatory for every matching backend change.
- No DP-5 or closure evidence, SQL, P1/P2 source, core, production control, or runtime behavior was weakened or changed.

## Stage 7 — Final review and PR handoff

- Pending exact GitHub PR-head CI results after the compatibility correction.
- Merge remains prohibited until explicit user approval.
