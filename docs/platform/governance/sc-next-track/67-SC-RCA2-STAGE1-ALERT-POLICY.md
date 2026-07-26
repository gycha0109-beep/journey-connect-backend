# Alert Policy

## Purpose

Define notification ownership and route prerequisites.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
ALERT_OWNER=OPERATIONS
CRITICAL_NOTIFICATION_ROUTE=NOT_VERIFIED
CRITICAL_ALERT_DELIVERY=BLOCKING_PREREQUISITE
P1_P2_ALERTS=LANE_SEPARATED
ZERO_TOLERANCE_ALERTS=IMMEDIATE
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

Critical conditions require immediate Operations notification and global disable. Dashboard and notification routing must be proved before enablement.

## Protection

Feature flag remains `OFF`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden.

## Verification

Dashboard, critical notification route and alert delivery are `NOT_EXECUTED`.

## Handoff

Operations must implement and verify alerts in a separate preparation change.
