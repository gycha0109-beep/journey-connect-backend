# SC Contract, Module, Namespace and Sequence Registry

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-platform-registry-v1` |
| status | `ACTIVE / RCA1B_COMPLETE / RCA2_ENTRY_AUTHORIZED` |
| authoritative main/work-start | `3efbf96ebf25ae1645a62f35269c4b569425a9ca` |
| RCA-1B exact-final-head | `dbb6b5397ad0fe675856b195e280faf9a0f3030c` |
| canonical SQL | `01..52` |
| unallocated SQL | `53+` |

## Baseline markers

`RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE`, `CROSS_VERSION_RESULT_EQUIVALENCE=PASS`, `READ_ONLY_BOUNDARY=ENFORCED`, `QUERY_ALLOWLIST=ENFORCED`, `CHECKPOINT_BOUNDARY=ENFORCED`, `LINEAGE_BOUNDARY=ENFORCED`.

## Workstream and phase identifiers

| ID | Meaning | Status |
|---|---|---|
| `RCA` | Recommendation Consumer Adoption cross-track workstream | ACTIVE |
| `RCA-0` | contract/fixture alignment | COMPLETE / MERGED |
| `RCA-1` | offline deterministic reconciliation | COMPLETE |
| `RCA-1B` | non-production read-only reconciliation | COMPLETE / MERGED |
| `SC-5` | RCA-2 entry authorization | ACTIVE |
| `RCA-2` | controlled runtime dark read | ENTRY AUTHORIZED / NOT IMPLEMENTED |
| `RP` | Reliability Platform | PROTECTED ACRONYM |

`RP` is reserved for Reliability Platform and is not a Recommendation workstream name.

## Contract registry

| Contract ID | Owner | Status |
|---|---|---|
| `recommendation-data-consumer-alignment-v1` | SC | ACTIVE / RCA-0 |
| `recommendation-shadow-reconciliation-v1` | lane owners | ACTIVE / RCA-1 |
| `recommendation-shadow-reconciliation-evidence-v1` | Reliability | ACTIVE / RCA-1/RCA-1B |
| `recommendation-runtime-dark-read-boundary-v1` | SC | ALLOCATED / RCA-2 |
| `recommendation-runtime-dark-read-query-registry-v1` | Data + lane owners | REQUIRED / NOT IMPLEMENTED |
| `recommendation-runtime-dark-read-evidence-v1` | Reliability + Privacy | REQUIRED / NOT IMPLEMENTED |

No DB contract or SQL number is allocated by SC-5.

## Module boundary registry

| Boundary | Owner | Status |
|---|---|---|
| current P1 source/result | Intelligence | PROTECTED |
| current P2 source/exposure/dataset/metrics | Reliability | PROTECTED |
| RCA-1B test-only DB adapter and queries | joint | HISTORICAL / TEST ONLY |
| RCA-2 shadow orchestrator | Operations/shared implementation | RESERVED / SEPARATE PR |
| RCA-2 P1 comparator | Intelligence | RESERVED |
| RCA-2 P2 comparator | Reliability | RESERVED |
| RCA-2 checkpoint/lineage adapter | Data | RESERVED |
| RCA-2 flag/breaker/kill/rollback controls | Operations | RESERVED |
| production dark-read trigger | SC | BLOCKED |
| authority-transfer adapter | SC | NOT ALLOCATED |

## Environment registry

```text
RCA2_ENTRY_AUTHORIZED
RCA2_EXECUTION_ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME
RCA2_RUNTIME_MODEL=ASYNC_POST_RESPONSE_SHADOW
CI_RUNTIME_SIMULATION=REQUIRED
PRODUCTION_DARK_READ=BLOCKED
FEATURE_FLAG_DEFAULT=OFF
INITIAL_TRAFFIC_PERCENT=0
MAX_PRODUCTION_DARK_READ_PERCENT=0
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_SERVING=FORBIDDEN
SHADOW_FAILURE_FALLBACK=KEEP_PRIMARY_RESULT
IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY
RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT
```

## Runtime model registry

| Model | Status |
|---|---|
| synchronous isolated shadow | TEST FALLBACK ONLY |
| asynchronous post-response shadow | APPROVED |
| queue/event shadow worker | BLOCKED / SEPARATE CONTRACT |

## Current Recommendation authority

| Meaning | Authority |
|---|---|
| P1 source | `RecommendationP1ProfileSource` |
| P1 result | `recommendation_p1_profile_snapshot` |
| P2 source | `RecommendationP2ObservationSource` |
| P2 exposure | `recommendation_p2_experiment_exposure` |
| P2 dataset | `recommendation-evaluation-dataset-v1` |
| P2 metrics | `engagement_rate`, `fallback_rate` |

`CURRENT_P1_P2_AUTHORITY_UNCHANGED`.

## Identity registry

| Mode | Status |
|---|---|
| synthetic runtime simulation | APPROVED |
| explicit isolated non-production test account | CONDITIONALLY APPROVED / ALLOWLIST |
| pseudonymized production subject | BLOCKED |
| actual production identity | BLOCKED |

## Query registry status

The RCA-1B seven-query registry is `TEST_ONLY`. `recommendation-runtime-dark-read-query-registry-v1` is allocated as an application contract but has zero approved production DB queries. Dynamic SQL and raw identity queries are forbidden.

## Role and credential registry

| Item | Status |
|---|---|
| `rca1b_readonly` | HISTORICAL / EPHEMERAL TEST ONLY |
| RCA-2 persistent DB role | NOT ALLOCATED |
| RCA-2 DB grant | NOT ALLOCATED |
| RCA-2 non-production workload identity | REQUIRED / OPERATIONS OWNED / NOT IMPLEMENTED |
| production credential | BLOCKED |
| production route | BLOCKED |

## DB sequence

| Range | Status |
|---|---|
| `01..52` | PROTECTED |
| `53+` | UNALLOCATED / SC ASSIGNMENT REQUIRED |

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=NO
NEW_GRANT_REQUIRED=NO
PERSISTED_EVIDENCE_REQUIRED=NO
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
```
