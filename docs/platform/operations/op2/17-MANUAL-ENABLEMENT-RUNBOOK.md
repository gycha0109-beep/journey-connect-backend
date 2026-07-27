# Manual Enablement Runbook

1. Verify exact deployment/version.
2. Verify endpoint, credential and allowlist.
3. Verify candidate source/version.
4. Verify metrics, dashboard and critical route.
5. Verify rollback owner and role approvals.
6. Verify effective traffic is `0%`.
7. Confirm manual 1% approval.
8. Execute, inspect, observe 30 minutes/100 requests, then exit or rollback.

The runbook is user-approved, but execution remains blocked. OP-2 does not run steps after the zero-traffic checkpoint.
