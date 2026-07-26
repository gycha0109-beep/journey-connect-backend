# Dependency Graph

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


```text
ENDPOINT
  -> CREDENTIAL
  -> NETWORK_ALLOWLIST
  -> TEST_ACCOUNT_ALLOWLIST
  -> COHORT_SELECTION
  -> CANDIDATE_ADAPTER
  -> METRICS
  -> DASHBOARD_ALERT
  -> ROLLBACK_DRILL
  -> ROLE_APPROVALS
  -> MANUAL_ENABLEMENT
```

| From | To | Type | Reason |
|---|---|---|---|
| `ENDPOINT` | `CREDENTIAL` | `hard_dependency` | credential audience and route validation require endpoint contract |
| `ENDPOINT` | `NETWORK_ALLOWLIST` | `hard_dependency` | route must terminate only in isolated non-production |
| `CREDENTIAL` | `NETWORK_ALLOWLIST` | `soft_dependency` | can be prepared in parallel but joint validation is required |
| `NETWORK_ALLOWLIST` | `TEST_ACCOUNT_ALLOWLIST` | `independent` | identity admission design can proceed in parallel; both are required before execution |
| `TEST_ACCOUNT_ALLOWLIST` | `COHORT_SELECTION` | `hard_dependency` | cohort key is restricted to admitted hashed test subjects |
| `COHORT_SELECTION` | `CANDIDATE_ADAPTER` | `hard_dependency` | adapter runs only after deterministic selection |
| `CANDIDATE_SOURCE_DECISION` | `CANDIDATE_ADAPTER` | `external_dependency` | actual source/protocol is not yet resolved |
| `CANDIDATE_ADAPTER` | `METRICS` | `hard_dependency` | adapter execution points must emit required metrics |
| `COHORT_SELECTION` | `METRICS` | `hard_dependency` | selected/skipped counts originate at selector |
| `METRICS` | `DASHBOARD_ALERT` | `hard_dependency` | dashboard and alert rules require implemented metrics |
| `ENDPOINT` | `ROLLBACK_DRILL` | `hard_dependency` | network/deployment rollback requires actual endpoint |
| `CREDENTIAL` | `ROLLBACK_DRILL` | `hard_dependency` | credential revoke drill requires issued test credential |
| `DASHBOARD_ALERT` | `ROLLBACK_DRILL` | `hard_dependency` | drill verification depends on metrics/alerts |
| `ROLLBACK_DRILL` | `ROLE_APPROVALS` | `approval_dependency` | approvers require successful drill evidence |
| `DASHBOARD_ALERT` | `ROLE_APPROVALS` | `approval_dependency` | observability evidence is blocking |
| `ROLE_APPROVALS` | `MANUAL_ENABLEMENT` | `approval_dependency` | all six approvals are AND conditions |
| `MANUAL_ENABLEMENT` | `STAGE1_OBSERVATION` | `hard_dependency` | observation starts only after manual 1% enablement |

## Parallel work

Endpoint design, allowlist design, candidate-source decision and metric design may start in parallel. Credential policy, cohort golden vectors, dashboard design and rollback runbook design may also proceed in parallel, but their acceptance evidence joins the serial gate chain.
