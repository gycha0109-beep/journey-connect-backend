# Stage 1 Execution Handoff Prompt

## Purpose

Define the next separate implementation/operations preparation package.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

The next work item must start from the then-current authoritative main and must not modify this governance decision.

Required preparation: isolated endpoint, short-lived credential, approved test-account allowlist, stable-hash cohort, all 27 metrics, dashboard, alert route, rollback drill, six exact-head blocking approvals and manual enablement control.

The preparation change must preserve:

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

It must report actual endpoint/credential/allowlist/observation as `NOT_EXECUTED` until independently run. It must remain Draft and must not enable 1% traffic without a separate user-approved manual decision.

## Protection

Current traffic remains `0%`; feature flag remains `OFF`; production traffic is `0%`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden; DB/SQL changes are none.

## Verification

This document is governance evidence only. Actual traffic, endpoint, credential, allowlist, observation, production route/identity/traffic, candidate serving and authority transfer are `NOT_EXECUTED`.

## Handoff

Any implementation or Operations preparation requires a separate Draft change and explicit user approval.
