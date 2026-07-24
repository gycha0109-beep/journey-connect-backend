# RCA-0 P2 Outcome Compatibility Matrix

## Scope

RCA-0 only. Work started from exact authoritative `main` `a89dd336cfdd20f650eac4aee8dd2db8de8f3c04`. The phase implements DB-free consumer parsing, validation, compatibility classification and deterministic fixtures.

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

Independent verifier records exact commands and exact tested SHA. It distinguishes `PASS`, `FAIL`, `NOT_EXECUTED`, and `NOT_APPLICABLE`. PostgreSQL, shadow, canary, load, replay, and production checks are not claimed.

## Compatibility

| P2 requirement | Classification | RCA-0 conclusion |
|---|---|---|
| experiment/version/variant | `EXACT` | Variant restricted to baseline/treatment |
| exact P2 exposure reference | `EXACT` | Authority fixed to `recommendation_p2_experiment_exposure` |
| bound run/subject/session/exposure timestamp | `EXACT` | Binding mismatch fails closed |
| outcome window | `EXACT` | Exactly `604800` seconds |
| click/like/save/share | `EXACT` | Only protected engagement set accepted |
| fallback | `EXACT` | Only bound exposed run may supply fallback |
| checkpoint/source count/lineage | `EXACT` | Required and validated |
| stale unexposed assignment | `AUTHORITY_PROTECTED` | Migration equivalence required |
| one-observation dedupe | `AUTHORITY_PROTECTED` | Protected migration dimension |
| canonical dataset bytes/hash | `AUTHORITY_PROTECTED` | Existing dataset remains authoritative |
| evaluation/release evidence | `AUTHORITY_PROTECTED` | Immutable and unchanged |

General recommendation exposure, behavior impression, `view`, `hide`, and `report` are never promoted to P2 exposure or engagement.

## Risks

A fixture-level exact binding does not implement stale-assignment exclusion, one-observation dedupe, canonical dataset serialization/hash, evaluation gates, or release evidence migration.

## Handoff

RCA-1 shadow reconciliation requires a separate System Coordination approval. User approval is required before merge.

