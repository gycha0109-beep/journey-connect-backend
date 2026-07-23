# Data Platform Production Activation Dependencies V1

```text
Data technical baseline closed
→ target-track contracts approved
→ Operations runtime implemented
→ Reliability gates approved
→ production shadow authorized
→ shadow execution
→ parity/drift validation
→ limited cohort
→ staged rollout
→ production adoption decision
```

| Gate | Objective | Owner | Prerequisites | Evidence | Approval | Rollback | Status |
|---|---|---|---|---|---|---|---|
| `GATE-1` | technical closure | Data/SC | DP0~7 merged | closure pack+exact-head CI | SC/user | docs revert | PR_READY_FOR_USER_APPROVAL |
| `GATE-2` | contract readiness | target/SC | handoffs | approved contracts | target+SC | current sources | PARTIAL |
| `GATE-3` | runtime readiness | Operations | workers/scheduler/deploy | runtime tests | Operations | disable | NOT_READY |
| `GATE-4` | observability | Ops/Reliability | signals | dashboard/alerts | joint | HOLD | NOT_READY |
| `GATE-5` | security | Ops/Security | roles | secrets/network/audit | joint | revoke | PARTIAL |
| `GATE-6` | reliability | Reliability | SLI/SLO/recovery | thresholds/DR/rollback | Reliability | HOLD | NOT_READY |
| `GATE-7` | shadow authorization | joint | G2~6 | signed scope/cohort/kill | joint | stop | NOT_AUTHORIZED |
| `GATE-8` | consumer adoption | target | parity | migration/rollback | target+Rel+SC | current source | NOT_AUTHORIZED |
| `GATE-9` | production cutover | joint | staged evidence | promotion pack | joint go/no-go | rollback | NOT_AUTHORIZED |

GATE-1 becomes authoritative `COMPLETE` only after user-approved closure PR merge and post-merge verification. Gates 2–9 remain independent and cannot be inferred from technical closure.
