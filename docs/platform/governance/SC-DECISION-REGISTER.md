# SC Decision Register

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-decision-register-v1` |
| status | `ACTIVE / SC-5 RCA-2 ENTRY AUTHORIZED` |
| authoritative main/work-start | `3efbf96ebf25ae1645a62f35269c4b569425a9ca` |
| RCA-1B exact-final-head | `dbb6b5397ad0fe675856b195e280faf9a0f3030c` |
| updated | `2026-07-25` |

## Historical decisions retained

- Data Platform closure and SQL `01..52` protection remain complete.
- SQL `53+` remains unallocated.
- RCA-0, RCA-1 and RCA-1B are complete and merged as applicable.
- `RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE` and `CROSS_VERSION_RESULT_EQUIVALENCE=PASS` remain authoritative.
- `READ_ONLY_BOUNDARY=ENFORCED`, `QUERY_ALLOWLIST=ENFORCED`, `CHECKPOINT_BOUNDARY=ENFORCED`, `LINEAGE_BOUNDARY=ENFORCED`.
- `RP` is reserved for Reliability Platform.
- P1 `RecommendationP1ProfileSource` and P2 `RecommendationP2ObservationSource` authority remains unchanged.
- RCA-1B historical source, tests, evidence and artifacts are immutable.

## SC-5 decisions

| Decision ID | Decision | Status | Restriction |
|---|---|---|---|
| `SC-RCA2-001` | authorize RCA-2 implementation entry | APPROVED | separate Draft PR |
| `SC-RCA2-002` | Environment B isolated non-production | APPROVED | no production data/route/identity |
| `SC-RCA2-003` | Environment A CI simulation | REQUIRED | implementation verification |
| `SC-RCA2-004` | Environment C production dark read | BLOCKED | separate SC approval |
| `SC-RCA2-005` | async post-response model | APPROVED | dedicated bounded executor |
| `SC-RCA2-006` | queue/event model | BLOCKED | queue/event contract absent |
| `SC-RCA2-007` | feature flag | REQUIRED | default OFF; stale/unknown OFF |
| `SC-RCA2-008` | initial traffic | APPROVED | 0% |
| `SC-RCA2-009` | non-production staged ceiling | CONDITIONALLY APPROVED | manual 1/10/50/100 stages |
| `SC-RCA2-010` | production traffic ceiling | APPROVED | 0% |
| `SC-RCA2-011` | primary authority | PROTECTED | current P1/P2 only |
| `SC-RCA2-012` | shadow authority | NONE | no serving/fallback/write/event |
| `SC-RCA2-013` | timeout/resources | APPROVED | finite 100/300/500 ms, 4/100 |
| `SC-RCA2-014` | retry | FORBIDDEN | NONE |
| `SC-RCA2-015` | circuit breakers | REQUIRED | lane + global |
| `SC-RCA2-016` | kill switches | REQUIRED | lane + global |
| `SC-RCA2-017` | credential/network | CONDITIONALLY APPROVED | non-production workload identity only |
| `SC-RCA2-018` | production credential/route | BLOCKED | Operations/Security/SC review |
| `SC-RCA2-019` | identity | CONDITIONALLY APPROVED | synthetic/test account only |
| `SC-RCA2-020` | actual production identity | BLOCKED | separate privacy governance |
| `SC-RCA2-021` | P1 runtime lane | CONDITIONALLY APPROVED | expected gaps preserved |
| `SC-RCA2-022` | P2 runtime lane | CONDITIONALLY APPROVED | exposure/window/event/fallback protected |
| `SC-RCA2-023` | runtime freshness | MEASUREMENT_ONLY | threshold blocked pending evidence |
| `SC-RCA2-024` | observability/redaction | REQUIRED | low cardinality, no raw IDs/content |
| `SC-RCA2-025` | alert/automatic disable | REQUIRED | critical violations global kill |
| `SC-RCA2-026` | deployment/enable separation | REQUIRED | deploy does not enable |
| `SC-RCA2-027` | rollback hierarchy | REQUIRED | seven levels |
| `SC-RCA2-028` | DB/SQL | NOT_REQUIRED | no persistent objects/roles/grants |
| `SC-RCA2-029` | runtime query registry | REQUIRED | application contract only |
| `SC-RCA2-030` | persisted evidence | NOT_REQUIRED | existing observability |
| `SC-RCA2-031` | production activation | NOT AUTHORIZED | separate SC decision |
| `SC-RCA2-032` | authority transfer | FORBIDDEN | separate review after RCA-2 exit |

## Explicit decision block

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

## Blocking approvals

Intelligence, Reliability, Operations, Privacy/Security and System Coordination are `BLOCKING_APPROVAL`. Data is `REQUIRED`. These approvals gate nonzero traffic and stage increases; SC-5 technical verification does not imply approval.

## Deferred and blocked

- safe async executor implementation and runtime tests;
- live freshness threshold;
- production credential, route, identity, canary, load and validation;
- persistent DB object/role/grant or SQL allocation;
- queue/event infrastructure;
- candidate serving, cutover, source deprecation and authority transfer.
