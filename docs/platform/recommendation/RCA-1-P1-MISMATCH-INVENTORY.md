# RCA-1 P1 Mismatch Inventory

## Scope

RCA-1 Model A offline deterministic reconciliation only. No DB, runtime dark read, actual identity mapping, production traffic, cutover, or authority transfer.

## Current Baseline

Authoritative work-start: `5a0ca52c8226a0f4a6e21f9af96c7da0732c8d5b`. RCA-0 and Data Platform technical closure remain authoritative. SQL `01..52` is protected, SQL `53+` is unallocated, production activation is not authorized, and current P1/P2 authority is unchanged.

## Implementation

Generated a P1-only mismatch inventory. Expected-negative cases prove exact mismatch, derived mismatch, checkpoint mismatch/staleness, lineage mismatch, and all synthetic identity failure states. No aggregate count is expanded into a fabricated event stream.

## Authority

P1 semantics are owned by Intelligence; P2 exposure/outcome/metric semantics by Reliability; SC controls breaking changes; Operations runtime scope is not required.

## Dependencies

Pure Java 21 standard library, existing RCA-0 contracts/fixtures, recorded synthetic references, candidate fixtures, and explicit fixture timestamps.

## Allowed Changes

Immutable reconciliation records/enums, deterministic normalization, lane comparators, synthetic fixtures, redacted evidence, verifier, tests, docs, and minimal CI.

## Forbidden Changes

RCA-0 behavior, P1/P2 sources, Recommendation result, Spring/JPA/JDBC/HTTP, SQL, roles/grants, runtime wiring, production config/traffic, real identity mapping, canonical P2 hash/release evidence, or authority transfer.

## Identity/Privacy

`IDENTITY_MODE=SYNTHETIC_ONLY`. Absent, invalid, expired, deleted, mismatched, unauthorized-purpose, and unauthorized-caller cases fail closed. Evidence contains hashed case IDs and no raw identity or mapping pair.

## Comparison Dimensions

P1 and P2 use independent dimensions and independent lane verdicts. Expected gaps, migration-required dimensions, protected authority differences, and unexpected mismatches are distinct.

## Evidence

Deterministic fixed-order JSON/TSV includes only approved synthetic-safe fields, explicit timestamp, verifier version, and exact tested SHA. CI retention is 90 days.

## Verification

Independent verifier checks baseline, contracts, 23 P1 and 39 P2 cases, taxonomy, redaction, determinism, counters, protected diff, RCA-0 regression, Recommendation core, and backend tests.

## Compatibility

Fixture reconciliation is not runtime, production, cutover, source replacement, or authority-transfer compatibility.

## Risks

Model A cannot prove DB query equivalence, source freshness under live traffic, runtime latency, real identity safety, or production stability.

## Exit Criteria

`P1_RECONCILIATION_EXECUTED`, `P1_DIFFERENCES_CLASSIFIED`, `P2_RECONCILIATION_EXECUTED`, `P2_DIFFERENCES_CLASSIFIED`, `IDENTITY_BOUNDARY_ENFORCED`, `PROTECTED_AUTHORITY_UNCHANGED`, `NO_PRODUCTION_TRAFFIC`, `NO_AUTHORITY_TRANSFER`.

## Handoff

RCA-1B non-production read-only reconciliation and RCA-2 runtime dark read each require a separate SC authorization and implementation PR.
