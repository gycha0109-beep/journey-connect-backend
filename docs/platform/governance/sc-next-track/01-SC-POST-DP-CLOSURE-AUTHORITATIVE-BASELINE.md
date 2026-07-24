# SC Post-DP-Closure Authoritative Baseline

## Scope

Fix the system baseline after PR #21 without rewriting historical phase-time evidence.

## Current Baseline

| Item | Authority |
|---|---|
| repository | `gycha0109-beep/journey-connect-backend` |
| main | `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77` |
| closure head | `478a15929db43b1b3d3fde4648a5027a36ee75da` |
| tree comparison | zero changed files |
| Data status | `DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE` |
| canonical SQL | `01..52` immutable |
| SQL `53+` | unallocated |
| production | not authorized |

PR #21 exact-head evidence belongs to the closure head. Main push CI is unavailable and merge-commit local checkout was not executed.

## Contract Impact

This document supersedes earlier current-state expressions such as SQL `01..28`, DP-7 proposed and merge pending. It does not invalidate the historical evidence context in which those expressions were written.

## Authority

- Data retains canonical event, projection, quality and integration evidence authority.
- Intelligence retains P1 profile and recommendation runtime meaning.
- Reliability retains P2 experiment, metric, evaluation and release meaning.
- Operations retains runtime execution, deployment and operational control.
- SC retains registries, SQL allocation, breaking-change and authority-transfer approval.

## Dependencies

All next-track work depends on this baseline and the closed Data handoffs.

## Allowed Changes

Forward-only governance decisions and contract-only consumer work.

## Forbidden Changes

SQL `01..52` modification, closure evidence rewrite, authority transfer, runtime activation or source cutover.

## Verification

- fetch current main;
- compare closure head to merge commit;
- verify SQL `01..52` exactly once and SQL `53+` absent;
- preserve protected production defaults.

## Compatibility

Recommendation profile and outcome remain `CONDITIONALLY_COMPATIBLE`; Intelligence and Search remain `INCONCLUSIVE` at the Data closure boundary.

## Risks

Historical governance documents can be read as current unless the post-closure decision is consulted first.

## Handoff

Use this baseline for RCA-0 and all subsequent Intelligence, Search, Operations and Reliability entry decisions.
