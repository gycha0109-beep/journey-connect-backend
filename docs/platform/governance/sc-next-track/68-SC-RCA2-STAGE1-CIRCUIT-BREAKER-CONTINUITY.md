# Circuit Breaker Continuity

## Purpose

Preserve RCA-2 breaker semantics for Stage 1.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

P1 and P2 retain independent `CLOSED/OPEN/HALF_OPEN` breakers and lane kill switches. Global shadow disable overrides both. Existing 20-sample, 25% failure, 20% timeout, 60-second open and two-probe continuity is protected; Stage 1 does not weaken it. Breaker reset never enables traffic.

## Protection

Current traffic remains `0%`; feature flag remains `OFF`; production traffic is `0%`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden; DB/SQL changes are none.

## Verification

This document is governance evidence only. Actual traffic, endpoint, credential, allowlist, observation, production route/identity/traffic, candidate serving and authority transfer are `NOT_EXECUTED`.

## Handoff

Any implementation or Operations preparation requires a separate Draft change and explicit user approval.
