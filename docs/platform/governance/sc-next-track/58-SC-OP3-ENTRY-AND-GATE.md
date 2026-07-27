# SC OP-3 Entry AND Gate

## Rule

OP-3 entry is an AND gate. One false, unresolved, not executed, or blocked condition keeps the gate closed.

| Gate | Required state | Current state |
|---|---|---|
| OP-2 merged and exact-head verified | YES | YES |
| External metrics backend available | YES | NO |
| Dashboard deployed and queryable | YES | NO |
| Critical alert route delivered end-to-end | YES | NO |
| Warning alert route delivered end-to-end | YES | NO |
| Workload credential issued and bounded | YES | NO |
| Test identity allowlist store connected | YES | NO |
| Non-production endpoint approved | YES | NO |
| Candidate source selected | YES | NO |
| Candidate protocol selected | YES | NO |
| Candidate API/schema version pinned | YES | NO |
| Candidate adapter integration-tested | YES | NO |
| Credential revoke drill passed | YES | BLOCKED_EXTERNAL_DEPENDENCY |
| Network route revoke drill passed | YES | BLOCKED_EXTERNAL_DEPENDENCY |
| Deployment rollback drill passed | YES | NOT_EXECUTED |
| Manual enablement operator assigned | YES | UNASSIGNED |
| Incident commander assigned | YES | UNASSIGNED |
| Evidence retention location verified | YES | APPLICATION_ONLY |
| SC final execution approval | YES | NOT_GRANTED |

## Current verdict

`OP3_ENTRY_RECOMMENDATION=BLOCKED`

## Prohibitions while blocked

- Non-production traffic increase above 0%
- Feature flag enablement
- Candidate invocation against an external endpoint
- Candidate result serving
- Primary response mutation
- Production route or production identity use
- Automatic rollout
- Authority transfer