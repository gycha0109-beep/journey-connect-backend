# SC Decision Register

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-decision-register-v1` |
| status | `ACTIVE / SC-6 STAGE 1 CONDITIONALLY AUTHORIZED / ENABLEMENT BLOCKED` |
| authoritative main/work-start | `b57c344c9b4e332966fe9f6d36a5da66a5faae71` |
| RCA-2 exact-final-head | `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` |
| updated | `2026-07-26` |

## Historical decisions retained

Data Platform closure and SQL `01..52` protection remain complete; SQL `53+` remains absent/unallocated. RCA-0, RCA-1, RCA-1B and RCA-2 historical evidence remains immutable. PR #29 is merged. Current P1/P2 authority, RCA-2 default OFF, traffic 0, no serving, no production activation and no authority transfer remain protected.

## SC-5 retained decision

`RCA2_ENTRY_AUTHORIZED` authorized the isolated non-production runtime implementation only. RCA-2 completed at `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` with evidence artifact `8621492010` and digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## SC-6 decisions

| Decision ID | Decision | Status | Restriction |
|---|---|---|---|
| `SC-RCA2-S1-001` | Stage 1 candidate ceiling | CONDITIONALLY AUTHORIZED | target 1%, current 0% |
| `SC-RCA2-S1-002` | stable-hash cohort | REQUIRED | hashed non-production test subject only |
| `SC-RCA2-S1-003` | endpoint | BLOCKED | Operations-owned, not implemented |
| `SC-RCA2-S1-004` | credential | BLOCKED | short-lived, not issued |
| `SC-RCA2-S1-005` | identity allowlist | BLOCKED | Privacy/Security-owned, empty |
| `SC-RCA2-S1-006` | observation | REQUIRED | 30 minutes and 100 executions |
| `SC-RCA2-S1-007` | metrics | BLOCKED | exact 27-metric inventory incomplete |
| `SC-RCA2-S1-008` | safety ceilings | CONDITIONALLY AUTHORIZED | zero critical tolerance |
| `SC-RCA2-S1-009` | rollback Level 1..7 | REQUIRED | Operations execution owner |
| `SC-RCA2-S1-010` | six role approvals | BLOCKED | pending user review |
| `SC-RCA2-S1-011` | actual traffic enablement | NOT AUTHORIZED | separate manual decision |
| `SC-RCA2-S1-012` | production traffic | NOT AUTHORIZED | 0% |
| `SC-RCA2-S1-013` | DB/SQL | NOT REQUIRED | no change/allocation |
| `SC-RCA2-S1-014` | authority transfer | FORBIDDEN | separate review |

## Decision block

```text
RCA2_NONZERO_NONPRODUCTION_TRAFFIC_STAGE1_CONDITIONALLY_AUTHORIZED
TARGET_ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME
TARGET_TRAFFIC_STAGE=STAGE_1
TARGET_TRAFFIC_PERCENT=1
CURRENT_TRAFFIC_PERCENT=0
TRAFFIC_ENABLEMENT=BLOCKED_PENDING_ALL_CONDITIONS
FEATURE_FLAG_DEFAULT=OFF
MANUAL_ENABLEMENT_REQUIRED=YES
AUTOMATIC_ROLLOUT=FORBIDDEN
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_SERVING=FORBIDDEN
PRODUCTION_TRAFFIC_PERCENT=0
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
APPROVAL_STATUS=PENDING_USER_REVIEW
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
STAGE1_EXECUTION_REQUIRES_SEPARATE_PR_OR_OPERATIONS_CHANGE
```

## Blockers

- isolated non-production endpoint not built or verified;
- candidate adapter remains contract-only primary mirror;
- stable-hash test-subject cohort not implemented;
- short-lived credential not issued or verified;
- test-account allowlist empty;
- traffic selection, executor, task-age, cancellation and checkpoint-lag metrics incomplete;
- dashboard and critical notification route unverified;
- credential/network rollback infrastructure drill not executed;
- six blocking approvals pending user review;
- runtime observation not executed;
- no empirical unexpected-mismatch threshold basis.
