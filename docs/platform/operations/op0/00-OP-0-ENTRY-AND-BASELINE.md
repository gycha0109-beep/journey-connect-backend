# OP-0 Entry and Baseline

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


## Entry verification

- PR #29: `MERGED`; exact final head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; merge commit `b57c344c9b4e332966fe9f6d36a5da66a5faae71`.
- PR #30: `MERGED`; exact final head `20da93e932c50b5bebd549a56db40edb00ca1eea`; merge commit `40ff229e2401e7d5d9c5323d469bcd012530e882`.
- Current `main`: `40ff229e2401e7d5d9c5323d469bcd012530e882`. It is exactly the SC-6 merge commit.
- SC-6 merge-tree equivalence and ancestry are independently checked by `verification/operations/op0/run_op0_verification.py`.

## OP-0 result

`RCA2_STAGE1_OPERATIONS_PREPARATION_BASELINE_ESTABLISHED`

The baseline is complete as a planning/governance package. OP-1 entry and Stage 1 enablement remain blocked because implementation paths, actual infrastructure, evidence and approvals are not complete. No pending item is represented as approved.

## Immutable boundary

```text
CURRENT_NONPRODUCTION_TRAFFIC_PERCENT=0
TARGET_NONPRODUCTION_TRAFFIC_PERCENT=1
PRODUCTION_TRAFFIC_PERCENT=0
FEATURE_FLAG_DEFAULT=OFF
MANUAL_ENABLEMENT_REQUIRED=YES
AUTOMATIC_ROLLOUT=FORBIDDEN
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_SERVING=FORBIDDEN
PRIMARY_RESPONSE_MUTATION=FORBIDDEN
DATABASE_WRITE=FORBIDDEN
CACHE_WRITE=FORBIDDEN
EVENT_EMISSION=FORBIDDEN
NOTIFICATION_EMISSION=FORBIDDEN
RANKING_FEEDBACK=FORBIDDEN
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
```

## Change class

Governance and operations-preparation contracts only. No endpoint, credential, identity registration, route, dashboard deployment, alert connection, candidate source connection, runtime source, traffic configuration, DB or SQL is changed.
