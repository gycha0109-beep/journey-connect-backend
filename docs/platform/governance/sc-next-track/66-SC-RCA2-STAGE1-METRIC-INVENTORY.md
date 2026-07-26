# Stage 1 Metric Inventory

## Purpose

Define the exact 27-metric governance inventory.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

1. `traffic_selection_evaluated_total`
2. `traffic_selection_selected_total`
3. `stable_hash_cohort_bucket`
4. `shadow_submission_total`
5. `shadow_execution_started_total`
6. `shadow_execution_completed_total`
7. `shadow_timeout_total`
8. `shadow_exception_total`
9. `shadow_queue_rejection_total`
10. `shadow_late_discard_total`
11. `shadow_cancellation_total`
12. `shadow_executor_active`
13. `shadow_executor_queue_depth`
14. `shadow_task_age_milliseconds`
15. `shadow_total_duration_milliseconds`
16. `shadow_checkpoint_lag_seconds`
17. `shadow_lineage_mismatch_total`
18. `shadow_p1_expected_protected_gap_total`
19. `shadow_p1_unexpected_mismatch_total`
20. `shadow_p2_migration_gap_total`
21. `shadow_p2_unexpected_mismatch_total`
22. `shadow_redaction_failure_total`
23. `shadow_response_mutation_total`
24. `shadow_database_write_total`
25. `shadow_event_emission_total`
26. `shadow_production_route_detection_total`
27. `shadow_authority_mismatch_total`

P1 expected/protected gaps and P2 migration gaps use dedicated counters. They are never silently included in unexpected mismatch.

All labels are bounded and privacy-safe. Raw IDs, endpoint values, credentials, content and query parameters are forbidden.

```text
METRIC_COUNT=27
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

## Protection

Feature flag remains `OFF`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden.

## Verification

Actual metric emission, dashboard observation and traffic are `NOT_EXECUTED`.

## Handoff

All 27 metrics and their alert routes must be implemented and verified in a separate preparation change.
