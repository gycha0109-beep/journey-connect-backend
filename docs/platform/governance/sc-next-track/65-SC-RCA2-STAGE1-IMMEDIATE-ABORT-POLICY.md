# Immediate Abort Policy

## Purpose

Define deterministic abort triggers.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

- `ABORT-001`: `redaction_failure_count > 0` → `GLOBAL_SHADOW_DISABLE`.
- `ABORT-002`: `response_mutation_count > 0` → `GLOBAL_SHADOW_DISABLE`.
- `ABORT-003`: `database_write_count > 0` → `GLOBAL_SHADOW_DISABLE`.
- `ABORT-004`: `event_emission_count > 0` → `GLOBAL_SHADOW_DISABLE`.
- `ABORT-005`: `production_route_detection_count > 0` → `NETWORK_ROUTE_REVOKE`.
- `ABORT-006`: `authority_mismatch_count > 0` → `GLOBAL_SHADOW_DISABLE`.
- `ABORT-007`: `timeout_rate_percent > 20` → `LANE_KILL_SWITCH`.
- `ABORT-008`: `exception_rate_percent > 25` → `LANE_KILL_SWITCH`.
- `ABORT-009`: `queue_rejection_rate_percent > 5` → `LANE_KILL_SWITCH`.
- `ABORT-010`: `late_discard_rate_percent > 5` → `LANE_KILL_SWITCH`.
- `ABORT-011`: raw identity used as cohort key → `GLOBAL_SHADOW_DISABLE`.
- `ABORT-012`: feature flag not explicitly and manually enabled → `FLAG_OFF`.
- `ABORT-013`: any blocking approval missing → `FLAG_OFF`.
- `ABORT-014`: observation contract not satisfied → `FLAG_OFF`.

```text
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

Every abort preserves current P1/P2 primary authority. Shadow results are discarded and never served.

## Protection

Feature flag remains `OFF`; shadow serving and authority transfer are forbidden.

## Verification

Actual abort execution, runtime observation and traffic are `NOT_EXECUTED`.

## Handoff

Abort controls must be demonstrated in a separate Operations preparation and rollback drill.
