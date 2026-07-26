# Credential Boundary

## Purpose

Define short-lived least-privilege credential requirements.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
CREDENTIAL_OWNER=OPERATIONS
CREDENTIAL_STATUS=NOT_ISSUED
CREDENTIAL_TYPE=SHORT_LIVED_NONPRODUCTION_WORKLOAD_IDENTITY
MAX_CREDENTIAL_TTL_SECONDS=3600
SECRET_STORAGE=PLATFORM_SECRET_MANAGER
PRODUCTION_CREDENTIAL=FORBIDDEN
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

Issuance, validation and revocation drilling are separate Operations and Privacy/Security work.

## Protection

Feature flag remains `OFF`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden.

## Verification

Actual credential issuance, authentication and infrastructure revocation are `NOT_EXECUTED`.

## Handoff

Operations must provide a separate exact-head credential preparation and rollback drill.
