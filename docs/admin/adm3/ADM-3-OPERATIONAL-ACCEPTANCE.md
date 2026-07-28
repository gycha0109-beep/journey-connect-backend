# ADM-3 Operational Acceptance Conditions

This register defines conditions required before the simple Admin UI is connected. It does not provision infrastructure or assign unknown people.

| Condition | Status | Evidence or decision required |
|---|---|---|
| `ADMIN_USER_PROVISIONING_METHOD` | `IMPLEMENTED` | Existing controlled DB role update path; no public Admin creation/appointment API. Actual operator procedure must use approved database administration. |
| `ADMIN_LOGIN_AND_TOKEN_REFRESH_REQUIREMENT` | `VERIFIED` | Admin uses normal authentication. Promotion requires a newly issued token; demotion/suspension fails immediately through DB-authoritative guard. |
| `JC_ADMIN_DB_MEMBERSHIP_REQUIREMENT` | `VERIFIED` | Backend login is `NOINHERIT` member of `jc_admin`; `DatabaseRoleCapabilityVerifier` checks capability at startup. |
| `BACKEND_STARTUP_CAPABILITY_VERIFICATION` | `IMPLEMENTED` | Existing ADM-1 startup verifier checks restricted role assumption. |
| `AUDIT_RETENTION_POLICY_REFERENCE` | `VERIFIED` | `admin_actions` is append-only; retention remains governed by the existing DB/operations policy. No ADM-3 purge endpoint exists. |
| `ADMIN_ACCOUNT_RECOVERY_PROCEDURE` | `PENDING_DEPLOYMENT_DECISION` | Define identity verification and approved database operator procedure for restoring a suspended Admin. |
| `ALL_ADMIN_LOCKOUT_RECOVERY_PROCEDURE` | `PENDING_DEPLOYMENT_DECISION` | Runtime lockout is prevented by DB functions. Break-glass database-owner procedure and credential storage location remain deployment decisions. |
| `ROLLBACK_PROCEDURE` | `VERIFIED` | Application rollback reverts ADM-3 Java/UI-independent changes; DB security fix is forward-only and should be corrected by a new replacement migration rather than reverting to the vulnerable function. |
| `INCIDENT_CONTACT_OWNER` | `PENDING_OWNER_ASSIGNMENT` | Named operational/security owner is not yet assigned. |
| `GATEWAY_WAF_RATE_POLICY` | `PENDING_DEPLOYMENT_DECISION` | Set Admin endpoint request-rate and enumeration controls when the deployment gateway is selected. |
| `ADMIN_UI_OWNER` | `IMPLEMENTED` | Final owner is `gycha0109-beep`; Youngtak source remains initial-draft-only with no ongoing sync. |

UI integration must not begin until the pending owner/deployment decisions are explicitly accepted or documented as deferred by the user.
