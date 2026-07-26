# Checkpoint, Lineage and Freshness Policy

## Purpose

Define measurement-only freshness evidence.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

```text
CHECKPOINT_BOUNDARY=ENFORCED
LINEAGE_BOUNDARY=ENFORCED
FRESHNESS_MODE=MEASUREMENT_ONLY
CHECKPOINT_LAG_METRIC=REQUIRED_NOT_IMPLEMENTED
LIVE_FRESHNESS_THRESHOLD=BLOCKED_PENDING_EMPIRICAL_EVIDENCE
```

No freshness PASS threshold is invented in SC-6. Incompatible checkpoint or lineage keeps primary and classifies the shadow result.

## Protection

Current traffic remains `0%`; feature flag remains `OFF`; production traffic is `0%`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden; DB/SQL changes are none.

## Verification

This document is governance evidence only. Actual traffic, endpoint, credential, allowlist, observation, production route/identity/traffic, candidate serving and authority transfer are `NOT_EXECUTED`.

## Handoff

Any implementation or Operations preparation requires a separate Draft change and explicit user approval.
