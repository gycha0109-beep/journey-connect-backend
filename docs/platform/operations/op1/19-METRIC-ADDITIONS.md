# Metric Additions

| Field | Value |
|---|---|
| Official phase | `OP-1 RCA-2 Stage 1 Environment and Access Preparation` |
| Work-start / authoritative main | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| OP-0 exact final head | `e29a056d63c8c953851e4261bde9f71f3cd19441` |
| OP-0 merge commit | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| Artifact version | `op1-rca2-stage1-environment-access-v1` |
| Updated at | `2026-07-27T11:00:00Z` |

## Added low-cardinality metrics

```text
shadow_endpoint_validation_total
shadow_endpoint_blocked_total
shadow_credential_unavailable_total
shadow_credential_refresh_total
shadow_credential_refresh_failure_total
shadow_allowlist_lookup_total
shadow_allowlist_denied_total
shadow_cohort_selected_total
shadow_cohort_skipped_total
shadow_candidate_invocation_blocked_total
```

Labels remain the existing bounded `environment`, `lane`, `result_class`, `breaker_state`. The authoritative SC-6 metric inventory and thresholds are unchanged. Dashboard and alert routes remain OP-2 work.
