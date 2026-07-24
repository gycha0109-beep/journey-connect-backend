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

## Stage 6 — Recommendation P0 Database CI scope correction

- The earlier exact-head run exposed that `Recommendation P0 Database CI` ran historical DP-5 and Data Platform closure diff verifiers for every backend source change.
- Corrected only workflow ownership dispatch. P0 static and PostgreSQL 15/18 execution remained mandatory.
- No DP-5 or closure evidence, SQL, P1/P2 source, core, production control, or runtime behavior was weakened or changed.

## Stage 7 — Backend PR CI failure investigation

- Purpose: investigate PR #23 exact-head Backend CI without extending RCA-0 implementation.
- Reconfirmed current `main`: `a89dd336cfdd20f650eac4aee8dd2db8de8f3c04`.
- Initial failed head: `4605f4e3acd2f975c055e36b0c0d1fcd776fc9c7`.
- Failed workflow/run: `Backend PR CI`, run `30075921716`.
- Failed job/step: `Java / canonical PostgreSQL 15` / `Run IP-12.5 full protected readiness gate`.
- Exact Gradle command: `./gradlew verifyIp125 --stacktrace --no-daemon`.
- Gradle result: `BUILD SUCCESSFUL`; IP-12.5 and backend regression were not the defect.
- Next command: `python ../verification/dp5/run_dp5_static_verification.py`.
- Failure assertion: DP-5 historical verifier rejected all `jc-backend/src/main/**` changes as `protected production/Recommendation/Search source changed`.
- Actual rejected files: the five pure Java classes under `com/jc/backend/recommendation/dataadoption/`.
- The following Data Platform closure verifier was not reached because the DP-5 command failed first.
- Classification: `CROSS_PHASE_VERIFIER_SCOPE_LEAK`.
- Rejected alternatives: not `RCA0_IMPLEMENTATION_DEFECT`, not `ACTUAL_PROTECTED_AUTHORITY_CHANGE`, not infrastructure failure.

## Stage 8 — Minimal exact-head CI correction

- Modified `.github/workflows/backend-pr-ci.yml`.
- Modified `.github/workflows/recommendation-p0-db-ci.yml` only to bind checkout to the exact PR head.
- Added exact checkout ref `${{ github.event.pull_request.head.sha || github.sha }}` to both workflows.
- Backend CI continues to execute `verifyIp125` unconditionally.
- Recommendation P0 Database CI continues to execute `p0Verification` and PostgreSQL 15/18 unconditionally.
- DP-5 verifier executes only when DP-5/Data projection authoritative paths change.
- Data Platform closure verifier executes only when closure-owned paths change.
- Non-owning verifier status is printed as `NOT_APPLICABLE`; no check uses `continue-on-error`, unconditional success, broad `jc-backend/src/**` allowlisting, or skipped PostgreSQL execution.
- Existing RCA verifier continues to assert that P1/P2 sources, `jc-recommendation-core`, SQL `01..52`, SQL `53+`, production profiles and runtime controls are unchanged.

## Stage 9 — Exact-final-head verification and evidence publication

- Required reruns: `RCA-0 Contract and Fixture CI`, `Recommendation P0 Database CI`, and `Backend PR CI`.
- Exact commands and resolved `testedSha` are emitted in `verification/rca0/runtime/RCA0_VERIFICATION_SUMMARY.json` and `.tsv`.
- Final workflow run IDs and conclusions are written to the PR body after all runs terminate. This avoids a post-success source commit that would invalidate the exact tested head.
- Draft status and no-merge rule remain mandatory.
- Residual semantic risks are unchanged: P1 event-grain/explicit-preference gaps, P2 protected migration dimensions, and unresolved restricted identity mapping ownership.
