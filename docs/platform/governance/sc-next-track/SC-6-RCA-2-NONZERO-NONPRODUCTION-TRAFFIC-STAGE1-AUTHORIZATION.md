# SC-6 RCA-2 Nonzero Non-production Traffic Stage 1 Authorization

## Purpose

Define governance-only entry conditions for a future isolated non-production Stage 1 shadow traffic ceiling. This document does not enable traffic, deploy an endpoint, issue credentials, register identities, change runtime source, alter configuration, create SQL, or transfer authority.

## Authoritative baseline

```text
AUTHORITATIVE_MAIN_WORK_START=b57c344c9b4e332966fe9f6d36a5da66a5faae71
PR29_MERGED=YES
RCA2_EXACT_FINAL_HEAD=511b19f80cdd42bb2fafde0563c7388b4f5b5f48
RCA2_EVIDENCE_ARTIFACT_ID=8621492010
RCA2_EVIDENCE_DIGEST=sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760
RCA2_CONTROLLED_NONPRODUCTION_RUNTIME_DARK_READ_COMPLETE
RCA2_EXECUTION_ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME
RCA2_RUNTIME_MODEL=ASYNC_POST_RESPONSE_SHADOW
FEATURE_FLAG_DEFAULT=OFF
INITIAL_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_SERVING=FORBIDDEN
SHADOW_FAILURE_FALLBACK=KEEP_PRIMARY_RESULT
P1_RESULT=CONTRACT_ADAPTER_MATCH_WITH_EXPECTED_PROTECTED_GAPS
P2_RESULT=CONTRACT_ADAPTER_MATCH_WITH_MIGRATION_GAPS
CHECKPOINT_BOUNDARY=ENFORCED
LINEAGE_BOUNDARY=ENFORCED
IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY
OBSERVABILITY=ACTIVE
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
```

## Conditional decision

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

Merging this governance PR does not enable Stage 1 traffic. Traffic remains 0 until the separate implementation/operations preparation, blocking role approvals, and manual enablement decision are complete.

## Stage 1 contract

- Environment: `ISOLATED_NON_PRODUCTION_RUNTIME`.
- Stage: `STAGE_1`.
- Candidate ceiling: `1%`.
- Effective traffic: `0%`.
- Feature flag default: `OFF`.
- Enablement: manual, audited, separately approved.
- Automatic rollout: forbidden.
- Production ceiling: `0%`.
- Cohort selection: `STABLE_HASH_PERCENTAGE`.
- Cohort key: `HASHED_NONPRODUCTION_TEST_SUBJECT_REF`.
- Raw identity cohort key: forbidden.

## Observation contract

`MIN_OBSERVATION_DURATION_MINUTES=30`, `MIN_SHADOW_EXECUTION_COUNT=100`, and `BOTH_CONDITIONS_REQUIRED=YES`. Neither condition has been executed in this governance phase.

## Safety thresholds

- `timeout_rate_percent` ≤ `20` PERCENT; action `ABORT_OR_LANE_DISABLE`.
- `exception_rate_percent` ≤ `25` PERCENT; action `ABORT_OR_LANE_DISABLE`.
- `queue_rejection_rate_percent` ≤ `5` PERCENT; action `ABORT_OR_LANE_DISABLE`.
- `late_discard_rate_percent` ≤ `5` PERCENT; action `ABORT_OR_LANE_DISABLE`.
- `redaction_failure_count` ≤ `0` COUNT; action `IMMEDIATE_GLOBAL_ABORT`.
- `response_mutation_count` ≤ `0` COUNT; action `IMMEDIATE_GLOBAL_ABORT`.
- `database_write_count` ≤ `0` COUNT; action `IMMEDIATE_GLOBAL_ABORT`.
- `event_emission_count` ≤ `0` COUNT; action `IMMEDIATE_GLOBAL_ABORT`.
- `production_route_detection_count` ≤ `0` COUNT; action `IMMEDIATE_GLOBAL_ABORT`.
- `authority_mismatch_count` ≤ `0` COUNT; action `IMMEDIATE_GLOBAL_ABORT`.

P1 expected/protected gaps and P2 migration gaps are separate categories and never count as unexpected mismatch unless their protected classification itself is violated.

## Required owners

- `endpoint` owner: **Operations** (`OPERATIONS`).
- `credential` owner: **Operations** (`OPERATIONS`).
- `identity_allowlist` owner: **Privacy/Security** (`PRIVACY_SECURITY`).
- `alert` owner: **Operations** (`OPERATIONS`).
- `rollback_execution` owner: **Operations** (`OPERATIONS`).

## Blocking approvals

- `Intelligence=BLOCKING_APPROVAL`; status `PENDING_USER_REVIEW`.
- `Reliability=BLOCKING_APPROVAL`; status `PENDING_USER_REVIEW`.
- `Data=BLOCKING_APPROVAL`; status `PENDING_USER_REVIEW`.
- `Operations=BLOCKING_APPROVAL`; status `PENDING_USER_REVIEW`.
- `Privacy/Security=BLOCKING_APPROVAL`; status `PENDING_USER_REVIEW`.
- `SystemCoordination=BLOCKING_APPROVAL`; status `PENDING_USER_REVIEW`.

No human approval is represented as approved.

## Current blockers

- `SC6-B001` — **isolated_nonproduction_endpoint**: Isolated non-production endpoint is not built or verified.
- `SC6-B002` — **candidate_adapter**: Candidate adapter remains a contract-only primary mirror.
- `SC6-B003` — **stable_hash_cohort**: Approved test-subject stable-hash cohort is not implemented.
- `SC6-B004` — **short_lived_credential**: Short-lived credential is not issued or verified.
- `SC6-B005` — **test_account_allowlist**: Test-account allowlist is empty.
- `SC6-B006` — **traffic_selection_metric**: Traffic selection metric is incomplete.
- `SC6-B007` — **executor_gauge**: Executor gauge is incomplete.
- `SC6-B008` — **task_age_metric**: Task age metric is incomplete.
- `SC6-B009` — **cancellation_metric**: Cancellation metric is incomplete.
- `SC6-B010` — **checkpoint_lag_metric**: Checkpoint lag metric is incomplete.
- `SC6-B011` — **dashboard**: Stage 1 dashboard is not verified.
- `SC6-B012` — **critical_notification_route**: Critical notification route is not verified.
- `SC6-B013` — **rollback_infrastructure_drill**: Credential/network rollback infrastructure drill is not executed.
- `SC6-B014` — **blocking_approvals**: Six blocking approvals remain pending user review.
- `SC6-B015` — **runtime_observation**: Stage 1 runtime observation is not executed.
- `SC6-B016` — **unexpected_mismatch_threshold**: No empirical unexpected mismatch threshold basis exists.

## Rollback ownership

- `LEVEL_1 FLAG_OFF` — owner `Operations`, trigger: flag enabled unexpectedly or any threshold breach; procedure: set Stage 1 flag OFF and confirm effective traffic 0; max `60s`; verification: flag OFF, selected count stops, primary unaffected; recovery: all critical counters stable at zero; manual reapproval required.
- `LEVEL_2 LANE_KILL_SWITCH` — owner `Operations`, trigger: P1 or P2 lane-specific breach; procedure: activate affected lane kill switch; max `60s`; verification: affected lane submissions stop; other lane and primary remain intact; recovery: lane root cause corrected and lane-specific approval renewed.
- `LEVEL_3 GLOBAL_SHADOW_DISABLE` — owner `Operations`, trigger: cross-lane or authority/redaction breach; procedure: activate global shadow disable; max `120s`; verification: all shadow submissions stop and traffic is 0; recovery: incident closed and all six blocking approvals renewed.
- `LEVEL_4 CONFIG_ROLLBACK` — owner `Operations`, trigger: invalid cohort/threshold/flag configuration; procedure: restore last known-good signed configuration; max `300s`; verification: configuration digest restored; flag remains OFF; recovery: configuration independently reviewed and exact-head bound.
- `LEVEL_5 DEPLOYMENT_ROLLBACK` — owner `Operations`, trigger: binary/runtime regression; procedure: roll back isolated non-production deployment image; max `600s`; verification: previous image digest active; no shadow execution; recovery: deployment verification and rollback review pass.
- `LEVEL_6 CREDENTIAL_REVOKE` — owner `Operations`, trigger: credential compromise or auth anomaly; procedure: revoke workload credential and invalidate leases; max `300s`; verification: credential rejected and no task can authenticate; recovery: new short-lived credential issued after Privacy/Security approval.
- `LEVEL_7 NETWORK_ROUTE_REVOKE` — owner `Operations`, trigger: production route detection or network policy failure; procedure: remove route and deny egress at network boundary; max `600s`; verification: route unreachable and production detection remains zero; recovery: network drill passes and SC explicitly reauthorizes preparation.

## Prohibited implementation

No runtime Java/Kotlin, traffic configuration, flag enablement, endpoint deployment, credential/secret, network route, actual allowlist, production configuration, DB/SQL, Recommendation source/core, candidate serving, or authority transfer is included.

## Verification truth

Governance documents, machine-readable evidence, exact work-start ancestry, PR #29 merge/tree equivalence, RCA-2 artifact identity, SQL protection, historical evidence protection and diff scope may be `PASS`. Actual traffic, endpoint, credential, allowlist, runtime observation, canary, load, production route/identity/traffic, candidate serving and authority transfer remain `NOT_EXECUTED`.

## Handoff

Stage 1 preparation requires a separate Draft implementation/operations PR or controlled Operations change. User approval is required before this governance PR may be marked Ready or merged.
