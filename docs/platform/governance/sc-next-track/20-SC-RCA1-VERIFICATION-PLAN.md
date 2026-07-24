# SC RCA-1 Verification Plan

## Scope

Define SC-3 governance verification and later RCA-1 implementation verification from `f802a105e46a62718616acaa7a3db6c172e7ed10`.

## Current Baseline

RCA-0 exact-final-head `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d` passed fixture, core, backend and PostgreSQL protection. Those results remain historical and are not reclassified.

## Decision

SC-3 verifier is `APPROVED`. Actual RCA-1 comparison remains `NOT_EXECUTED` until a separate implementation PR.

SC-3 checks:

- authoritative main and RCA-0 merge/tree;
- handoff/contracts/fixture counts;
- workstream/naming;
- P1/P2 authority;
- SQL and production defaults;
- single execution/identity decisions;
- lane taxonomies/result taxonomy;
- evidence/privacy and DB/SQL decisions;
- required documents and handoff prompt;
- governance-only diff and unchanged RCA-0 evidence.

Later RCA-1 checks:

- pure Java compile/test;
- deterministic normalization across locale/timezone/map order/system clock;
- lane-separated reference cases;
- zero exact/derived mismatch;
- expected/protected gap inventory;
- fail-closed identity/checkpoint/lineage/exposure/window/fallback cases;
- source/core/SQL/config regressions.

## Rationale

Governance readiness and implementation evidence are different phases and must not share PASS claims.

## Authority

SC owns the entry verifier. Intelligence and Reliability own lane acceptance in implementation.

## Dependencies

Git history, repository files and Python 3 for SC-3. Java 21 for later implementation.

## Allowed Changes

Governance verifier, machine evidence and runtime summary artifact.

## Forbidden Changes

Marking PostgreSQL, actual reconciliation, runtime, canary, load, replay or production as PASS in SC-3.

## Identity/Privacy

Verifier output contains tested SHA, check names, status and non-sensitive detail only.

## DB/SQL Impact

None.

## Production Impact

None.

## Verification

Exact final PR head must be checked out. Runtime evidence records `testedSha`. `PASS`, `FAIL`, `NOT_EXECUTED` and `NOT_APPLICABLE` are distinct.

## Risks

Synthetic merge refs can invalidate exact-head claims; workflow must checkout the PR head SHA.

## Exit Criteria

All executed governance checks PASS, no forbidden diff, unexecuted checks explicitly marked, exact final head recorded.

## Handoff

The RCA-1 implementation PR reruns its own independent verifier after every head change.
