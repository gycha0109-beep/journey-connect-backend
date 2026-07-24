# RCA-0 Handoff and RCA-1 Blockers

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

Fixture compatibility does not mean runtime compatibility. P1 remains aggregate-grain and conditionally compatible only; P2 exact exposure/outcome fixtures are compatible for fixture validation only. Protected migration dimensions remain unresolved.

## Risks

RCA-1 is blocked by unresolved identity mapping ownership, incomplete P1 event-grain equivalence, protected P2 migration dimensions, and absent runtime/deployment authorization.

## Handoff

RCA-0 can be merged only after explicit user approval and exact final-head validation. The permitted completion statement is:

```text
RCA0_CONTRACT_AND_FIXTURE_COMPLETE
NEXT_PHASE: RCA-1 SHADOW RECONCILIATION REQUIRES SEPARATE SC APPROVAL
DB_CHANGE: NONE
SQL_ALLOCATION: NOT_REQUIRED
PRODUCTION_IMPACT: NONE
PRODUCTION_ACTIVATION: NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
```

RCA-1 proposal must define source-by-source reconciliation, identity mapping governance, P1 event-grain equivalence, P2 stale-assignment/dedupe/hash equivalence, rollback, observability, and separate SC/Reliability/Intelligence approvals.

