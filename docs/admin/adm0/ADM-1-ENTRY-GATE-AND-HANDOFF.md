# ADM-1 Entry Gate and Handoff

## Entry gate

All conditions are contract-defined but approval remains pending.

```text
ADMIN_MVP_SCOPE_APPROVED=PENDING_USER_APPROVAL
ROLE_PERMISSION_MODEL_DEFINED=YES
CURRENT_SCHEMA_INVENTORIED=YES
SCHEMA_GAPS_DEFINED=YES
MIGRATION_PLAN_DEFINED=YES
ADMIN_API_CONTRACT_DEFINED=YES
MODERATION_STATE_MACHINE_DEFINED=YES
AUDIT_CONTRACT_DEFINED=YES
UI_REUSE_MATRIX_DEFINED=YES
BACKEND_REPOSITORY_AUTHORITY_DEFINED=YES
FRONTEND_SOURCE_AUTHORITY_DEFINED=YES
FINAL_SYNC_METHOD_DEFINED=YES
ADM1_ENTRY=BLOCKED_PENDING_USER_APPROVAL
```

## ADM-1 handoff

### Objective

Implement the minimum database and security foundation that connects the existing canonical Admin SQL capability to an authorised Java application boundary.

### Required implementation

- Re-verify `main`, ADM-0 merge SHA and canonical DB sequence.
- Obtain System Coordination migration/SQL sequence allocation after `26`.
- Add `DatabaseRole.ADMIN` and role-routing/startup membership verification.
- Add DB-authoritative active staff principal resolution.
- Protect `/api/v1/admin/**` with default deny and permission mapping.
- Add/extend migration for version/CAS, request/operation identity and privacy-safe audit fields only if validated gaps remain.
- Map existing `reports`, `admin_actions`, post moderation and account status; do not duplicate them.
- Add test-only moderator/admin fixtures without production credentials.
- Add role/permission, 401/403, grant, append-only, redaction and concurrency tests.

### Forbidden

- Admin API business queries/commands beyond foundation adapters.
- Frontend changes.
- Direct `youngtak` mutation.
- Permanent-delete endpoint.
- User content editing.
- Production Admin seed/default password.
- Existing migration modification.
- Unallocated SQL version.
- Audit payload dump.

### Completion evidence

- exact head and changed-file manifest;
- PostgreSQL migration + security smoke;
- Hibernate schema validation;
- Java security integration tests;
- existing backend and protected recommendation regressions;
- no frontend or cloud change;
- Draft PR kept pending user approval.
