# SC Recommendation Consumer Adoption Scope Decision

## Scope

Define exactly which adoption layer RCA-0 may implement.

## Current Baseline

The Data handoff separates consumer contract adoption, shadow reconciliation, runtime enablement, production write, traffic cutover and authority transfer.

## Contract Impact

RCA-0 classification: `CONTRACT_AND_FIXTURE`.

| Layer | RCA-0 decision |
|---|---|
| consumer contract adoption | allowed |
| deterministic fixture validation | allowed |
| shadow reconciliation | deferred to RCA-1 |
| runtime consumer enablement | prohibited |
| production write | prohibited |
| traffic cutover | prohibited |
| authority transfer | prohibited |

## Authority

Intelligence leads consumer implementation. Reliability approves every P2 semantic fixture. SC approves scope changes.

## Dependencies

- existing Data projection contracts;
- current P1/P2 source fixtures;
- no real identity mapping;
- no Operations runtime dependency.

## Allowed Changes

- immutable consumer DTOs or records;
- validators and compatibility classifiers;
- parser/adapter boundary without Spring registration;
- deterministic fixtures and contract tests;
- explicit unsupported/migration-required results.

## Forbidden Changes

Repository/service wiring, Data projection DB reads, feature flags, schedulers, workers, shadow execution, source switching and persistence.

## Verification

Tests must prove strict version parsing, required-field failure, identity failure, exposure authority protection, exact P2 outcome window and no current-source mutation.

## Compatibility

A successful RCA-0 fixture means only that a candidate contract can be parsed and semantically classified. It is not parity, runtime or production evidence.

## Risks

Aggregate profile data may be insufficient for current P1 event-grain semantics. Fixture success must not imply source equivalence.

## Handoff

RCA-1 may be proposed only after RCA-0 identifies exact parity fields, missing semantics and a safe identity strategy.
