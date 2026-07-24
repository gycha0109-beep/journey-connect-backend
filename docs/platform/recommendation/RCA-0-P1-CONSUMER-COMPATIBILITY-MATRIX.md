# RCA-0 P1 Consumer Compatibility Matrix

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

| P1 requirement | Classification | RCA-0 conclusion |
|---|---|---|
| subject reference | `EXACT` | Explicit Data subject; restricted identity binding still required |
| projection as-of time | `EXACT` | Maps to deterministic reference time |
| source checkpoint | `DERIVABLE` | Coverage evidence, not current event query |
| profile schema/policy versions | `EXACT` | Explicit version mapping |
| 7/30/90-day windows | `EXACT` | Strict enum validation |
| interaction/recent reference/signal aggregates | `DERIVABLE` | Aggregate-grain facts only |
| source event count | `DERIVABLE` | Not partition evidence |
| lineage and Data record fingerprints | `EXACT` | Exact for Data schema only |
| event-grain ordering | `MISSING` | Cannot reproduce current source |
| event timestamps | `MISSING` | Per-event timestamps absent |
| explicit preferences | `MISSING` | `recommendation_user_preference` absent |
| exact `BehaviorProfileEvent` partitions | `MISSING` | Not reproducible from aggregates |
| feature vocabulary transform | `MISSING` | Intelligence-owned transform absent |
| decay inputs | `MISSING` | Per-event age absent |
| saturation inputs | `MISSING` | Exact current inputs not proven |
| current snapshot fingerprint semantics | `AUTHORITY_PROTECTED` | Data fingerprint cannot replace P1 fingerprint |
| aggregate-to-event conversion | `INCOMPATIBLE` | Synthetic stream generation rejected |

Valid 7/30/90-day fixtures return `CONDITIONALLY_COMPATIBLE`, not runtime-ready status.

## Risks

The aggregate projection cannot reconstruct current event ordering, event timestamps, explicit preferences, partition behavior, feature vocabulary transforms, decay/saturation inputs, or P1 fingerprint semantics. No fake `BehaviorProfileEvent` stream is generated.

## Handoff

RCA-1 shadow reconciliation requires a separate System Coordination approval. User approval is required before merge.

