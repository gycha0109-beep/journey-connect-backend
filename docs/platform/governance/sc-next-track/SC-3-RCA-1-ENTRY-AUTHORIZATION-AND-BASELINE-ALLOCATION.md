# SC-3 RCA-1 Shadow Reconciliation Entry Authorization & Baseline Allocation

## Scope

This System Coordination phase authorizes and allocates the governance boundary for `RCA-1 Recommendation Data Shadow Reconciliation`. It is not an RCA-1 implementation.

The phase changes governance documents, registries, decision evidence, verifier logic and an implementation handoff only. Java/Kotlin production source, RCA-0 behavior, P1/P2 source, DB/SQL, runtime wiring, identity mapping and production controls are outside scope.

## Current Baseline

```text
AUTHORITATIVE_MAIN=f802a105e46a62718616acaa7a3db6c172e7ed10
RCA0_EXACT_FINAL_HEAD=d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d
RCA0_CONTRACT_AND_FIXTURE_COMPLETE
DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE
CANONICAL_SQL=01..52_PROTECTED
SQL_53_PLUS=UNALLOCATED
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
```

PR #23 is merged. The RCA-0 final tree and the `main` merge tree are identical.

Current authority:

```text
P1_SOURCE=RecommendationP1ProfileSource
P1_RESULT=recommendation_p1_profile_snapshot
P2_SOURCE=RecommendationP2ObservationSource
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
P2_DATASET=recommendation-evaluation-dataset-v1
P2_METRICS=engagement_rate,fallback_rate
```

Data candidates `recommendation-profile-input-v1` and `experiment-outcome-input-v1`, and RCA consumer contracts, remain non-authoritative.

## Decision

| Decision area | Verdict | Decision |
|---|---|---|
| RCA-1 purpose | `APPROVED` | deterministic non-production source-by-source comparison and classification |
| Model A | `APPROVED` | official RCA-1 model |
| Model B | `DEFERRED` | separate RCA-1B/SC approval |
| Model C | `BLOCKED` | RCA-2 candidate |
| P1 lane | `CONDITIONALLY_APPROVED` | exact/derived parity plus explicit expected gaps |
| P2 lane | `CONDITIONALLY_APPROVED` | exact exposure/window/event/fallback protection |
| identity mode | `APPROVED` | `SYNTHETIC_ONLY` |
| real identity mapping | `BLOCKED/DEFERRED` | governance unresolved |
| evidence/privacy | `APPROVED` | synthetic/redacted, no raw identities or payloads |
| DB/SQL | `NOT_REQUIRED` | no object, role, grant or allocation |
| runtime wiring | `BLOCKED` | separate RCA-2 decision |
| production activation | `BLOCKED` | unchanged |

Canonical decision:

```text
RCA1_ENTRY_AUTHORIZED
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
IDENTITY_MODE=SYNTHETIC_ONLY
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

## Rationale

Model A is the only model that can validate deterministic comparison logic while preserving the unresolved identity boundary and avoiding DB/runtime authority. Model B adds credential, role, checkpoint and privacy obligations that require separate approval. Model C changes runtime behavior and operational blast radius and therefore belongs to RCA-2.

P1 cannot be reduced to object equality because the Data aggregate omits event ordering, event grain, explicit preferences and profile transform semantics. P2 can require exact parity for authoritative exposure bindings and metric inputs while leaving persisted dedupe, canonical hash and release evidence protected.

## Authority

- Intelligence: P1 semantics, expected gaps, transform interpretation and P1 acceptance.
- Reliability: P2 exposure/outcome/metric semantics, dedupe/hash/release protection and P2 acceptance.
- Data: candidate projection, checkpoint and lineage contract interpretation.
- System Coordination: entry/exit, registry, breaking change and authority transfer.
- Operations: no execution role for Model A; consulted for production-control protection.
- Privacy/Security: evidence policy review; no real identity material.

## Dependencies

- merged PR #23 and RCA-0 handoff;
- RCA-0 contracts and 12 P1 / 21 P2 fixtures;
- current P1/P2 source markers;
- Data candidate contracts;
- protected SQL and production defaults;
- exact-final-head SC verifier.

No DB, runtime credential, feature flag, scheduler or production traffic dependency exists for Model A.

## Allowed Changes

For the later RCA-1 implementation PR:

- pure Java/offline reconciliation types;
- deterministic normalization;
- recorded authoritative reference snapshots;
- Data candidate fixtures;
- lane-specific comparison dimensions;
- result taxonomy and redacted evidence;
- RCA-0 fixture extension without changing RCA-0 expected classifications;
- offline counters and protected regressions.

## Forbidden Changes

- P1/P2 source, result, exposure, dataset, metric, hash or release change;
- fake aggregate-to-event stream;
- Spring, repository, DB query, worker, listener, scheduler or feature flag;
- SQL, migration, role or grant;
- real identity mapping;
- runtime dark read;
- production traffic or authority transfer;
- main direct push or automatic merge.

## Identity/Privacy

RCA-1 Model A uses synthetic identities only. The synthetic binding is purpose-bound to offline reconciliation, stored only in static test fixtures, and excluded from evidence except as a hashed case identifier.

All invalid mapping states fail closed. Real identity reconciliation remains blocked until owner, authority, encrypted storage, retention, deletion, invalidation, audit, purpose binding, logging and failure policy are separately approved.

## DB/SQL Impact

```text
DB_CHANGE_REQUIRED=NO
SQL_ALLOCATION_REQUIRED=NO
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=NO
NEW_GRANT_REQUIRED=NO
```

SQL `01..52` remains protected. SQL `53+` remains unallocated. A later discovered DB need produces `RCA1_ENTRY_BLOCKED_BY_SQL_ALLOCATION` for that sub-scope.

## Production Impact

None. Model A has no runtime source read, feature flag, traffic, production identity or deployment. GATE-3 through GATE-9 remain unchanged.

## Verification

SC-3 independent verification must confirm:

- exact work-start and PR #23 merge ancestry/tree;
- RCA-0 handoff/contracts/fixture counts;
- official RCA/RP naming;
- P1/P2 authority markers;
- SQL and production controls;
- single Model A and synthetic-only decisions;
- lane taxonomies and forbidden results;
- no authority transfer or runtime wiring;
- required documents and handoff;
- governance-only diff;
- unchanged historical RCA-0 evidence.

PostgreSQL, actual shadow comparison, runtime, canary, load, replay and production are `NOT_EXECUTED`.

## Risks

- physical real-identity mapping governance remains unresolved;
- P1 non-comparable semantics can be mislabeled as defects if the lane taxonomy is ignored;
- P2 stale assignment, persisted dedupe and canonical hash remain migration dimensions;
- deterministic fixtures do not validate source freshness or checkpoint lag;
- future Model B/C work can be incorrectly inferred from Model A unless phase markers are enforced.

## Exit Criteria

SC-3 exits when all governance documents and machine evidence are complete, the verifier passes on the exact final PR head, the PR remains unmerged, and the implementation prompt is usable without additional scope invention.

RCA-1 exits only after:

```text
P1_RECONCILIATION_EXECUTED
P1_DIFFERENCES_CLASSIFIED
P2_RECONCILIATION_EXECUTED
P2_DIFFERENCES_CLASSIFIED
IDENTITY_BOUNDARY_ENFORCED
PROTECTED_AUTHORITY_UNCHANGED
NO_PRODUCTION_TRAFFIC
NO_AUTHORITY_TRANSFER
```

## Handoff

Use [22-RCA-1-IMPLEMENTATION-HANDOFF-PROMPT.md](22-RCA-1-IMPLEMENTATION-HANDOFF-PROMPT.md) only after this SC-3 PR is explicitly approved and merged. Any implementation before merge is blocked.
