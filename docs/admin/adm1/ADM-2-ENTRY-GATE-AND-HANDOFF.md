# ADM-2 Entry Gate and Handoff

## Gate

```text
ADM1_DATABASE_BASELINE_VERIFIED=YES
ADM1_SECURITY_FOUNDATION_IMPLEMENTED=YES
ADMIN_ROUTE_PROTECTION_VERIFIED=YES
DB_AUTHORITATIVE_GUARD_VERIFIED=YES
JC_ADMIN_ROLE_ROUTING_VERIFIED=YES
SQL_CHANGE=NONE
FRONTEND_SOURCE_CHANGE=NO
ADM2_ENTRY=BLOCKED_PENDING_USER_APPROVAL
```

ADM-2 may begin only after PR #48 is explicitly approved and merged, actual `main` is re-read, all required checks remain green, and no overlapping Admin runtime branch has appeared.

## ADM-2 scope

Implement the basic backend APIs required by the low-complexity dashboard surface:

- Reports list/detail and basic resolve/dismiss commands;
- Posts list/detail and hide/restore commands;
- Users list/detail and suspend/unsuspend commands;
- basic dashboard aggregate and recent-item queries.

Every entry point must:

1. use `/api/admin/**`;
2. call `AdminAuthorizationGuard.requireActiveAdmin()`;
3. use `DatabaseRole.ADMIN`;
4. use server pagination and bounded allowlisted filters;
5. execute mutations through the existing audited security-definer functions;
6. expose stable DTOs and generic 400/401/403/404/409 errors;
7. avoid role assignment, physical deletion, raw audit payloads and complex assignment/review workflows.

## Preserved exclusions

No frontend work, Youngtak synchronization, role appointment, IAM UI, audit raw-data UI, infrastructure console, bulk moderation, arbitrary SQL or physical deletion is authorised by this handoff.
