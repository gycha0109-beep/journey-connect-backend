# Rollback Drill Plan

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


| Level | Control | Owner | Maximum time | OP-0 state |
|---|---|---|---|---|
| `LEVEL_1` | `FLAG_OFF` | OPERATIONS | 60s | `NOT_EXECUTED` |
| `LEVEL_2` | `LANE_KILL_SWITCH` | OPERATIONS | 60s | `NOT_EXECUTED` |
| `LEVEL_3` | `GLOBAL_SHADOW_DISABLE` | OPERATIONS | 120s | `NOT_EXECUTED` |
| `LEVEL_4` | `CONFIG_ROLLBACK` | OPERATIONS | 300s | `NOT_EXECUTED` |
| `LEVEL_5` | `DEPLOYMENT_ROLLBACK` | OPERATIONS | 600s | `NOT_EXECUTED` |
| `LEVEL_6` | `CREDENTIAL_REVOKE` | OPERATIONS | 300s | `NOT_EXECUTED` |
| `LEVEL_7` | `NETWORK_ROUTE_REVOKE` | OPERATIONS | 600s | `NOT_EXECUTED` |

OP-2 must execute all levels in the isolated non-production environment while effective traffic remains 0. Each drill records trigger, procedure, verification, time, evidence, recovery criteria and escalation. No drill is represented as executed in OP-0.
