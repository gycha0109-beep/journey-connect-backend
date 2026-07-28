# ADM-3 Admin API Hardening, Audit and Acceptance

## 1. Purpose

ADM-3 proves that the existing Dashboard, Reports, Posts and Users APIs remain safe under concurrency, transaction failure, stale authority, malformed input and privacy review. It adds no production endpoint and no Admin UI capability.

```text
ADMIN_MVP_FEATURE_EXPANSION=NO
BACKEND_HARDENING=STRONG
UI_COMPLEXITY=LOW
```

## 2. Authoritative baseline

```text
REMOTE_MAIN=e7dd0d11de9104e2be62f9ba886ddc20cfe27fad
ADM2_PR=49
ADM2_EXACT_HEAD=f9942bf51ad347274032aec5e46103dffc059ff7
ADM2_MERGE_COMMIT=e7dd0d11de9104e2be62f9ba886ddc20cfe27fad
ADM2_ADMIN_BASIC_APIS_IMPLEMENTED=YES
ADM2_SCOPE_COMPLIANT=YES
```

The endpoint inventory remains exactly 13 endpoints. No controller mapping is added or removed.

## 3. Cross-admin risk analysis

Before ADM-3, `admin_suspend_user` checked the actor and then locked only the target row. Two active admins could therefore both pass authorization and suspend one another in concurrent transactions. An application-local mutex would not protect multiple backend instances and would leave the privileged DB function callable without the protection.

### Selected lock strategy

A forward-only PostgreSQL migration replaces the existing privileged function bodies without changing tables or columns.

```text
CONTROL_PLANE_LOCK=pg_advisory_xact_lock(1245789,3)
LOCK_SCOPE=TRANSACTION
ACTOR_TARGET_ROW_LOCK=DETERMINISTIC_ID_ORDER
ACTOR_DB_STATE_RECHECK=AFTER_ADVISORY_LOCK
LAST_ACTIVE_ADMIN_CHECK=SAME_TRANSACTION
```

The same transaction-scoped advisory lock is used by all existing runtime paths that can remove an active admin from the control plane:

- `admin_suspend_user`
- `admin_withdraw_user`
- `admin_change_user_role` when demoting an active admin

The actor and target rows are locked in ascending ID order before `require_staff_actor` is evaluated again. The target transition is rejected when it would remove the final active admin. This protects against cross-instance mutual suspension and also closes the equivalent withdraw/demotion bypass without exposing role management through an API.

## 4. SQL migration decision

```text
SQL_CHANGE=53_admin_control_plane_hardening.sql,54_admin_control_plane_hardening_smoke_test.sql
DB_SCHEMA_CHANGE=FUNCTION_HARDENING_AND_COMMAND_ADAPTERS
TABLE_CHANGE=NONE
COLUMN_CHANGE=NONE
DATA_MIGRATION=NONE
```

Existing migration files are unchanged. The migration is forward-only because the original functions were already deployed. Function ownership remains `jc_security_owner`; runtime execution remains restricted to `jc_admin`. Clean bootstrap and 1-28 to 29-30 upgrade paths are independently executed in CI.

## 5. Failure injection

Failure tests use only isolated test-container objects or test-only Spring beans:

- a temporary `admin_actions` trigger forces audit insertion failure;
- a temporary target-table trigger forces mutation failure before audit insertion;
- a test-only `@DatabaseTransactional(DatabaseRole.ADMIN)` probe performs a real privileged mutation and throws before commit;
- all test triggers and functions are removed after each test and never appear in production SQL.

Expected invariant:

```text
AUDIT_FAILURE_ROLLS_BACK_MUTATION=YES
MUTATION_FAILURE_CREATES_NO_AUDIT=YES
PARTIAL_COMMIT=NO
DUPLICATE_AUDIT_ON_RETRY=NO
```

## 6. Concurrency results and policy

Reports, posts and users retain ADM-2 state-aware idempotency. PostgreSQL row locks are coordinated in tests with explicit transactions rather than timing sleeps.

| Target | Concurrent commands | Result |
|---|---|---|
| Report | resolve / resolve | one mutation, one no-op, one audit |
| Report | resolve / dismiss | one terminal success, one 409, one audit |
| Post | hide / hide | one mutation, one no-op, one audit |
| Post | hide / restore | valid visible/hidden state only; no physical delete |
| User | suspend / suspend | one mutation, one no-op, one audit |
| User | suspend / unsuspend | valid active/suspended state only |
| Admin | A suspends B / B suspends A | one success, one conflict, at least one active admin remains |

A blocked actor is re-read from `app_users` after the control-plane advisory lock is acquired. An actor suspended while waiting cannot commit the target command.

## 7. Audit completeness

All six exposed mutations continue to call existing audited `SECURITY DEFINER` functions. Application code does not directly update moderation/account state and does not insert `admin_actions` rows.

```text
AUDIT_ACTOR_DB_AUTHORITATIVE=YES
AUDIT_REASON_REQUIRED=YES
AUDIT_ACTION_TYPE_CANONICAL=YES
AUDIT_TARGET_TYPE_CANONICAL=YES
AUDIT_TARGET_ID_PRESENT=YES
AUDIT_BEFORE_STATE_CAPTURED=YES
AUDIT_RESULTING_STATE_DERIVABLE=YES
AUDIT_CREATED_AT_DB_GENERATED=YES
AUDIT_AND_MUTATION_ATOMIC=YES
```

Resulting state is derived deterministically from canonical action type:

- `report_resolve` -> `resolved`
- `report_reject` -> `rejected`
- `post_hide` -> `hidden`
- `post_restore` -> `visible`
- `user_suspend` -> `suspended`
- `user_restore` -> `active`

No after-state column is added.

## 8. Audit privacy

Existing DB functions build explicit JSON projections; they do not serialize complete entities. User snapshots contain only ID, username, role and account status. Post snapshots contain ID, title, author/region IDs and lifecycle/moderation state. Report snapshots contain report/target IDs and previous state.

Tests reject or scan for:

```text
password_hash
refresh_token
access_token
authorization_header
cookie
oauth_secret
database_password
full_jwt_claims
raw_stack_trace
raw_sql_error
```

Command reasons are NFKC-normalized, bounded to 1,000 characters, single-line/control-character-free and rejected when they resemble Authorization, Bearer token, access/refresh token, password or cookie material.

## 9. API response privacy

Every list/detail/dashboard DTO is asserted against an exact JSON field allowlist. JPA entities are not returned. List and detail surfaces remain task-specific; internal DB function names, DB roles, SQL errors, JWT claims, infrastructure metadata and stack traces are absent.

Email remains present only in the existing Users DTO contract because the Admin MVP explicitly supports email/login lookup. No authentication or OAuth material is returned.

## 10. Error contract

Existing codes remain authoritative:

```text
401 AUTHENTICATION_REQUIRED
403 ADMIN_ACCESS_DENIED
404 ADMIN_TARGET_NOT_FOUND
409 ADMIN_STATE_CONFLICT
400 INVALID_ADMIN_COMMAND
```

Malformed Admin request bodies/path variables are normalized to `INVALID_ADMIN_COMMAND`. Unexpected `DataAccessException` from an Admin controller is normalized to generic `500 ADMIN_OPERATION_FAILED`; SQLSTATE, relation, column, function, role and stack information are not returned.

Authorization executes before controller/query processing, preventing unauthorized target probing.

## 11. Input hardening

```text
DEFAULT_PAGE_SIZE=20
MAX_PAGE_SIZE=100
MAX_SEARCH_LENGTH=100
MAX_REASON_LENGTH=1000
UNBOUNDED_QUERY=NO
CLIENT_DYNAMIC_SORT=NO
```

Additional rules:

- negative page, zero/oversized size and overflowed offset are rejected;
- search/filter/reason text is NFKC-normalized;
- control characters and NUL/CRLF are rejected;
- unsupported or duplicate query parameters are rejected by an Admin-only MVC interceptor;
- path IDs must be positive and malformed/out-of-range numbers produce generic 400;
- enum filters are case-insensitive after normalization but remain allowlisted;
- SQL values remain parameter-bound and no client SQL identifier is interpolated.

## 12. Abuse review

No Redis, gateway or external rate limiter is introduced. Application-level controls are bounded pages, fixed ordering, 100-character search, minimal DTOs, idempotent commands and 1,000-character reasons. Dashboard/repeated-list request rate limiting and global enumeration protection remain deployment gateway/WAF conditions.

## 13. Acceptance flow

The PostgreSQL-backed HTTP acceptance test performs:

1. active Admin token preparation;
2. dashboard query;
3. pending report list and detail;
4. target post detail and hide;
5. report resolve;
6. target user detail and suspend;
7. dashboard aggregate recheck;
8. exact audit verification;
9. same-command retries with `changed=false` and no duplicate audit;
10. post restore and user unsuspend;
11. final state and physical-retention verification.

Normal users cannot enter the flow. An Admin suspended mid-flow immediately receives `ADMIN_ACCESS_DENIED` with the existing token.

## 14. Security regression

ADM-1 and ADM-2 workflows remain exact-head gates. ADM-3 adds moderator, withdrawn-admin, promotion-token-refresh, demotion and suspension regressions while preserving ordinary user routes.

## 15. Scope exclusions

```text
PHYSICAL_DELETE_IMPLEMENTED=NO
ROLE_MANAGEMENT_IMPLEMENTED=NO
ADMIN_APPOINTMENT_IMPLEMENTED=NO
FRONTEND_SOURCE_CHANGE=NO
YOUNGTAK_SOURCE_CHANGE=NO
ADMIN_UI_PORT_EXECUTED=NO
ADMIN_MVP_FEATURE_EXPANSION=NO
```

## 16. Residual risks

- Distributed request-rate enforcement remains a deployment gateway/WAF decision.
- Human owner/contact and recovery authority are not yet assigned.
- The advisory lock key is a documented internal contract; future privileged functions that can remove active Admin authority must reuse it.
- Existing `suspendedAt` remains inferred from `updated_at`; no schema expansion is introduced in ADM-3.

## 17. Result

```text
CROSS_ADMIN_TOTAL_LOCKOUT_PROTECTED=YES
AUDIT_FAILURE_ROLLS_BACK_MUTATION=YES
MUTATION_FAILURE_CREATES_NO_AUDIT=YES
AUDIT_PRIVACY_VERIFIED=YES
API_RESPONSE_PRIVACY_VERIFIED=YES
ADMIN_MVP_ACCEPTANCE_FLOW=PASS
ADM3_ADMIN_API_HARDENING_COMPLETE=YES
ADM3_ACCEPTANCE_COMPLETE=YES
ADM4_ENTRY=BLOCKED_PENDING_USER_APPROVAL
```
