# RCA-0 Protected Authority Report

## Scope

RCA-0 only. Work started from exact authoritative `main` `a89dd336cfdd20f650eac4aee8dd2db8de8f3c04`. The phase implements DB-free consumer parsing, validation, compatibility classification and deterministic fixtures. This report fixes the no-change boundary for current Recommendation sources, core, SQL, production controls, and P2 evidence.

## Current Baseline

`DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE`; canonical SQL `01..52` protected; SQL `53+` unallocated; production activation not authorized; current P1/P2 authority unchanged.

## Contract Impact

Registers and implements `recommendation-data-consumer-alignment-v1`, `recommendation-profile-input-consumer-v1`, `experiment-outcome-input-consumer-v1`, and `recommendation-data-consumer-fixture-v1` without creating runtime authority.

## Authority

Implementation lead and P1 semantics: Intelligence. P2 exposure/outcome/metric semantics: Reliability. Registry and breaking changes: System Coordination. Runtime/deployment: Operations. `RP` remains Reliability Platform.

## Dependencies

Reads Data candidate field contracts through a deterministic reader boundary and verifies source-field equivalence against the existing Data projection records. No Spring, DB, HTTP, environment, or system-clock dependency.

## Allowed Changes

Pure Java immutable consumer types, validators, compatibility matrices, deterministic fixtures, non-production verifier/evidence, RCA-0 documentation, and CI coverage.

## Forbidden Changes

SQL/migration, P1/P2 source wiring, Spring beans, repositories, workers, schedulers, feature flags, production configuration, identity mapping implementation, runtime adoption, traffic cutover, authority transfer, or PR merge.

## Verification

Independent verifier records exact commands and exact tested SHA. It distinguishes `PASS`, `FAIL`, `NOT_EXECUTED`, and `NOT_APPLICABLE`. PostgreSQL, shadow, canary, load, replay, and production checks are not claimed. The verifier rejects any diff under current P1/P2 source files, `jc-recommendation-core/`, canonical SQL, production profiles, or production control modules.

## Compatibility

`RecommendationP1ProfileSource`, `RecommendationP2ObservationSource`, `recommendation_p1_profile_snapshot`, `recommendation_p2_experiment_exposure`, `recommendation-evaluation-dataset-v1`, `engagement_rate`, `fallback_rate`, dataset bytes/hash, evaluation, gate, and release evidence remain authoritative and unchanged.

## Risks

Identity mapping ownership is unresolved. P1 event-grain semantics and explicit preferences are missing. P2 stale-assignment filtering, one-observation dedupe, and canonical dataset bytes/hash remain protected migration dimensions.

## Handoff

RCA-1 shadow reconciliation requires a separate System Coordination approval. User approval is required before merge.

