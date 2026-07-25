# Journey Connect System Contract V1

## 1. Document identity

| Field | Value |
|---|---|
| contract ID | `jc-system-contract-v1` |
| revision | `V1.6 / SC-5 RCA-2 ENTRY` |
| status | `ACTIVE / RCA1B_COMPLETE / RCA2_ENTRY_AUTHORIZED` |
| authoritative main/work-start | `3efbf96ebf25ae1645a62f35269c4b569425a9ca` |
| RCA-1B exact-final-head | `dbb6b5397ad0fe675856b195e280faf9a0f3030c` |
| classification | `JOINT_INTELLIGENCE_RELIABILITY_OPERATIONS_ADOPTION` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| SQL `53+` | `UNALLOCATED` |
| date | `2026-07-25` |

## 2. Authoritative baseline

- PR #27 is merged at `3efbf96ebf25ae1645a62f35269c4b569425a9ca`.
- RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c` is an ancestor and has an identical tree to the merge commit.
- `RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE`.
- `POSTGRESQL_VERSION_MATRIX=15,18`; `CROSS_VERSION_RESULT_EQUIVALENCE=PASS`.
- P1 is `RECONCILED_WITH_EXPECTED_GAPS`; P2 is `RECONCILED_WITH_MIGRATION_GAPS`.
- `READ_ONLY_BOUNDARY=ENFORCED`, `QUERY_ALLOWLIST=ENFORCED`, `CHECKPOINT_BOUNDARY=ENFORCED`, `LINEAGE_BOUNDARY=ENFORCED`.
- No production database, traffic, credential, route, identity mapping or activation occurred.
- SQL `01..52` remains protected; SQL `53+` remains absent/unallocated.
- `CURRENT_P1_P2_AUTHORITY_UNCHANGED`; `NO_AUTHORITY_TRANSFER`.

## 3. Current Recommendation authority

```text
P1_SOURCE=RecommendationP1ProfileSource
P1_RESULT=recommendation_p1_profile_snapshot
P2_SOURCE=RecommendationP2ObservationSource
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
P2_DATASET=recommendation-evaluation-dataset-v1
P2_METRICS=engagement_rate,fallback_rate
```

## 4. Workstream naming

`RCA` is the Recommendation Consumer Adoption cross-track workstream. `RP` is reserved for Reliability Platform and never means Recommendation Platform.

## 5. RCA sequence

```text
RCA-0 contract and fixture alignment [COMPLETE / MERGED]
RCA-1 offline deterministic reconciliation [COMPLETE]
RCA-1B non-production read-only reconciliation [COMPLETE / MERGED]
RCA-2 controlled runtime dark read [ENTRY AUTHORIZED / IMPLEMENTATION NOT STARTED]
production dark read [BLOCKED / SEPARATE SC APPROVAL]
authority transfer [FORBIDDEN / SEPARATE REVIEW]
```

## 6. SC-5 entry decision

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

`RCA2_ENTRY_AUTHORIZED` authorizes a separate implementation PR for an isolated non-production, default-off, zero-initial-traffic shadow path only. It does not authorize execution, production traffic, serving or transfer.

## 7. Runtime environment and model

```text
ENVIRONMENT_A=REQUIRED_CI_RUNTIME_SIMULATION
ENVIRONMENT_B=APPROVED_ISOLATED_NON_PRODUCTION_RUNTIME
ENVIRONMENT_C=BLOCKED_PRODUCTION_DARK_READ
RCA2_RUNTIME_MODEL=ASYNC_POST_RESPONSE_SHADOW
MODEL_A=TEST_FALLBACK_ONLY
MODEL_C=BLOCKED_PENDING_QUEUE_EVENT_CONTRACT
```

The executor is dedicated, bounded and invoked only after the authoritative response is committed. The repository currently has no validated asynchronous boundary; implementation must create and prove it before enablement.

## 8. Feature flag and traffic

```text
FEATURE_FLAG_REQUIRED=YES
FEATURE_FLAG_DEFAULT=OFF
FAIL_CLOSED_ON_UNKNOWN_FLAG=YES
LOCAL_DEFAULT_ENABLE=NO
ENVIRONMENT_OVERRIDE_REQUIRED=YES
RUNTIME_ENABLE_WITHOUT_SC_APPROVAL=FORBIDDEN
INITIAL_TRAFFIC_PERCENT=0
MAX_NONPRODUCTION_TRAFFIC_PERCENT=100_STAGED
MAX_PRODUCTION_DARK_READ_PERCENT=0
```

Missing, stale, expired or malformed flag state resolves OFF. Global and lane kill switches override every enable flag. Deployment and enablement are separate approvals.

## 9. Primary and shadow authority

```text
PRIMARY_RESULT_SOURCE=CURRENT_AUTHORITATIVE_SOURCE
PRIMARY_RESULT_MUTATION=FORBIDDEN
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_USER_VISIBLE=NO
SHADOW_RESULT_SERVING=FORBIDDEN
SHADOW_RESULT_FALLBACK=FORBIDDEN
SHADOW_RESULT_CACHE_WRITE=FORBIDDEN
SHADOW_RESULT_DATABASE_WRITE=FORBIDDEN
SHADOW_RESULT_EVENT_EMISSION=FORBIDDEN
SHADOW_RESULT_NOTIFICATION=FORBIDDEN
SHADOW_RESULT_RANKING_FEEDBACK=FORBIDDEN
SHADOW_FAILURE_USER_IMPACT=NONE
```

## 10. Runtime resource and failure contract

```text
SHADOW_CONNECTION_TIMEOUT_MS=100
SHADOW_READ_TIMEOUT_MS=300
SHADOW_TOTAL_TIMEOUT_MS=500
TASK_QUEUE_TIMEOUT_MS=50
MAX_TASK_AGE_MS=1000
MAX_SHADOW_CONCURRENCY=4
MAX_SHADOW_QUEUE_DEPTH=100
LATE_RESULT_POLICY=DISCARD
RETRY_POLICY=NONE
PRIMARY_TIMEOUT_UNCHANGED=YES
SHADOW_TIMEOUT_CANNOT_EXTEND_PRIMARY_BUDGET=YES
CIRCUIT_BREAKER_REQUIRED=YES
GLOBAL_KILL_SWITCH_REQUIRED=YES
LANE_KILL_SWITCH_REQUIRED=YES
```

Timeout, exception, queue rejection, stale data and circuit-open preserve the primary result. Breakers are independent for P1 and P2.

## 11. Credential, network and DB boundary

```text
RUNTIME_DB_ACCESS_REQUIRED=NO
PRODUCTION_ROUTE_ALLOWED=NO
TLS_REQUIRED=YES
CREDENTIAL_OWNER=OPERATIONS
CREDENTIAL_SCOPE=ENVIRONMENT_SPECIFIC_NONPRODUCTION_LEAST_PRIVILEGE
CREDENTIAL_STORAGE=PLATFORM_SECRET_MANAGER
CREDENTIAL_MAX_TTL_SECONDS=3600
NETWORK_POLICY=DENY_BY_DEFAULT_EXPLICIT_NONPRODUCTION_ALLOWLIST
DB_CHANGE_REQUIRED=NO
SQL_ALLOCATION_REQUIRED=NO
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=NO
NEW_GRANT_REQUIRED=NO
```

RCA-1B `rca1b_readonly` is test-only and cannot become a runtime role. A persistent DB object/role/grant requirement blocks entry by SQL allocation and no SQL may be authored in RCA-2 implementation.

## 12. Runtime query boundary

The seven RCA-1B queries remain test-only. RCA-2 requires `recommendation-runtime-dark-read-query-registry-v1` as an application-level versioned contract with static identifiers, finite limits, deterministic normalization, parameter provenance, checkpoint/lineage dependencies and redaction classification. Dynamic SQL, owner queries, raw identity queries and production DB queries are not authorized.

## 13. Identity and privacy

```text
IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY
IDENTITY_OWNER=PRIVACY_SECURITY
IDENTITY_AUTHORITY=RCA2_NONPRODUCTION_TEST_ACCOUNT_ALLOWLIST_V1
IDENTITY_PURPOSE_BINDING=RCA2_ISOLATED_NONPRODUCTION_DARK_READ_ONLY
IDENTITY_STORAGE=ENCRYPTED_ENVIRONMENT_IDENTITY_REGISTRY
IDENTITY_FAILURE_POLICY=FAIL_CLOSED_KEEP_PRIMARY
ACTUAL_PRODUCTION_IDENTITY=BLOCKED
RAW_IDENTITY_RETENTION=NONE
RAW_RESULT_RETENTION=NONE
CREDENTIAL_RETENTION=NONE
```

Anonymous, nearest-user, alternate-subject and inferred-ID fallback are forbidden.

## 14. P1 runtime lane

```text
P1_RUNTIME_DARK_READ_ONLY
CURRENT_P1_AUTHORITY_UNCHANGED
P1_SHADOW_RESULT_NOT_SERVED
```

Compare normalized digests, result size, shared and derived fields, 7/30/90 windows, checkpoint, lineage, latency and failure classification. Existing expected/protected gaps remain excluded from unexpected mismatch. Aggregate-to-event fabrication is forbidden.

## 15. P2 runtime lane

```text
P2_RUNTIME_DARK_READ_ONLY
CURRENT_P2_AUTHORITY_UNCHANGED
P2_SHADOW_RESULT_NOT_SERVED
NO_AUTHORITY_TRANSFER
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
OUTCOME_WINDOW_SECONDS=604800
ENGAGEMENT_EVENTS=click,like,save,share
FALLBACK_SOURCE=BOUND_RECOMMENDATION_RUN_ONLY
ONE_OBSERVATION_KEY=experimentRef,experimentVersion,subjectRef
```

`STALE_UNEXPOSED_ASSIGNMENT_GAP` and `OBSERVATION_DEDUPE_GAP` remain migration gaps. `CANONICAL_DATASET_HASH_PROTECTED` and `RELEASE_EVIDENCE_PROTECTED` remain inaccessible.

## 16. Checkpoint, lineage and freshness

```text
CHECKPOINT_REQUIRED=YES
LINEAGE_FINGERPRINT_REQUIRED=YES
RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT
INCOMPATIBLE_CHECKPOINT_POLICY=KEEP_PRIMARY_AND_CLASSIFY
CLOCK_SOURCE=UTC_MONOTONIC_CAPTURE
```

No live lag threshold is invented. Isolated non-production measurement must establish evidence before a threshold proposal.

## 17. Observability and retention

Required metrics are lane-separated and low-cardinality. Raw user/subject/session/run/exposure IDs, recommendation content, DB rows, query parameters, credentials, connection strings, endpoints, canonical dataset rows and release evidence are forbidden in logs or metrics. Metrics retain 30 days, redacted logs 14 days, exact-head review artifacts 90 days. Operational telemetry is not a P2 product metric or SLO.

## 18. Alert, kill switch and rollback

Critical conditions—response mutation, candidate write attempt, authority mismatch, redaction failure, production route or traffic-ceiling breach—trigger immediate global disable. Timeout/exception/queue and semantic failures trigger lane breakers. Rollback order is flag OFF, lane kill, global disable, config rollback, deployment rollback, credential revoke and network route revoke.

## 19. Approval boundary

| Role | Approval |
|---|---|
| Intelligence | `BLOCKING_APPROVAL` |
| Reliability | `BLOCKING_APPROVAL` |
| Data | `REQUIRED` |
| Operations | `BLOCKING_APPROVAL` |
| Privacy/Security | `BLOCKING_APPROVAL` |
| System Coordination | `BLOCKING_APPROVAL` |

No nonzero traffic is allowed before all blocking approvals are exact-head bound.

## 20. Production and authority-transfer gates

RCA-2 completion does not imply production activation, full traffic, candidate serving, cutover, source deprecation, actual identity approval, load/scale completion, migration-gap resolution or authority transfer. Production dark read and any transfer require separate System Coordination review.

## 21. Absolute prohibitions

- production serving, traffic, route, credential, DB access or activation;
- primary response mutation, blending or candidate fallback;
- actual identity or inferred subject mapping;
- persistent database object, role or grant;
- canonical SQL change or SQL `53+` allocation;
- P2 canonical dataset/hash/release access;
- dynamic/unbounded query, unbounded retry or queue;
- automatic rollout, main direct push or automatic merge.
