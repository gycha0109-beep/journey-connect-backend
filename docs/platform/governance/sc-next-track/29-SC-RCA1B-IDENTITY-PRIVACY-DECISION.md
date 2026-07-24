# SC RCA-1B Identity and Privacy Decision

## Scope

Fix the identity mode and privacy boundary for database reconciliation. No mapping store or implementation is authorized.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 completed with `IDENTITY_MODE=SYNTHETIC_ONLY`.

## Decision

- Identity A — synthetic only: `APPROVED`.
- Identity B — pseudonymized non-production test binding: `DEFERRED`.
- Identity C — actual user identity mapping: `BLOCKED`.

```text
IDENTITY_MODE=SYNTHETIC_ONLY
IDENTITY_MAPPING_OWNER=NOT_REQUIRED_FOR_SYNTHETIC_ONLY
IDENTITY_MAPPING_AUTHORITY=NONE
IDENTITY_MAPPING_STORAGE=EPHEMERAL_TEST_FIXTURE_ONLY
IDENTITY_MAPPING_RETENTION=EXECUTION_LIFETIME_ONLY
IDENTITY_MAPPING_DELETION=CONTAINER_DESTRUCTION
IDENTITY_MAPPING_INVALIDATION=FIXTURE_VERSION_INVALIDATION
IDENTITY_MAPPING_AUDIT=HASHED_CASE_AND_CLASSIFICATION_ONLY
IDENTITY_MAPPING_PURPOSE_BINDING=RCA1B_NONPRODUCTION_RECONCILIATION_ONLY
IDENTITY_MAPPING_LOGGING_POLICY=NO_RAW_ID_OR_MAPPING_PAIR
IDENTITY_MAPPING_FAILURE_POLICY=FAIL_CLOSED
```

## Rationale

RCA-1B needs database semantics, not actual identity governance. Synthetic rows are sufficient to test joins and fail-closed behavior.

## Authority

Privacy/Security approves identity and evidence boundaries; SC controls scope; lane owners approve semantic bindings.

## Dependencies

Deterministic synthetic subject/user/session/run/exposure references and purpose/caller markers.

## Execution Environment

No network or data path to actual identity systems.

## DB Access Boundary

Actual mapping tables, user directories, credentials and identity functions are not granted to the reconciliation role.

## Query Boundary

No raw identity lookup, inference, anonymous fallback, nearest-user fallback, alternate-subject fallback or unauthorized-caller fallback.

## Identity/Privacy

Absent, invalid, expired, deleted, mismatched, unauthorized-purpose and unauthorized-caller cases abort the case or execution according to lane policy. Raw user, subject, session, run and exposure identifiers are redacted before evidence creation.

## Evidence

Use hashed case IDs and categorical safe statuses only. No mapping pairs or reversible identifiers.

## DB/SQL Impact

No identity table, view, role or migration.

## Production Impact

None.

## Verification

SC-4 verifies policy completeness. Actual identity mapping and database permission tests are `NOT_EXECUTED`.

## Risks

Synthetic identity cannot prove deletion propagation or production mapping safety; those remain blockers for runtime phases.

## Exit Criteria

Every identity-negative case fails closed and no raw/reversible identifier appears in query evidence or logs.

## Handoff

Pseudonymized or actual identity use requires separate owner, storage, encryption, retention, deletion, invalidation, audit and purpose-binding approval.