# SC RCA-1B Verification Plan

## Scope

Define governance and future implementation verification without reporting unexecuted database work as PASS.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 exact-final-head `38896b2a37180633870282e9d9e305d9c9fbbf8a`.

## Decision

SC-4 governance verifier must execute:

- exact main/work-start and PR #25 merge/tree equivalence;
- RCA-1 completion, lane, identity, contract and fixture markers;
- official RCA/RP naming;
- SQL `01..52`, SQL `53+` absence, production and authority protection;
- single environment/version/dataset/identity decisions;
- finite read-only limits and query allowlist;
- P1/P2 DB dimension separation;
- checkpoint/lineage and evidence policy;
- prerequisite and DB/SQL decisions;
- required documents and implementation handoff;
- governance-only diff and unchanged RCA-0/RCA-1 historical evidence.

Future RCA-1B implementation verification must add:

- PostgreSQL 15/18 ephemeral schema/seed execution;
- role/grant permission and blocked-write tests;
- allowlisted query/fingerprint tests;
- lane query result, checkpoint, lineage, duplicate and counter tests;
- deterministic/redacted evidence and cross-version equivalence.

## Rationale

Governance authorization and database execution are separate evidence classes.

## Authority

SC owns governance verifier; Operations owns DB boundary tests; Intelligence/Reliability own lane acceptance; Privacy/Security owns evidence scanning.

## Dependencies

Machine-readable decisions and exact-head CI.

## Execution Environment

SC-4 itself uses no PostgreSQL. Future implementation uses CI ephemeral PostgreSQL only.

## DB Access Boundary

Any implementation test that lacks the read-only login or finite limits fails before query execution.

## Query Boundary

Unknown query IDs/fingerprints and unbounded queries fail closed.

## Identity/Privacy

Redaction scan and synthetic-only assertions are mandatory.

## Evidence

Statuses are `PASS`, `FAIL`, `NOT_EXECUTED`, `NOT_APPLICABLE`. PostgreSQL reconciliation, role tests, actual queries, runtime, canary, load, replay, production and actual identity mapping are `NOT_EXECUTED` in SC-4.

## DB/SQL Impact

None.

## Production Impact

None.

## Verification

The independent verifier writes deterministic JSON/TSV with commands, details and exact tested SHA.

## Risks

A passing governance verifier does not imply a passing implementation or safe database permission boundary.

## Exit Criteria

SC-4 exact-head verifier and all triggered governance continuity workflows pass; implementation remains separate.

## Handoff

The RCA-1B implementation PR must rerun every required test after any head change.