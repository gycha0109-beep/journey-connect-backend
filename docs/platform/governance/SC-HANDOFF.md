# System Coordination Handoff

## Status

`RCA2_CONTROLLED_NONPRODUCTION_RUNTIME_DARK_READ_COMPLETE / SC6_STAGE1_CONDITIONALLY_AUTHORIZED / TRAFFIC_ENABLEMENT_BLOCKED`

## Authoritative baseline

```text
AUTHORITATIVE_MAIN_WORK_START=b57c344c9b4e332966fe9f6d36a5da66a5faae71
PR29_MERGED=YES
RCA2_EXACT_FINAL_HEAD=511b19f80cdd42bb2fafde0563c7388b4f5b5f48
RCA2_EVIDENCE_ARTIFACT_ID=8621492010
RCA2_EVIDENCE_DIGEST=sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760
RCA2_CONTROLLED_NONPRODUCTION_RUNTIME_DARK_READ_COMPLETE
FEATURE_FLAG_DEFAULT=OFF
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

## SC-6 decision

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

Merging the SC-6 governance PR does not enable Stage 1 traffic. Traffic remains 0 until separate implementation/Operations preparation, all blocking approvals and a manual enablement decision are complete.

## Cohort and observation

```text
COHORT_SELECTION=STABLE_HASH_PERCENTAGE
COHORT_KEY=HASHED_NONPRODUCTION_TEST_SUBJECT_REF
RAW_IDENTITY_COHORT_KEY=FORBIDDEN
MIN_OBSERVATION_DURATION_MINUTES=30
MIN_SHADOW_EXECUTION_COUNT=100
BOTH_CONDITIONS_REQUIRED=YES
OBSERVATION_STATUS=NOT_EXECUTED
```

## Safety ceiling

Timeout ≤20%, exception ≤25%, queue rejection ≤5% and late discard ≤5%. Redaction failure, response mutation, database write, event emission, production-route detection and authority mismatch each have zero tolerance.

P1 expected/protected gaps and P2 migration gaps remain independent from unexpected mismatch.

## Owners

- endpoint: Operations;
- credential: Operations;
- identity allowlist: Privacy/Security;
- alert: Operations;
- rollback execution: Operations.

## Approvals

Intelligence, Reliability, Data, Operations, Privacy/Security and SystemCoordination are each `BLOCKING_APPROVAL / PENDING_USER_REVIEW`. No human approval is represented as approved.

## Rollback

| Level | Action | Owner | Maximum |
|---|---|---|---:|
| 1 | flag OFF | Operations | 60s |
| 2 | lane kill switch | Operations | 60s |
| 3 | global shadow disable | Operations | 120s |
| 4 | configuration rollback | Operations | 300s |
| 5 | deployment rollback | Operations | 600s |
| 6 | credential revoke | Operations | 300s |
| 7 | network route revoke | Operations | 600s |

Real credential and network rollback drills remain `NOT_EXECUTED`.

## Current blockers

Endpoint not built; candidate adapter contract-only; stable-hash cohort absent; credential not issued; allowlist empty; traffic-selection/executor/task-age/cancellation/checkpoint-lag metrics incomplete; dashboard and notification route unverified; infrastructure rollback drill not run; six approvals pending; runtime observation not run; empirical unexpected-mismatch threshold absent.

## Documents and evidence

SC-6 master plus documents 57 through 75 are under `docs/platform/governance/sc-next-track/`. Fourteen TSV evidence files and the independent verifier are under `verification/sc-next-track/rca2-nonzero-nonprod-entry/`.

## Next work

A separate Draft Stage 1 preparation PR or controlled Operations change must prepare endpoint, credential, allowlist, cohort, 27 metrics, dashboard, alert route and rollback drill. It must not enable traffic without a separate explicit user-approved manual decision.
