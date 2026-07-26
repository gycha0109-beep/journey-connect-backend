# Rollback Levels 1-7 Plan

The full trigger, owner, procedure, maximum execution time, expected state, verification query, recovery criteria, escalation and evidence are in `rollback-matrix.json`.

| Level | Control | Readiness |
|---:|---|---|
| 1 | FLAG_OFF | application drill `PASS` |
| 2 | LANE_KILL_SWITCH | application drill `PASS` |
| 3 | GLOBAL_SHADOW_DISABLE | application drill `PASS` |
| 4 | CONFIG_ROLLBACK | local contract drill `PASS` |
| 5 | DEPLOYMENT_ROLLBACK | `NOT_EXECUTED` |
| 6 | CREDENTIAL_REVOKE | `BLOCKED_EXTERNAL_DEPENDENCY` |
| 7 | NETWORK_ROUTE_REVOKE | `BLOCKED_EXTERNAL_DEPENDENCY` |

No external control-plane result is represented as PASS.
