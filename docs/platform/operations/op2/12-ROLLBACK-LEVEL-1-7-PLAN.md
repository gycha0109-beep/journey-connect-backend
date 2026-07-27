# Rollback Level 1-7 Plan

1. `FLAG_OFF` — PASS
2. `LANE_KILL_SWITCH` — PASS
3. `GLOBAL_SHADOW_DISABLE` — PASS
4. `CONFIG_ROLLBACK` — procedure ready, not executed
5. `DEPLOYMENT_ROLLBACK` — contract ready, not executed
6. `CREDENTIAL_REVOKE` — blocked external dependency
7. `NETWORK_ROUTE_REVOKE` — blocked external dependency

Detailed trigger, owner, time limit, verification and escalation data is in `rollback-matrix.json`.
