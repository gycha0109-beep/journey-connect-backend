# Metric Implementation Backlog

| Field | Value |
|---|---|
| Official phase | `OP-0 RCA-2 Stage 1 Operations Preparation Baseline` |
| Work-start / authoritative main | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| RCA-2 exact final head | `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` |
| RCA-2 merge commit | `b57c344c9b4e332966fe9f6d36a5da66a5faae71` |
| SC-6 exact final head | `20da93e932c50b5bebd549a56db40edb00ca1eea` |
| SC-6 merge commit | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| Artifact version | `op0-rca2-stage1-operations-preparation-v1` |
| Updated at | `2026-07-26T14:15:55Z` |


## Authoritative SC-6 inventory: 27 metrics

| Metric | Type | Existing semantic owner | Continuity status |
|---|---|---|---|
| `traffic_selection_evaluated_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `traffic_selection_selected_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `stable_hash_cohort_bucket` | gauge | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_submission_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_execution_started_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_execution_completed_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_timeout_total` | counter | RELIABILITY | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_exception_total` | counter | RELIABILITY | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_queue_rejection_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_late_discard_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_cancellation_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_executor_active` | gauge | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_executor_queue_depth` | gauge | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_task_age_milliseconds` | histogram | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_total_duration_milliseconds` | histogram | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_checkpoint_lag_seconds` | histogram | DATA | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_lineage_mismatch_total` | counter | DATA | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_p1_expected_protected_gap_total` | counter | INTELLIGENCE | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_p1_unexpected_mismatch_total` | counter | INTELLIGENCE | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_p2_migration_gap_total` | counter | RELIABILITY | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_p2_unexpected_mismatch_total` | counter | RELIABILITY | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_redaction_failure_total` | counter | PRIVACY_SECURITY | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_response_mutation_total` | counter | RELIABILITY | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_database_write_total` | counter | DATA | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_event_emission_total` | counter | DATA | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_production_route_detection_total` | counter | OPERATIONS | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |
| `shadow_authority_mismatch_total` | counter | SYSTEMCOORDINATION | `AUTHORITATIVE_SC6_REQUIRED_NOT_IMPLEMENTED` |

## Minimum implementation gaps

| Required name | SC-6 mapping | Type/unit | Metric owner |
|---|---|---|---|
| `traffic_selected_count` | `traffic_selection_selected_total` | counter / count | RELIABILITY |
| `traffic_skipped_count` | `traffic_selection_evaluated_total[result=skipped]` | counter / count | RELIABILITY |
| `executor_active_count` | `shadow_executor_active` | gauge / count | RELIABILITY |
| `executor_queue_depth` | `shadow_executor_queue_depth` | gauge / count | RELIABILITY |
| `shadow_task_age_ms` | `shadow_task_age_milliseconds` | histogram / milliseconds | RELIABILITY |
| `shadow_cancelled_count` | `shadow_cancellation_total` | counter / count | RELIABILITY |
| `checkpoint_lag_ms` | `shadow_checkpoint_lag_seconds` | histogram / milliseconds | RELIABILITY |

Each backlog item defines labels, cardinality, source, alert use, retention, redaction and acceptance test in `metric-backlog.json`. `checkpoint_lag_ms` is an implementation-facing millisecond measurement explicitly mapped to SC-6 `shadow_checkpoint_lag_seconds`; conversions must be tested and cannot change threshold semantics.
