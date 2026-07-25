# Journey Connect Track Governance V1

## 1. Document identity

| Field | Value |
|---|---|
| revision | `V1.6 / SC-5 RCA-2 ENTRY` |
| status | `ACTIVE / RCA1B_COMPLETE / RCA2_ENTRY_AUTHORIZED` |
| authoritative main/work-start | `3efbf96ebf25ae1645a62f35269c4b569425a9ca` |
| RCA-1B exact-final-head | `dbb6b5397ad0fe675856b195e280faf9a0f3030c` |
| classification | `JOINT_INTELLIGENCE_RELIABILITY_OPERATIONS_ADOPTION` |
| SQL `53+` | `UNALLOCATED` |

## 2. Authoritative sequence

```text
Data Platform closure [COMPLETE]
→ RCA-0 [COMPLETE / MERGED]
→ RCA-1 [COMPLETE]
→ RCA-1B [COMPLETE / MERGED]
→ SC-5 RCA-2 entry [AUTHORIZED]
→ RCA-2 implementation [SEPARATE DRAFT PR]
→ production dark read [BLOCKED / SEPARATE APPROVAL]
→ authority-transfer review [NOT STARTED]
```

This is not a production release plan.

## 3. Track responsibilities

### Intelligence
Owns P1 authoritative semantics, expected/protected gaps, runtime mismatch classification and P1 exit recommendation. Approval is blocking.

### Reliability
Owns P2 exposure/window/event/fallback semantics, migration-gap treatment, runtime failure policy, evidence integrity and P2 exit recommendation. Approval is blocking.

### Data
Owns candidate runtime contract, checkpoint, lineage, schema/version compatibility and freshness measurement. Participation is required; Data cannot transfer P1/P2 authority.

### Operations
Owns isolated runtime environment, deployment, feature flags, workload credentials, network allowlist, bounded executor, timeouts, breakers, traffic stages, observability and rollback. Approval is blocking.

### Privacy/Security
Owns synthetic/test-account identity, credential security, encryption, redaction, retention, audit and incident response. Approval is blocking.

### System Coordination
Owns entry/exit, registry, breaking changes, SQL allocation, rollout ceiling, production gate and authority transfer. Approval is blocking.

## 4. Naming

`RCA` means Recommendation Consumer Adoption and is a cross-track workstream. `RP` remains reserved for Reliability Platform and never means Recommendation Platform.

## 5. Baseline retained

`RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE`, `CROSS_VERSION_RESULT_EQUIVALENCE=PASS`, P1 `RECONCILED_WITH_EXPECTED_GAPS`, P2 `RECONCILED_WITH_MIGRATION_GAPS`, `READ_ONLY_BOUNDARY=ENFORCED`, `QUERY_ALLOWLIST=ENFORCED`, `CHECKPOINT_BOUNDARY=ENFORCED`, `LINEAGE_BOUNDARY=ENFORCED`, no production DB/traffic and unchanged authority are retained.

## 6. RCA-2 execution decision

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

Environment A remains CI support. Environment B is the only implementation target. Environment C is blocked. Model B is selected; Model C requires a separate queue/event contract.

## 7. Enablement governance

Code deployment does not enable shadow execution. Feature flag, lane switches, traffic stage and deployment are separate states. Unknown/stale configuration fails OFF. Nonzero traffic requires all blocking approvals and stage-specific evidence.

## 8. Resource governance

```text
SHADOW_CONNECTION_TIMEOUT_MS=100
SHADOW_READ_TIMEOUT_MS=300
SHADOW_TOTAL_TIMEOUT_MS=500
TASK_QUEUE_TIMEOUT_MS=50
MAX_SHADOW_CONCURRENCY=4
MAX_SHADOW_QUEUE_DEPTH=100
RETRY_POLICY=NONE
```

Dedicated executor only; primary budget cannot be extended. Lane breakers prevent P1 and P2 failures from masking each other.

## 9. Traffic governance

Non-production stages are 0%, 1%, 10%, 50% and 100%, each with minimum duration/request counts, zero critical violations and bounded timeout/exception thresholds. Production remains 0%. Stage progression is manual and audited.

## 10. Authority governance

Current authority remains:
```text
P1_SOURCE=RecommendationP1ProfileSource
P2_SOURCE=RecommendationP2ObservationSource
```
Primary remains current P1/P2. Shadow has no serving, fallback, cache, write, event, notification or feedback authority. Better-looking candidate results cannot replace or repair the response.

## 11. Credential, identity and network governance

Only environment-specific non-production workload identity, short-lived credentials, TLS and explicit allowlist are permitted. Identity is synthetic or explicit non-production test account only. Production identity, route and credential remain blocked.

## 12. Query and DB governance

RCA-1B queries remain test-only. A new application contract registry is required; production DB runtime queries are not authorized. No persistent table, view, role, grant or evidence store is allocated. Any such requirement returns to SC as `RCA2_ENTRY_BLOCKED_BY_SQL_ALLOCATION`.

## 13. Lane governance

P1 and P2 produce independent runtime classifications, counters, breakers, dashboards and exit recommendations. A combined overall PASS cannot hide either lane.

## 14. Freshness governance

`RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT`. Non-production runtime may measure checkpoint lag but cannot define production tolerance without Data, Operations, lane-owner and SC approval.

## 15. Evidence governance

Only redacted digests, bounded classes, checkpoint/lineage versions, latency buckets, flag/config/deployment versions and exact SHA are permitted. Raw IDs/content/rows/parameters/secrets/endpoints are prohibited.

## 16. Rollback governance

Flag OFF is fastest. Lane kill and global disable must work without primary rollback. Config, deployment, credential and network rollback are independently verifiable and audited.

## 17. Entry and exit

Entry authorizes implementation only. Exit requires controlled isolated non-production execution and every listed boundary. Exit does not authorize production, serving, cutover, actual identity or authority transfer.

## 18. Refusal conditions

Reject default-on flags, nonzero initial traffic, unbounded resources, retry storms, missing breaker/kill/rollback, raw identity logging, combined P1/P2 verdicts, production route/credential/data, persistent DB changes, SQL `53+`, protected P2 access or transfer language.

## 19. Canonical governance paths

- [System Contract](JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md)
- [Decision Register](SC-DECISION-REGISTER.md)
- [Platform Registry](SC-PLATFORM-REGISTRY.md)
- [RACI](SC-RACI.md)
- [SC Handoff](SC-HANDOFF.md)
- [SC-5 master](sc-next-track/SC-5-RCA-2-ENTRY-AUTHORIZATION-AND-EXECUTION-BOUNDARY.md)
- [RCA-2 implementation prompt](sc-next-track/56-RCA-2-IMPLEMENTATION-HANDOFF-PROMPT.md)
