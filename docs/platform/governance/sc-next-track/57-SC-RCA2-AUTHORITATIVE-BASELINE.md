# Authoritative RCA-2 Baseline

## Purpose

Freeze the merged RCA-2 implementation and evidence baseline used by SC-6.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
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

The work-start commit is the PR #29 merge commit. The RCA-2 exact-final-head must be its ancestor and tree-equivalent. Historical RCA-0/RCA-1/RCA-1B/RCA-2 evidence is immutable.

## Protection

Current traffic remains `0%`; feature flag remains `OFF`; production traffic is `0%`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden; DB/SQL changes are none.

## Verification

This document is governance evidence only. Actual traffic, endpoint, credential, allowlist, observation, production route/identity/traffic, candidate serving and authority transfer are `NOT_EXECUTED`.

## Handoff

Any implementation or Operations preparation requires a separate Draft change and explicit user approval.
