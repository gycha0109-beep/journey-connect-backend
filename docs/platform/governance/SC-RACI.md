# SC RACI

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-raci-v1` |
| status | `ACTIVE / SC-6 STAGE 1 ALIGNED` |
| authoritative main/work-start | `b57c344c9b4e332966fe9f6d36a5da66a5faae71` |
| RCA-2 exact-final-head | `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` |

## Stage 1 RACI

| Area | Responsible | Accountable | Consulted | Approval |
|---|---|---|---|---|
| P1 expected/protected gaps and unexpected mismatch | Intelligence | Intelligence | Data/System Coordination | `BLOCKING_APPROVAL / PENDING_USER_REVIEW` |
| P2 migration gaps and unexpected mismatch | Reliability | Reliability | Data/System Coordination | `BLOCKING_APPROVAL / PENDING_USER_REVIEW` |
| candidate checkpoint, lineage and freshness metrics | Data | Data | lane owners/Operations | `BLOCKING_APPROVAL / PENDING_USER_REVIEW` |
| endpoint, executor, dashboard, alerts and traffic control | Operations | Operations | Reliability/System Coordination | `BLOCKING_APPROVAL / PENDING_USER_REVIEW` |
| workload credential and network rollback | Operations | Operations | Privacy/Security/System Coordination | `BLOCKING_APPROVAL / PENDING_USER_REVIEW` |
| identity allowlist and cohort privacy | Privacy/Security | Privacy/Security | Operations/System Coordination | `BLOCKING_APPROVAL / PENDING_USER_REVIEW` |
| Stage 1 entry, ceiling and manual enablement | SystemCoordination | SystemCoordination | all tracks | `BLOCKING_APPROVAL / PENDING_USER_REVIEW` |
| rollback Level 1..7 execution | Operations | Operations | all tracks | `BLOCKING_APPROVAL / PENDING_USER_REVIEW` |

## Required owner markers

- `endpoint` owner: **Operations** (`OPERATIONS`).
- `credential` owner: **Operations** (`OPERATIONS`).
- `identity_allowlist` owner: **Privacy/Security** (`PRIVACY_SECURITY`).
- `alert` owner: **Operations** (`OPERATIONS`).
- `rollback_execution` owner: **Operations** (`OPERATIONS`).

## Rules

- All six roles are blocking in SC-6; no role is marked approved.
- Operations deployment does not imply enablement.
- P1 expected/protected gaps and P2 migration gaps remain separate from unexpected mismatch.
- Primary authority is unchanged; shadow serving and authority transfer are forbidden.
- Traffic remains 0 until a separate manual decision.
