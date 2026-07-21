# IP-12.5 Handoff

## Baseline

- Repository: `gycha0109-beep/journey-connect-backend`
- Base branch: `main`
- Base HEAD: `356cc0e25c9da2b57a5f9ed292f997bc3cea3119`
- Work branch: `codex/ip-12-5-readiness`
- Attested implementation HEAD: `f95c1928b6143d8c5d519d6f2504f158661d82cb`

## Implemented

- fail-closed operational approval inputs for positive production sampling
- activation window enforcement before identity resolution
- production controls restricted to `prod`/`production` profile resources
- safe production defaults: disabled, kill-switch active, 0 BPS, empty allowlist
- `verifyIp125InternalPilotReadiness` and `verifyIp125` tasks
- PR CI upgraded to run the full protected readiness gate
- activation/rollback runbook and machine-readable evidence templates
- canonical PostgreSQL test bootstrap for SQL 01..28
- explicit separation of production login verification from Testcontainers administrator bootstrap

## External verification

| Gate | Result | Evidence |
|---|---|---|
| Backend `verifyIp125` | PASS | workflow run `29814868823` |
| Recommendation Java Core | PASS | workflow run `29814868799` |
| PostgreSQL 15/18 matrix | PASS | workflow run `29814868839` |
| Direct compile | PASS | 586 pure + 68 backend typed-stub sources |
| Direct operational contract | PASS | 43 assertions |
| IP-12 static | PASS | 68/68 |
| IP-12.5 static | PASS | 51/51 |

## Protected

- production shadow remains disabled
- effective sample remains 0 BPS
- actual cohort remains empty
- no account hash generated
- no Search response cutover
- no new canonical SQL or migration
- legacy `/api/v1/explore` remains authoritative

## Current decision

`IP-12.5 HOLD_OPERATIONAL_INPUTS_PENDING`

Technical readiness is complete. Operational authorization is not complete.

## Required user inputs

1. one to three approved lowercase SHA-256 internal account hashes
2. initial sample BPS from 1 to 10
3. UTC activation start and end
4. approval reference and approver reference
5. execution owner reference
6. rollback owner confirmation/reference
7. concrete metric or dashboard verification reference
8. exact production restart/deployment procedure

No pilot start is permitted until all values are supplied and separately approved.
