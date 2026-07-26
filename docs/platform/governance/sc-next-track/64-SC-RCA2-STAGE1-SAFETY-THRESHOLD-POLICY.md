# Safety Threshold Policy

## Purpose

Define Stage 1 safety ceilings and zero-tolerance controls.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
MAX_TIMEOUT_RATE_PERCENT=20
MAX_EXCEPTION_RATE_PERCENT=25
MAX_QUEUE_REJECTION_RATE_PERCENT=5
MAX_LATE_DISCARD_RATE_PERCENT=5
MAX_REDACTION_FAILURE_COUNT=0
MAX_RESPONSE_MUTATION_COUNT=0
MAX_DATABASE_WRITE_COUNT=0
MAX_EVENT_EMISSION_COUNT=0
MAX_PRODUCTION_ROUTE_DETECTION_COUNT=0
MAX_AUTHORITY_MISMATCH_COUNT=0
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

Thresholds are abort ceilings, not SLOs. P1 expected/protected gaps and P2 migration gaps are kept separate from unexpected mismatch. Thresholds may only be revised through a new exact-head governance decision.

## Protection

Feature flag remains `OFF`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden.

## Verification

Actual threshold observation and Stage 1 traffic are `NOT_EXECUTED`.

## Handoff

Metric implementation, dashboard validation and empirical threshold review require a separate Draft change.
