# RCA-0 Verification Summary

## Scope

RCA-0 only. Work started from exact authoritative `main` `a89dd336cfdd20f650eac4aee8dd2db8de8f3c04`. The phase implements DB-free consumer parsing, validation, compatibility classification and deterministic fixtures. This correction does not extend RCA-0 contract semantics or runtime scope.

## Current Baseline

`DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE`; canonical SQL `01..52` protected; SQL `53+` unallocated; production activation not authorized; current P1/P2 authority unchanged.

## Contract Impact

Registers and implements `recommendation-data-consumer-alignment-v1`, `recommendation-profile-input-consumer-v1`, `experiment-outcome-input-consumer-v1`, and `recommendation-data-consumer-fixture-v1` without creating runtime authority. The exact-final-head correction changes CI execution scope only; contract IDs, fixture expectations and compatibility classifications are unchanged.

## Authority

Implementation lead and P1 semantics: Intelligence. P2 exposure/outcome/metric semantics: Reliability. Registry and breaking changes: System Coordination. Runtime/deployment: Operations. `RP` remains Reliability Platform.

## Dependencies

Reads Data candidate field contracts through a deterministic reader boundary and verifies source-field equivalence against the existing Data projection records. No Spring, DB, HTTP, environment, or system-clock dependency.

## Allowed Changes

Pure Java immutable consumer types, validators, compatibility matrices, deterministic fixtures, non-production verifier/evidence, RCA-0 documentation, and narrowly scoped CI coverage.

## Forbidden Changes

SQL/migration, P1/P2 source wiring, Spring beans, repositories, workers, schedulers, feature flags, production configuration, identity mapping implementation, runtime adoption, traffic cutover, authority transfer, protected-check removal, `continue-on-error`, or PR merge.

## Verification

Committed verification assets:

- `verification/rca0/run_rca0_verification.py`
- `verification/rca0/RCA0_BASELINE.tsv`
- `verification/rca0/RCA0_VERIFICATION_STATUS.tsv`
- `verification/rca0/java/.../Rca0ContractTestMain.java`

The verifier compiles with `javac --release 21 -Xlint:all -Werror`, executes all deterministic fixtures, validates protected diffs and source-field equivalence, then executes `:jc-recommendation-core:check` and backend `test` when requested. Runtime JSON/TSV/log evidence records the exact tested SHA and exact commands.

### Initial Backend PR CI failure

| Field | Evidence |
|---|---|
| failed PR head | `4605f4e3acd2f975c055e36b0c0d1fcd776fc9c7` |
| workflow | `Backend PR CI` |
| run ID | `30075921716` |
| job | `Java / canonical PostgreSQL 15` |
| failed step | `Run IP-12.5 full protected readiness gate` |
| classification | `CROSS_PHASE_VERIFIER_SCOPE_LEAK` |

Executed command chain:

```text
./gradlew verifyIp125 --stacktrace --no-daemon
python ../verification/dp5/run_dp5_static_verification.py
python ../verification/data-platform-closure/run_data_platform_closure_verification.py
```

`verifyIp125` completed with `BUILD SUCCESSFUL`. The following historical DP-5 verifier failed before the Data Platform closure verifier could execute. Its assertion treated every path under `jc-backend/src/main/` as a protected DP-5 production change.

Expected by the historical assertion:

```text
no changed path under jc-backend/src/main/
```

Actual RCA-0 paths rejected by that assertion:

```text
jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/CompatibilityMatricesV1.java
jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/DeterministicFixtureReaderV1.java
jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/P1ConsumerValidatorV1.java
jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/P2ConsumerValidatorV1.java
jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/RecommendationDataConsumerContracts.java
```

The rejected package is the approved non-Spring, non-runtime RCA consumer/fixture boundary. No DP-5 authoritative file, Recommendation P1/P2 source, core module, SQL or production configuration changed.

### Minimal correction

Changed files:

- `.github/workflows/backend-pr-ci.yml`
- `.github/workflows/recommendation-p0-db-ci.yml`

Corrections:

1. Both workflows checkout `${{ github.event.pull_request.head.sha || github.sha }}` so evidence is bound to the actual PR head rather than the synthetic merge ref.
2. `verifyIp125`, `p0Verification`, PostgreSQL 15/18 tests and artifact upload remain mandatory.
3. The DP-5 verifier runs only when DP-5/Data projection-owned SQL, contracts, governance allocation, documents or verifier paths change.
4. The Data Platform closure verifier runs only when closure-owned documents or evidence paths change.
5. Non-owning phases report `NOT_APPLICABLE`; they are not reported as PASS and are not disabled globally.

Protection is not weakened: the owning verifier still executes for every change in its authoritative scope, while RCA-0 independently rejects changes to current P1/P2 sources, `jc-recommendation-core`, canonical SQL and production controls.

### Exact-final-head evidence rule

The committed source uses runtime-bound `SELF_HEAD` semantics because embedding a commit's own SHA or workflow run IDs in that same commit would invalidate exact-head evidence. The exact resolved `testedSha`, exact commands and check states are emitted by `verification/rca0/runtime/RCA0_VERIFICATION_SUMMARY.json` and `.tsv`. Final workflow run IDs, conclusions and resolved final head are recorded in PR #23's body after all three workflows complete; updating the PR body does not change the tested Git head.

Required final workflows:

```text
RCA-0 Contract and Fixture CI
Recommendation P0 Database CI
Backend PR CI
```

## Compatibility

Passing fixture scenarios prove parse/validate/classify behavior only. PostgreSQL proves protected backend/DB regression, not RCA runtime adoption. Shadow reconciliation, canary, load, replay and production remain `NOT_APPLICABLE`, not PASS.

## Risks

Identity mapping ownership remains unresolved. P1 event-grain semantics and explicit preferences remain missing. P2 stale-assignment filtering, one-observation dedupe and canonical dataset bytes/hash remain protected migration dimensions. Historical phase verifiers must continue to use ownership-scoped triggers to avoid cross-phase false positives without broad allowlists.

## Handoff

RCA-1 shadow reconciliation requires a separate System Coordination approval. User approval is required before merge. Exact-final-head merge eligibility is determined only from the three required workflow conclusions recorded on PR #23.
