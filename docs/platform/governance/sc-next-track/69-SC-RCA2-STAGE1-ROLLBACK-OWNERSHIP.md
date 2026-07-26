# Rollback Ownership

## Purpose

Define Level 1 through Level 7 execution ownership.

## Authoritative baseline

Work-start `b57c344c9b4e332966fe9f6d36a5da66a5faae71`; PR #29 merged; RCA-2 exact-final-head `511b19f80cdd42bb2fafde0563c7388b4f5b5f48`; evidence artifact `8621492010` with digest `sha256:9e95b85fff822cfae5aa6f7dbf25425d7c47551c32c18ac014aea4b90ee6a760`.

## Contract

- `LEVEL_1 FLAG_OFF` — owner `Operations`, trigger: flag enabled unexpectedly or any threshold breach; procedure: set Stage 1 flag OFF and confirm effective traffic 0; max `60s`; verification: flag OFF, selected count stops, primary unaffected; recovery: all critical counters stable at zero; manual reapproval required.
- `LEVEL_2 LANE_KILL_SWITCH` — owner `Operations`, trigger: P1 or P2 lane-specific breach; procedure: activate affected lane kill switch; max `60s`; verification: affected lane submissions stop; other lane and primary remain intact; recovery: lane root cause corrected and lane-specific approval renewed.
- `LEVEL_3 GLOBAL_SHADOW_DISABLE` — owner `Operations`, trigger: cross-lane or authority/redaction breach; procedure: activate global shadow disable; max `120s`; verification: all shadow submissions stop and traffic is 0; recovery: incident closed and all six blocking approvals renewed.
- `LEVEL_4 CONFIG_ROLLBACK` — owner `Operations`, trigger: invalid cohort/threshold/flag configuration; procedure: restore last known-good signed configuration; max `300s`; verification: configuration digest restored; flag remains OFF; recovery: configuration independently reviewed and exact-head bound.
- `LEVEL_5 DEPLOYMENT_ROLLBACK` — owner `Operations`, trigger: binary/runtime regression; procedure: roll back isolated non-production deployment image; max `600s`; verification: previous image digest active; no shadow execution; recovery: deployment verification and rollback review pass.
- `LEVEL_6 CREDENTIAL_REVOKE` — owner `Operations`, trigger: credential compromise or auth anomaly; procedure: revoke workload credential and invalidate leases; max `300s`; verification: credential rejected and no task can authenticate; recovery: new short-lived credential issued after Privacy/Security approval.
- `LEVEL_7 NETWORK_ROUTE_REVOKE` — owner `Operations`, trigger: production route detection or network policy failure; procedure: remove route and deny egress at network boundary; max `600s`; verification: route unreachable and production detection remains zero; recovery: network drill passes and SC explicitly reauthorizes preparation.

`ROLLBACK_EXECUTION_OWNER=OPERATIONS`. Levels 6 and 7 remain unexecuted until real non-production infrastructure exists and the drill succeeds.

## Protection

Current traffic remains `0%`; feature flag remains `OFF`; production traffic is `0%`; primary authority remains current P1/P2; shadow serving and authority transfer are forbidden; DB/SQL changes are none.

## Verification

This document is governance evidence only. Actual traffic, endpoint, credential, allowlist, observation, production route/identity/traffic, candidate serving and authority transfer are `NOT_EXECUTED`.

## Handoff

Any implementation or Operations preparation requires a separate Draft change and explicit user approval.
