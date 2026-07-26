# OP-3 Execution Prompt

Execute `OP-3 RCA-2 Stage 1 Controlled Non-production Observation` only after independently verifying the latest `main`, the merged OP-2 exact head and artifact digest, all external readiness evidence, all six human approvals and manual enablement approval.

Before any traffic change, prove effective traffic is 0 and all rollback owners are available. Manually enable exactly 1% stable-hash non-production test traffic. Do not use production endpoint, identity, credential or route. Do not serve candidate results, mutate primary responses, write DB/cache, emit events/notifications, provide ranking feedback or transfer authority.

Observe the authoritative metrics for at least 30 minutes and 100 completed executions. Immediately execute the mapped rollback action on any critical zero-tolerance signal or SC-6 warning ceiling. Retain exact-head evidence and return traffic to 0 at exit.
