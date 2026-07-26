# OP-3 Execution Handoff

OP-3 may start only after every condition in `op3-entry-gate.json` is true and bound to the same deployment/version, rule digest and approval artifact.

Current handoff state is blocked. Required evidence includes external metric queries, deployed dashboard, critical route acknowledgement, deployment/credential/network rollback drills, endpoint/credential/allowlist/candidate readiness, six approvals and manual enablement approval.

OP-3 must begin from effective traffic 0, perform a manual 1% change only, observe at least 30 minutes and 100 executions, and rollback on any critical condition or warning ceiling. It must not serve candidate results or alter authority.
