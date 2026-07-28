# ADM-5 Delivery Checklist

## Build and integration

- [ ] Exact `main` and ADM-4 merge commit recorded.
- [ ] PostgreSQL 15 canonical database starts from reviewed SQL.
- [ ] Restricted backend login passes role-routing verification and has `jc_admin` membership.
- [ ] Backend and production-built frontend run simultaneously.
- [ ] Administrator login obtains a newly issued admin-role JWT.
- [ ] Admin endpoints are covered `13/13`; commands are covered `6/6`.
- [ ] Authentication, error, idempotency, stale-state, responsive, accessibility and SPA matrices pass.
- [ ] Frontend tests, lint, build and ADM-4/ADM-5 verifiers pass.
- [ ] ADM-1, ADM-2, ADM-3 verifiers and inherited PostgreSQL Admin tests pass.
- [ ] Browser evidence includes Chromium viewports `1280x900`, `768x1024`, `390x844`.
- [ ] No backend runtime, SQL, schema, new feature or new endpoint change exists.

## Demo and recovery

- [ ] Demo administrator and normal/suspendable users are local/non-production only.
- [ ] Visible post and multiple pending report fixtures exist.
- [ ] Hidden post and suspended user are restored after demo.
- [ ] Terminal reports are not reset; a new local fixture is prepared.
- [ ] No production DB reset, password, token or secret is stored.

## Operational acceptance classification

| Area | Status |
|---|---|
| ADMIN_PROVISIONING | VERIFIED |
| ADMIN_ACCOUNT_RECOVERY | PENDING_OWNER_ASSIGNMENT |
| ALL_ADMIN_LOCKOUT_RECOVERY | PENDING_DEPLOYMENT_DECISION |
| AUDIT_RETENTION | PENDING_DEPLOYMENT_DECISION |
| DATABASE_BACKUP | PENDING_DEPLOYMENT_DECISION |
| DATABASE_RESTORE | PENDING_DEPLOYMENT_DECISION |
| INCIDENT_OWNER | PENDING_OWNER_ASSIGNMENT |
| DEPLOYMENT_OWNER | PENDING_OWNER_ASSIGNMENT |
| SECRET_MANAGEMENT | PENDING_DEPLOYMENT_DECISION |
| CORS_PRODUCTION_ORIGIN | PENDING_DEPLOYMENT_DECISION |
| TLS_TERMINATION | PENDING_DEPLOYMENT_DECISION |
| RATE_LIMIT_OR_GATEWAY | PENDING_DEPLOYMENT_DECISION |

## Git delivery gate

- [ ] Draft PR body matches final exact-head evidence.
- [ ] `main` drift and open-PR direct file conflicts are rechecked.
- [ ] PR remains Draft.
- [ ] Ready transition, merge and auto-merge remain disabled pending explicit user approval.
