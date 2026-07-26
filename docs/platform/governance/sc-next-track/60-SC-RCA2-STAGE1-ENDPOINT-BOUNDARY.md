# Endpoint Boundary

## Purpose

Define ownership and prerequisites for an isolated non-production endpoint.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
ENDPOINT_OWNER=OPERATIONS
ENDPOINT_STATUS=NOT_IMPLEMENTED
ENDPOINT_ENVIRONMENT=ISOLATED_NON_PRODUCTION_ONLY
PRODUCTION_ENDPOINT=FORBIDDEN
PRODUCTION_ROUTE=FORBIDDEN
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

A concrete endpoint, host, route or deployment is outside SC-6. Operations must provide an environment-isolated endpoint, route proof and rollback evidence in a separate change.

## Protection

Feature flag remains `OFF`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden.

## Verification

Actual endpoint deployment, route verification and traffic are `NOT_EXECUTED`.

## Handoff

Operations preparation requires a separate Draft change and explicit approval.
