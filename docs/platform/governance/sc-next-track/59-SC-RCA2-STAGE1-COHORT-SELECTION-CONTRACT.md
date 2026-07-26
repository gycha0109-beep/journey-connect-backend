# Cohort Selection Contract

## Purpose

Define privacy-safe deterministic Stage 1 selection.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
COHORT_SELECTION=STABLE_HASH_PERCENTAGE
COHORT_KEY=HASHED_NONPRODUCTION_TEST_SUBJECT_REF
RAW_IDENTITY_COHORT_KEY=FORBIDDEN
COHORT_STABILITY_REQUIRED=YES
COHORT_RESEED_WITHIN_STAGE=FORBIDDEN
TEST_ACCOUNT_ALLOWLIST_REQUIRED=YES
CURRENT_TRAFFIC_PERCENT=0
PRODUCTION_TRAFFIC_PERCENT=0
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
```

The hash input is an approved non-production test-subject reference. Raw user IDs, emails, usernames, account numbers and production subjects are forbidden.

## Protection

Current traffic remains `0%`; feature flag remains `OFF`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden.

## Verification

Actual cohort implementation, allowlist registration and runtime observation are `NOT_EXECUTED`.

## Handoff

A separate Privacy/Security and Operations preparation change must implement and verify the cohort.
