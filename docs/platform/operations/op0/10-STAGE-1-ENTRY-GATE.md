# Stage 1 Entry Gate

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


The machine-readable gate is `stage1-enable-gate.json`. Every condition is AND-bound:

```text
ENDPOINT_READY
CREDENTIAL_READY
ALLOWLIST_READY
COHORT_READY
CANDIDATE_ADAPTER_READY
METRICS_READY
DASHBOARD_READY
CRITICAL_ALERT_READY
ROLLBACK_DRILL_READY
INTELLIGENCE_APPROVED
RELIABILITY_APPROVED
DATA_APPROVED
OPERATIONS_APPROVED
PRIVACY_SECURITY_APPROVED
SYSTEM_COORDINATION_APPROVED
MANUAL_ENABLEMENT_APPROVED
```

Current state: all implementation/approval conditions are false. Therefore:

```text
TRAFFIC_ENABLEMENT=BLOCKED
CURRENT_TRAFFIC_PERCENT=0
```
