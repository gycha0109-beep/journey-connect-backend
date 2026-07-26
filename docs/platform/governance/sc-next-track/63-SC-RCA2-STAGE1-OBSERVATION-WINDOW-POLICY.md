# Observation Window Policy

## Purpose

Define minimum evidence before any Stage 1 exit consideration.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
MIN_OBSERVATION_DURATION_MINUTES=30
MIN_SHADOW_EXECUTION_COUNT=100
BOTH_CONDITIONS_REQUIRED=YES
OBSERVATION_STATUS=NOT_EXECUTED
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

The clock starts only after approved manual enablement. Paused, blocked, rejected or pre-enable executions do not satisfy the contract.

## Protection

Feature flag remains `OFF`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden.

## Verification

Runtime observation and actual Stage 1 execution are `NOT_EXECUTED`.

## Handoff

Observation begins only after every blocker closes and a separate manual enablement decision is approved.
