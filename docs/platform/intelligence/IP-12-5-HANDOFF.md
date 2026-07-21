# IP-12.5 Handoff

## Baseline

- Repository: `gycha0109-beep/journey-connect-backend`
- Base branch: `main`
- Base HEAD: `356cc0e25c9da2b57a5f9ed292f997bc3cea3119`
- Work branch: `codex/ip-12-5-readiness`

## Implemented

- fail-closed operational approval inputs for positive production sampling
- activation window enforcement before identity resolution
- safe production profile resources restored to GitHub
- `verifyIp125InternalPilotReadiness` and `verifyIp125` tasks
- PR CI upgraded to run the full protected readiness gate
- activation/rollback runbook and machine-readable evidence templates

## Protected

- production shadow remains disabled
- effective sample remains 0 BPS
- actual cohort remains empty
- no account hash generated
- no Search response cutover
- no SQL/migration change
- legacy `/api/v1/explore` remains authoritative

## Current decision

`IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING`

## Required user inputs

1. approved Project Owner account hash
2. approved Team Lead Youngtak account hash, if included
3. approved Backend Owner account hash, if included
4. initial sample BPS from 1 to 10
5. UTC activation start/end
6. approval reference and approver reference
7. execution owner reference
8. rollback owner confirmation
9. metric verification path
10. exact environment restart procedure
