# OP Phase Map

| Field | Value |
|---|---|
| Official phase | `OP-0 RCA-2 Stage 1 Operations Preparation Baseline` |
| Work-start / authoritative main | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| RCA-2 exact final head | `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` |
| RCA-2 merge commit | `b57c344c9b4e332966fe9f6d36a5da66a5faae71` |
| SC-6 exact final head | `20da93e932c50b5bebd549a56db40edb00ca1eea` |
| SC-6 merge commit | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| Artifact version | `op0-rca2-stage1-operations-preparation-v1` |
| Updated at | `2026-07-26T14:15:55Z` |


| Phase | Contract | Scope | Traffic |
|---|---|---|---|
| OP-0 | `BASELINE_AND_EXECUTION_PLAN` | this package | 0% |
| OP-1 | `ENVIRONMENT_AND_ACCESS_PREPARATION` | endpoint, credential, allowlist, cohort, actual adapter readiness, route verification | 0% |
| OP-2 | `OBSERVABILITY_AND_SAFETY_PREPARATION` | metrics, dashboard, alerts, telemetry, revoke/rollback drills, approvals | 0% |
| OP-3 | `CONTROLLED_STAGE1_EXECUTION` | manual 1%, 30 minutes and 100 executions, live evidence | manual ceiling 1% only after all gates |
| OP-4 | `EXIT_REVIEW_AND_RCA3_HANDOFF` | observation evaluation, mismatch/lag/rollback evidence, exit decision | no production authorization |

OP-1 and OP-2 require separate implementation/evidence changes. OP-3 requires a separate explicit manual enablement decision.
