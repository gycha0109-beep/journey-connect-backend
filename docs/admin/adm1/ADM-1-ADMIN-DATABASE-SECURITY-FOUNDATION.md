# ADM-1 Admin Database and Security Foundation

## 1. Result

```text
ADM1_DATABASE_BASELINE_VERIFIED=YES
ADM1_SECURITY_FOUNDATION_IMPLEMENTED=YES
ADMIN_ROLE_SOURCE=APP_USERS_ROLE
ADMIN_ACCOUNT_STATUS_SOURCE=APP_USERS_ACCOUNT_STATUS
ADMIN_AUTHORIZATION_SOURCE=DB_AUTHORITATIVE
ADMIN_API_PREFIX=/api/admin
ADMIN_ROUTE_AUTHENTICATION_REQUIRED=YES
ADMIN_ROUTE_ADMIN_ROLE_REQUIRED=YES
SUSPENDED_ADMIN_BLOCKED=YES
NORMAL_USER_BLOCKED=YES
SQL_CHANGE=NONE
DB_SCHEMA_CHANGE=NONE
```

This stage implements only the backend authorization foundation. No Admin dashboard, report, post, user-management production controller or frontend source is added.

## 2. Authoritative baseline

| Item | Verified value |
|---|---|
| Repository | `gycha0109-beep/journey-connect-backend` |
| Work-start `main` | `a2b9e3d8e79df3dcf9d75b418011b3a8cca754b1` |
| ADM-0 PR | `#47`, merged |
| ADM-0 exact head | `7315f6ec77172b05dfc1ef6b0dc1ba16b647b148` |
| ADM-0 merge commit | `a2b9e3d8e79df3dcf9d75b418011b3a8cca754b1` |
| ADM-1 branch | `agent/adm1-admin-database-security-foundation` |

Open PRs at entry were historical OP/IP verification work and did not modify this branch. The branch name did not exist before ADM-1 entry.

## 3. Existing source-of-truth inventory

| Area | Existing object | ADM-1 decision |
|---|---|---|
| User role | `app_users.role`: `user/moderator/admin` | `REUSE` |
| Account state | `app_users.account_status`: `active/suspended/withdrawn` | `REUSE` |
| Reports | `reports` and existing state/handler evidence | `REUSE` |
| Post moderation | `posts.moderation_status`: `visible/hidden` | `REUSE` |
| Deletion retention | post lifecycle `deleted_at/purge_after`; comment deletion columns | `REUSE` |
| Audit | append-only `admin_actions` | `REUSE` |
| DB Admin role | `jc_admin`, read-only data plus controlled function execution | `REUSE` |
| Privileged commands | existing `SECURITY DEFINER` functions | `REUSE_LATER_IN_ADM2` |
| Java DB routing | APP/AUTH/RECOMMENDATION only | `EXTEND_WITH_ADMIN` |
| JWT role claim | absent before ADM-1 | `ADD_NON_AUTHORITATIVE_HINT` |
| Admin guard | absent | `CREATE` |

Canonical SQL currently continues beyond the backend test bootstrap sequence. Admin authority objects are established by protected SQL `04_admin_support.sql` and `05_security_roles.sql`; ADM-1 does not alter either sequence. The canonical PostgreSQL integration profile applies its reviewed `01..28` fixture set. H2 is not an active Gradle test dependency or Admin security source of truth.

## 4. Authorization contract

An Admin request succeeds only when all conditions hold:

```text
authenticated JWT
AND positive numeric JWT subject
AND request-bound verified subject matches the JWT subject
AND token role claim is admin
AND app_users row exists for the subject
AND app_users.role = admin
AND app_users.account_status = active
```

The JWT role claim is used as an early route authority and stale-token fail-closed signal. It is never the final authority. Current role and account status are always read from PostgreSQL inside `DatabaseRole.ADMIN`.

Consequences:

- role demotion invalidates an existing Admin token immediately at the DB guard;
- suspension or withdrawal invalidates an existing token immediately;
- a token issued while the user role was `user` does not gain Admin access after DB promotion; reauthentication or refresh is required;
- tokens issued before ADM-1 without a role claim cannot access `/api/admin/**`;
- missing users and all non-active/non-Admin states return the same `ADMIN_ACCESS_DENIED` 403 without disclosing which check failed.

## 5. Route and service boundaries

`SecurityConfig` protects both `/api/admin` and `/api/admin/**` with `ROLE_ADMIN`. The JWT converter maps only recognised role claims to `ROLE_USER`, `ROLE_MODERATOR` or `ROLE_ADMIN`.

`AdminAuthorizationGuard.requireActiveAdmin()` is the reusable service boundary. It:

1. resolves the authenticated `JwtAuthenticationToken`;
2. validates the numeric subject;
3. verifies the request identity installed by `DatabaseRequestIdentityFilter`;
4. validates the token Admin role;
5. enters a read-only `jc_admin` transaction;
6. loads `id/username/role/account_status` directly from `app_users`;
7. returns a minimal `AdminActor` only for an active Admin.

Future ADM-2 services must use an Admin transaction and call this guard at the command/query entry point. Repository access alone is not an authorization boundary.

## 6. Database role hardening

`DatabaseRole.ADMIN` maps to `jc_admin`. Startup capability verification now includes this role in the allowed restricted-login membership set and verifies the login can assume it.

Existing database guarantees remain intact:

- `jc_admin` cannot read `password_hash`;
- `jc_admin` cannot directly update role, status, reports, audit, posts or comments;
- physical content deletion is not granted;
- Admin mutations remain available only through audited `SECURITY DEFINER` functions;
- request identity remains transaction-local through `jc.current_user_id`.

## 7. Audit foundation

No audit table or column is added. Future commands map the requested logical contract to existing structures:

| Logical field | Existing source |
|---|---|
| actor Admin ID | `admin_actions.actor_user_id` |
| action type | `admin_actions.action_type` |
| target type/ID | `target_type/target_entity_id` |
| reason | `reason` |
| before state | allowlisted `target_snapshot` and/or `metadata` |
| after state | allowlisted `metadata` plus resulting row state |
| request timestamp | `created_at` |

The existing audited functions write the state change and `admin_actions` row in one transaction. Raw tokens, passwords, request bodies, private content, raw IP and full headers remain forbidden audit material. Request ID, user-agent and IP correlation are not added in ADM-1 because no stable collection and retention contract currently exists.

## 8. Migration decision

```text
SQL_CHANGE=NONE
DB_SCHEMA_CHANGE=NONE
NEW_FLYWAY_MIGRATION=NO
```

Rationale:

- all required authority/status/audit columns and constraints already exist;
- `jc_admin` already has the required least-privilege reads;
- privileged functions and append-only audit already exist;
- the missing pieces are application routing, JWT authority conversion and DB-authoritative guard logic;
- adding a migration would duplicate or unnecessarily expand the reviewed schema.

## 9. Changed files

Runtime:

- `jc-backend/src/main/java/com/jc/backend/admin/security/AdminActor.java`
- `jc-backend/src/main/java/com/jc/backend/admin/security/AdminAuthorizationGuard.java`
- `jc-backend/src/main/java/com/jc/backend/auth/AuthAccount.java`
- `jc-backend/src/main/java/com/jc/backend/auth/AuthService.java`
- `jc-backend/src/main/java/com/jc/backend/database/DatabaseRole.java`
- `jc-backend/src/main/java/com/jc/backend/database/DatabaseRoleCapabilityVerifier.java`
- `jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt`
- `database/journey-connect-db-v2.7/README.md`
- `database/journey-connect-db-v2.7/README_P0_3.md`

Tests and governance:

- Admin security integration tests
- DB role-routing regression additions
- ADM-1 documentation, machine-readable contract, verifier and exact-head CI

## 10. Security review

| Threat | Control |
|---|---|
| forged role claim | JWT signature validation plus DB role re-read |
| stale Admin token after demotion | DB role mismatch returns 403 |
| stale token after suspension | DB status mismatch returns 403 |
| normal user token | route authority returns 403 |
| missing DB subject | guard returns generic 403 |
| service invocation without request filter | request-identity mismatch returns 403 |
| direct repository mutation | `jc_admin` has no direct mutation grant |
| mass assignment | no Admin write DTO or role/status endpoint exists |
| physical deletion | no endpoint and no DB grant added |
| audit/state divergence | future mutations remain audited DB functions in one transaction |
| sensitive logging | generic errors; no token/body/audit payload logging added |
| CORS/CSRF regression | existing explicit-origin bearer-token policy retained |

## 11. Test contract

The test suite covers anonymous, user, active Admin, suspended/withdrawn Admin, missing DB user, both token/DB role mismatch directions, all Admin paths, non-Admin/public route regression, direct guard invocation, request subject mismatch, JWT role issuance, Admin DB read boundaries and startup capability verification.

All security integration tests use the canonical PostgreSQL/Testcontainers profile. No H2-only result is accepted as Admin authorization evidence.

## 12. UI and repository boundary

```text
ADMIN_UI_SOURCE_USAGE=INITIAL_DRAFT_ONLY
FINAL_UI_OWNER=gycha0109-beep
ONGOING_SOURCE_SYNC=NO
FULL_SOURCE_BRANCH_MERGE=FORBIDDEN
SELECTIVE_INITIAL_PORT=YES
FRONTEND_SOURCE_CHANGE=NO
YOUNGTAK_SOURCE_CHANGE=NO
ADMIN_UI_PORT_EXECUTED=NO
ADMIN_MVP_SURFACE=DASHBOARD_BASIC
BACKEND_HARDENING=STRONG
UI_COMPLEXITY=LOW
```

## 13. Residual risks

1. A role promotion requires token refresh or re-login because the old token intentionally remains non-Admin.
2. The runtime DB login must be externally provisioned as `NOINHERIT` and a member of `jc_admin`; the current v2.7 runtime-login documentation now records this requirement and startup verification fails closed otherwise.
3. ADM-2 must call the guard inside every Admin query/command entry point and use existing audited functions for mutations.
4. Audit request/correlation metadata remains deferred until a privacy, retention and trusted-header contract exists.
5. The broader canonical SQL package and backend test bootstrap have different sequence endpoints, but the protected Admin objects used here are identical and unchanged.
