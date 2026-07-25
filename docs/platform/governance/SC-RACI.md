# SC RACI

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-raci-v1` |
| status | `ACTIVE / SC-5 RCA-2 ALIGNED` |
| authoritative main/work-start | `3efbf96ebf25ae1645a62f35269c4b569425a9ca` |
| RCA-1B exact-final-head | `dbb6b5397ad0fe675856b195e280faf9a0f3030c` |

## Baseline

`RCA1B_NONPRODUCTION_READONLY_RECONCILIATION_COMPLETE`, `CROSS_VERSION_RESULT_EQUIVALENCE=PASS`, `READ_ONLY_BOUNDARY=ENFORCED`, `QUERY_ALLOWLIST=ENFORCED`, `CHECKPOINT_BOUNDARY=ENFORCED`, `LINEAGE_BOUNDARY=ENFORCED`.

## RCA-2 RACI

| Area | Responsible | Accountable | Consulted | Informed | Approval |
|---|---|---|---|---|---|
| P1 runtime semantics and mismatch classes | Intelligence | Intelligence | Data/SC | Reliability/Operations | `BLOCKING_APPROVAL` |
| P1 expected/protected gaps and exit | Intelligence | Intelligence | Data/SC | team | `BLOCKING_APPROVAL` |
| P2 exposure/window/event/fallback | Reliability | Reliability | Data/SC | Intelligence/Operations | `BLOCKING_APPROVAL` |
| P2 migration gaps, failure policy and evidence integrity | Reliability | Reliability | Operations/Privacy/SC | team | `BLOCKING_APPROVAL` |
| candidate contract/checkpoint/lineage/freshness measurement | Data | Data | lane owners/Operations/SC | team | `REQUIRED` |
| isolated non-production environment and deployment | Operations | Operations | Security/SC | lane owners | `BLOCKING_APPROVAL` |
| flag, traffic, executor, timeout, breaker and kill switches | Operations | Operations | Reliability/SC | team | `BLOCKING_APPROVAL` |
| workload credential and network allowlist | Operations | Operations | Privacy/Security/SC | lane owners | `BLOCKING_APPROVAL` |
| identity mode, redaction, retention and incident response | Privacy/Security | Privacy/Security | Operations/SC/lane owners | team | `BLOCKING_APPROVAL` |
| registry, entry/exit, SQL, rollout ceiling and authority | SC | SC | all tracks | team | `BLOCKING_APPROVAL` |
| production dark read | NOT ALLOCATED | SC | all tracks | team | `NOT_AUTHORIZED` |
| authority transfer | NOT ALLOCATED | SC | all tracks | team | `FORBIDDEN` |

## Rules

- P1 and P2 are independent lanes with separate breakers, counters, dashboards, mismatch inventories and exit recommendations.
- Intelligence cannot approve P2 semantics; Reliability cannot transfer P1 authority.
- Data candidate results remain non-authoritative.
- Operations may deploy but cannot enable without SC and blocking approvals.
- Privacy/Security approval is required before test-account identity or retained evidence.
- Physical implementation location does not transfer semantic authority.
- `RP` is reserved for Reliability Platform; RCA is a workstream.

## Enablement boundary

```text
RCA2_ENTRY_AUTHORIZED
RCA2_EXECUTION_ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME
RCA2_RUNTIME_MODEL=ASYNC_POST_RESPONSE_SHADOW
FEATURE_FLAG_DEFAULT=OFF
INITIAL_TRAFFIC_PERCENT=0
MAX_PRODUCTION_DARK_READ_PERCENT=0
PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_SERVING=FORBIDDEN
SHADOW_FAILURE_FALLBACK=KEEP_PRIMARY_RESULT
IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY
RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT
SQL_ALLOCATION=NOT_REQUIRED
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
AUTHORITY_TRANSFER=FORBIDDEN
```

Current sources remain `RecommendationP1ProfileSource` and `RecommendationP2ObservationSource`.

## DB and identity boundary

No persistent DB role/grant, SQL migration, production credential, production route or actual identity responsibility is allocated by SC-5.

## Exit boundary

RCA-2 exit requires exact-head evidence and all blocking approvals. Production activation, serving and authority transfer remain separate and unauthorized.
