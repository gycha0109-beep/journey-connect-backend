# Configuration Contract

| Field | Value |
|---|---|
| Official phase | `OP-1 RCA-2 Stage 1 Environment and Access Preparation` |
| Work-start / authoritative main | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| OP-0 exact final head | `e29a056d63c8c953851e4261bde9f71f3cd19441` |
| OP-0 merge commit | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| Artifact version | `op1-rca2-stage1-environment-access-v1` |
| Updated at | `2026-07-27T11:00:00Z` |

## OP-1 enforced values

```text
DEFAULT_FEATURE_FLAG=false
SHADOW_ENABLED=false
CONFIGURED_TRAFFIC_PERCENT=0
EFFECTIVE_TRAFFIC_PERCENT=0
MAX_CONFIGURABLE_PERCENT=1
AUTOMATIC_RAMP=FORBIDDEN
MANUAL_ENABLEMENT_PATH=BLOCKED
```

`Rca2Op1Configuration` rejects startup when traffic is non-zero, the shadow switch is true, the ceiling differs from one, automatic ramp is enabled or a database route is supplied.
