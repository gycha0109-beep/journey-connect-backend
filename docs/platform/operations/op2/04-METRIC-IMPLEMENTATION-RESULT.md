# Metric Implementation Result

Seven backlog metrics are implemented at actual application boundaries:

| Metric | Source | Result |
|---|---|---|
| `traffic_selected_count` | stable-hash selected decision | `IMPLEMENTED` |
| `traffic_skipped_count` | fail-closed selection exits | `IMPLEMENTED` |
| `executor_active_count` | bounded executor gauge | `IMPLEMENTED` |
| `executor_queue_depth` | bounded queue gauge | `IMPLEMENTED` |
| `shadow_task_age_ms` | enqueue-to-dispatch age | `IMPLEMENTED` |
| `shadow_cancelled_count` | queued/in-flight cancellation | `IMPLEMENTED` |
| `checkpoint_lag_ms` | comparator measurement | `IMPLEMENTED` |

Tests cover counters, gauges, timers, bounded labels, redaction, selection, cancellation and checkpoint lag. External scrape availability and retention remain unverified.
