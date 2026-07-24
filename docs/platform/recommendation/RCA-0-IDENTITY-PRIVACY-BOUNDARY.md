# RCA-0 Identity and Privacy Boundary

## Scope

RCA-0 only. Work started from exact authoritative `main` `a89dd336cfdd20f650eac4aee8dd2db8de8f3c04`. The phase implements DB-free consumer parsing, validation, compatibility classification and deterministic fixtures. Identity handling is limited to synthetic fixture binding and an unimplemented `IdentityMappingReadPort` contract.

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

`subject:<opaque-id>` and `user:<numeric-id>` remain separate schemes. `ABSENT`, `INVALID`, `EXPIRED`, and `MISMATCHED` mapping states fail closed. No anonymous or alternate-subject fallback exists.

## Risks

The physical mapping owner, retention, deletion, invalidation, audit, purpose binding, and access-control policy remain unresolved. Numeric/opaque mapping material must not be logged, and existing P2 rows/hashes are never rewritten.

## Handoff

RCA-1 shadow reconciliation requires a separate System Coordination approval. User approval is required before merge.

