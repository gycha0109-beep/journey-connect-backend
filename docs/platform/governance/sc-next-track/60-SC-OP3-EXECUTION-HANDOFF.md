# SC to OP-3 Execution Handoff

## Status

`HANDOFF_STATUS=PREPARED_BUT_NOT_AUTHORIZED`

## Entry baseline

- SC baseline: `83da5e5a075cdde2e5fd7c3e81d81f77d8c987f8`
- OP-2 exact tested head: `79009cf047fe67775b972d43a5f3f72aa8351908`
- OP-2 merge commit: `83da5e5a075cdde2e5fd7c3e81d81f77d8c987f8`

## OP-3 objective after authorization

Perform one controlled, manual, non-production Stage 1 execution at the approved ceiling, preserving current P1/P2 primary authority and forbidding candidate serving.

## Mandatory preconditions

Use `58-SC-OP3-ENTRY-AND-GATE.md`. Every row must be true with exact-head evidence. No inferred, pending, application-only, or externally blocked state is acceptable.

## Execution boundaries

- Manual activation only
- Stable allowlisted cohort only
- Non-production only
- Maximum approved traffic ceiling only
- Candidate read-only and non-serving
- Primary response unchanged
- Kill switch continuously available
- Abort on any critical alert
- No automatic ramp
- No production identity, endpoint, database, or route

## Required output

- Exact tested/executed SHA
- Operator and approver identities
- Start/stop timestamps
- Effective traffic proof
- Dashboard and alert evidence
- Metric snapshots
- Rollback/abort evidence if invoked
- Blocker and incident register updates
- SC exit recommendation

## Authorization

This handoff is not execution authority. A separate SC decision must set `SC_OP3_EXECUTION_APPROVED=YES`.