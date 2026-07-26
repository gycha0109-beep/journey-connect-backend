# Identity Allowlist Boundary

## Purpose

Define test-account identity governance.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
IDENTITY_ALLOWLIST_OWNER=PRIVACY_SECURITY
ALLOWLIST_STATUS=EMPTY
IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY
COHORT_KEY=HASHED_NONPRODUCTION_TEST_SUBJECT_REF
RAW_IDENTITY_COHORT_KEY=FORBIDDEN
RAW_IDENTITY_RETENTION=NONE
PRODUCTION_IDENTITY=FORBIDDEN
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

The allowlist must be purpose-bound, encrypted, expiring, auditable and revocable. No inferred identity fallback is allowed.

## Protection

Feature flag remains `OFF`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden.

## Verification

Actual allowlist registration and identity runtime use are `NOT_EXECUTED`.

## Handoff

Privacy/Security must approve and separately verify the allowlist before any enablement decision.
