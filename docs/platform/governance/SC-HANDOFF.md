# System Coordination Handoff

## Status

`RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE / RCA2_ENTRY_AUTHORIZED`

## Authoritative baseline

- repository: `gycha0109-beep/journey-connect-backend`;
- authoritative main/work-start: `3efbf96ebf25ae1645a62f35269c4b569425a9ca`;
- PR #27: merged;
- RCA-1B exact-final-head: `dbb6b5397ad0fe675856b195e280faf9a0f3030c`;
- merge tree: identical to exact-final-head tree;
- PostgreSQL 15/18 normalized equivalence: `CROSS_VERSION_RESULT_EQUIVALENCE=PASS`;
- P1: `RECONCILED_WITH_EXPECTED_GAPS`;
- P2: `RECONCILED_WITH_MIGRATION_GAPS`;
- `READ_ONLY_BOUNDARY=ENFORCED`, `QUERY_ALLOWLIST=ENFORCED`, `CHECKPOINT_BOUNDARY=ENFORCED`, `LINEAGE_BOUNDARY=ENFORCED`;
- production DB/traffic/identity/activation: none/not executed;
- SQL `01..52`: protected;
- SQL `53+`: absent/unallocated;
- current P1/P2 authority: unchanged.

## Official phase

```text
SC-5 RCA-2 Controlled Runtime Dark Read Entry Authorization & Execution Boundary
RCA-2 Recommendation Data Controlled Runtime Dark Read
CLASSIFICATION=JOINT_INTELLIGENCE_RELIABILITY_OPERATIONS_ADOPTION
```

`RCA` is cross-track. `RP` is reserved for Reliability Platform.

## Entry decision

```text
RCA2_ENTRY_AUTHORIZED
RCA2_EXECUTION_ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME
RCA2_RUNTIME_MODEL=ASYNC_POST_RESPONSE_SHADOW
FEATURE_FLAG_REQUIRED=YES
FEATURE_FLAG_DEFAULT=OFF
INITIAL_TRAFFIC_PERCENT=0
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_SERVING=FORBIDDEN
SHADOW_FAILURE_FALLBACK=KEEP_PRIMARY_RESULT
IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY
RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

## Purpose

Preserve current authoritative P1/P2 responses while a bounded, isolated, post-response non-production shadow path computes Data candidate results and records latency, timeout, fallback, checkpoint, lineage and classified differences. No user response, primary transaction, authority or product data is changed.

Current sources remain `RecommendationP1ProfileSource` and `RecommendationP2ObservationSource`.

## Runtime environment

- CI/runtime simulation: required;
- isolated non-production runtime: approved implementation target;
- production dark read: blocked;
- production traffic ceiling: 0%.

## Runtime model

Dedicated bounded asynchronous post-response task. No existing validated async boundary was found; the separate implementation must create, isolate and test one. Submission occurs only after response commit and after flag, cohort, breaker, identity and resource gates.

## Flag and traffic

Default OFF, unknown/stale OFF, initial 0%, no local default enable, manual audited stage changes only. Non-production stages are 1%, 10%, 50%, 100% after evidence. Production remains 0%.

## Primary/shadow contract

Primary is current P1/P2 only. Shadow has no serving, fallback, cache, DB write, event, notification or feedback authority. Every failure preserves primary.

## Timeouts and breakers

100 ms connection, 300 ms read, 500 ms total, 50 ms queue wait, 1000 ms task age, concurrency 4, queue 100, no retry, late discard. P1 and P2 breakers are independent; global and lane kill switches are mandatory.

## Credential and network

Operations-owned short-lived non-production workload identity only, secret manager, TLS and deny-by-default explicit allowlist. No production route/credential and no runtime DB role are authorized.

## Identity

Synthetic or explicit non-production test account only. Privacy/Security owns allowlist, purpose, encryption, deletion, invalidation and audit. Actual production identity and inferred fallback are blocked.

## P1 boundary

`P1_RUNTIME_DARK_READ_ONLY`, unchanged authority, no served shadow. Preserve expected/protected gaps and prohibit aggregate-to-event fabrication.

## P2 boundary

`P2_RUNTIME_DARK_READ_ONLY`, unchanged authority, no served shadow, no transfer. Preserve exact exposure/window/events/fallback, migration gaps and protected dataset/release boundaries.

## Checkpoint and freshness

Checkpoint, lineage, schema, deployment and artifact versions are mandatory. `RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT`; no live threshold is approved.

## Observability and evidence

Lane-separated low-cardinality metrics, 30-day metrics, 14-day redacted logs and 90-day exact-head artifacts. Raw IDs/content/rows/parameters/credentials/endpoints are forbidden. Critical violations are 100% recorded and auto-disable candidates.

## Deployment and rollback

Deployment, flag enable and traffic increase are separate. Exact image digest/artifact/config/flag versions are recorded. Rollback levels: flag, lane kill, global disable, config, deployment, credential and network.

## DB and SQL

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=NO
NEW_GRANT_REQUIRED=NO
RUNTIME_QUERY_REGISTRY_REQUIRED=YES_APPLICATION_CONTRACT_ONLY
PERSISTED_EVIDENCE_REQUIRED=NO
```

A newly discovered persistent DB requirement stops implementation and returns to SC. Do not allocate SQL `53+`.

## Approvals

| Role | State |
|---|---|
| Intelligence | `BLOCKING_APPROVAL` |
| Reliability | `BLOCKING_APPROVAL` |
| Data | `REQUIRED` |
| Operations | `BLOCKING_APPROVAL` |
| Privacy/Security | `BLOCKING_APPROVAL` |
| System Coordination | `BLOCKING_APPROVAL` |

## Verification truth

SC-5 verifies governance and baseline only. Runtime dark read, runtime flag behavior, credentials, production route, canary, load, replay, production validation, actual identity mapping, activation and authority transfer are not executed and are not PASS.

## Documents

- [SC-5 master](sc-next-track/SC-5-RCA-2-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md)
- [environment](sc-next-track/38-SC-RCA2-RUNTIME-ENVIRONMENT-DECISION.md)
- [model](sc-next-track/39-SC-RCA2-RUNTIME-MODEL-DECISION.md)
- [flag/traffic](sc-next-track/40-SC-RCA2-FEATURE-FLAG-AND-TRAFFIC-POLICY.md)
- [authority](sc-next-track/41-SC-RCA2-PRIMARY-SHADOW-AUTHORITY-CONTRACT.md)
- [timeouts/breakers](sc-next-track/42-SC-RCA2-TIMEOUT-FALLBACK-CIRCUIT-BREAKER-POLICY.md)
- [credential/network](sc-next-track/43-SC-RCA2-CREDENTIAL-AND-NETWORK-BOUNDARY.md)
- [query](sc-next-track/44-SC-RCA2-RUNTIME-QUERY-BOUNDARY.md)
- [identity](sc-next-track/45-SC-RCA2-IDENTITY-AND-PRIVACY-GOVERNANCE.md)
- [P1](sc-next-track/46-SC-RCA2-P1-RUNTIME-DARK-READ-DECISION.md)
- [P2](sc-next-track/47-SC-RCA2-P2-RUNTIME-DARK-READ-DECISION.md)
- [checkpoint/freshness](sc-next-track/48-SC-RCA2-CHECKPOINT-LINEAGE-FRESHNESS-DECISION.md)
- [observability](sc-next-track/49-SC-RCA2-OBSERVABILITY-METRIC-LOGGING-POLICY.md)
- [alert/kill](sc-next-track/50-SC-RCA2-ALERT-AND-KILL-SWITCH-POLICY.md)
- [deployment/rollback](sc-next-track/51-SC-RCA2-DEPLOYMENT-AND-ROLLBACK-POLICY.md)
- [DB/SQL](sc-next-track/52-SC-RCA2-DB-SQL-IMPACT-DECISION.md)
- [approvals](sc-next-track/53-SC-RCA2-OPERATIONS-RELIABILITY-APPROVAL-MATRIX.md)
- [verification](sc-next-track/54-SC-RCA2-VERIFICATION-PLAN.md)
- [exit](sc-next-track/55-SC-RCA2-EXIT-CRITERIA-AND-AUTHORITY-TRANSFER-BOUNDARY.md)
- [implementation handoff](sc-next-track/56-RCA-2-IMPLEMENTATION-HANDOFF-PROMPT.md)

## Current gate

```text
RCA2_ENTRY_AUTHORIZED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
```

Keep the SC-5 PR Draft and unmerged until explicit user approval.
