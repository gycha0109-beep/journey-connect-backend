# SC-5 RCA-2 Controlled Runtime Dark Read Entry Authorization & Execution Boundary

## Scope

Authorize only the separate implementation of RCA-2 controlled runtime dark read. This document implements no runtime code, feature flag, credential, deployment, SQL, DB object or identity mapping.

## Current Baseline

Authoritative main/work-start: `3efbf96ebf25ae1645a62f35269c4b569425a9ca`. PR #27 is merged. RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c` is an ancestor and has an identical tree to the merge commit. Baseline: `RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE`, PostgreSQL 15/18 equivalence PASS, P1 `RECONCILED_WITH_EXPECTED_GAPS`, P2 `RECONCILED_WITH_MIGRATION_GAPS`, enforced read-only/query/checkpoint/lineage boundaries, no production DB/traffic, no actual identity mapping, SQL `01..52` protected, SQL `53+` unallocated, current authority unchanged.

## Decision

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
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

## Rationale

Environment B exposes real orchestration, cancellation and observability defects without production data or serving authority. Environment A remains CI support. Environment C is blocked.

## Authority

P1: Intelligence. P2: Reliability. Candidate/checkpoint/lineage: Data. Runtime controls: Operations. Identity/redaction: Privacy/Security. Entry, exit, SQL, rollout ceiling and transfer: System Coordination. `RCA` is cross-track; `RP` remains Reliability Platform.

## Dependencies

Merged RCA-1B, separate Draft implementation PR, exact artifact/config versions, Data review, and blocking Intelligence, Reliability, Operations, Privacy/Security and SC approvals before nonzero traffic.

## Runtime Environment

`ISOLATED_NON_PRODUCTION_RUNTIME` is approved. CI/runtime simulation is required. Production dark read, production route, production credential and production identity are blocked.

## Runtime Model

Use a dedicated bounded asynchronous post-response executor. Submission occurs only after the authoritative response is committed. No validated async boundary currently exists; implementation must create and prove it. Queue/event worker Model C is blocked pending a separate contract.

## Feature Flag

Required; default OFF. Missing, unknown, malformed, expired or stale values resolve OFF. Refresh 30 seconds, stale after 120 seconds, maximum flag TTL 30 days, audit retention 90 days. Lane and global kill switches override flags.

## Traffic Boundary

Initial 0%. Non-production stages may progress manually through 1%, 10%, 50%, 100% after gates. Production ceiling remains 0%. Automatic rollout is forbidden.

## Primary/Shadow Authority

Primary is current P1/P2 only. Shadow authority is NONE. Serving, blending, fallback, cache/product persistence, DB write, event emission, notification, ranking feedback and response mutation are forbidden.

## Timeout/Fallback

Connection 100 ms; read 300 ms; total 500 ms; queue wait 50 ms; task age 1000 ms; concurrency 4; queue 100; retry NONE; late result DISCARD. Timeout, exception, rejection, stale data and circuit-open keep primary.

## Credential/Network

Only Operations-owned, environment-specific, short-lived non-production workload identity may be used. Maximum credential TTL 1 hour; secret manager storage; TLS 1.2+; deny-by-default egress; explicit non-production allowlist. Production route/credential and write/owner DB credentials are blocked.

## Identity/Privacy

Synthetic or explicit non-production test account only. Privacy/Security owns an encrypted allowlist. Entry TTL 30 days; audit 90 days; immediate deletion/invalidation; no raw IDs in logs or metrics; fail closed. Actual production identity and inferred subject fallback are blocked.

## P1 Result Boundary

`P1_RUNTIME_DARK_READ_ONLY`, `CURRENT_P1_AUTHORITY_UNCHANGED`, `P1_SHADOW_RESULT_NOT_SERVED`. Preserve expected/protected gaps and prohibit aggregate-to-event fabrication.

## P2 Result Boundary

`P2_RUNTIME_DARK_READ_ONLY`, `CURRENT_P2_AUTHORITY_UNCHANGED`, `P2_SHADOW_RESULT_NOT_SERVED`, `NO_AUTHORITY_TRANSFER`. Preserve exposure authority, 604800-second window, click/like/save/share, bound-run fallback, migration gaps, and protected dataset/release boundaries.

## Checkpoint/Lineage

Opaque monotonic checkpoint, UTC capture, schema/deployment/artifact versions and lineage fingerprint are required. `RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT`; no live lag threshold is invented.

## Observability

Lane-separated low-cardinality metrics. Metrics 30 days; redacted logs 14 days; exact-head artifacts 90 days. Critical failures 100%; success detail sampling at most 10%. Raw result, raw identity and credential retention are NONE.

## Rollback

`LEVEL_1=FLAG_OFF`, `LEVEL_2=LANE_KILL_SWITCH`, `LEVEL_3=GLOBAL_SHADOW_DISABLE`, `LEVEL_4=CONFIG_ROLLBACK`, `LEVEL_5=DEPLOYMENT_ROLLBACK`, `LEVEL_6=CREDENTIAL_REVOKE`, `LEVEL_7=NETWORK_ROUTE_REVOKE`.

## DB/SQL Impact

No DB change, SQL allocation, table, view, role, grant or persisted evidence. An application-level runtime query registry is required. Any persistent DB requirement returns `RCA2_ENTRY_BLOCKED_BY_SQL_ALLOCATION`; no SQL is created.

## Production Impact

None. Production serving, traffic, route, credential, identity, DB access, activation, cutover and authority transfer remain forbidden.

## Verification

SC-5 verifies governance only. Runtime dark read, feature-flag runtime test, credential test, production route test, canary, load, replay, production validation, actual identity mapping, activation and authority transfer are `NOT_EXECUTED`.

## Risks

The async boundary is unimplemented; freshness threshold lacks measurements; P1 semantic gaps and P2 migration gaps remain; production identity, credentials, route, query plans, load and privacy review remain unresolved.

## Exit Criteria

Future RCA-2 exit requires isolated runtime execution, preserved primary authority, no served shadow, default-off flag, enforced traffic/resource/fallback/breaker/kill/identity/checkpoint/lineage/observability/rollback boundaries, no response mutation, no DB write and no authority transfer.

## Handoff

Implement in a separate Draft PR. Do not enable nonzero traffic, mark Ready, merge, access production or start authority-transfer review without explicit approvals and exact-head evidence.