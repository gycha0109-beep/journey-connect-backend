# Journey Connect System Contract V1

## 1. Document identity

| Field | Value |
|---|---|
| contract ID | `jc-system-contract-v1` |
| revision | `V1.4 / SC-3 RCA-1 ENTRY` |
| status | `ACTIVE / RCA0_MERGED / RCA1_ENTRY_AUTHORIZED` |
| authoritative main | `f802a105e46a62718616acaa7a3db6c172e7ed10` |
| RCA-0 exact-final-head | `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| SQL `53+` | `UNALLOCATED` |
| date | `2026-07-24` |

This contract governs identity, time, version, source authority, exposure, database sequence, cross-track reconciliation and breaking changes across Data, Intelligence, Operations, Reliability and System Coordination.

## 2. Authoritative state

- PR #23 is merged into `main` at `f802a105e46a62718616acaa7a3db6c172e7ed10`.
- RCA-0 exact-final-head is `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d` and its tree is identical to the merge commit tree.
- `RCA0_CONTRACT_AND_FIXTURE_COMPLETE`.
- `DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE`.
- Data Platform DP-0~DP-7 remains technically closed.
- SQL `01..52` remains immutable and protected.
- SQL `53+` remains absent and unallocated.
- current P1/P2 authority remains unchanged.
- technical completion is not runtime or production approval.

```text
PRODUCTION_ACTIVATION: NOT_AUTHORIZED
```

Protected state:

```text
Production shadow: DISABLED
Kill switch: ENABLED
Sampling: 0 BPS
Cohort: EMPTY
Production Recommendation write: DISABLED
Intelligence runtime activation: DISABLED
Search indexing: DISABLED
Search cutover: NOT_STARTED
Worker: NOT_IMPLEMENTED
Scheduler: DISABLED
Replay: NOT_AUTHORIZED
Backfill: NOT_AUTHORIZED
Automatic rebuild: NOT_AUTHORIZED
Automatic purge: DISABLED
```

## 3. Track boundary

| Area | Semantic owner | Physical arrangement / restriction |
|---|---|---|
| canonical event, ingestion, Data projections, quality and lineage | Data | approved Data functions/roles only |
| P1 profile, Recommendation score/rank/policy/run meaning | Intelligence | current Recommendation package and `jc-recommendation-core` protected |
| P2 experiment exposure, outcome, metric, evaluation and release meaning | Reliability | current P2 physical package/role remains protected |
| deployment, credentials, runtime execution, monitoring and production controls | Operations | not required for RCA-1 Model A |
| registry, entry/exit, breaking change and authority transfer | System Coordination | approval authority |
| RCA implementation | joint Intelligence/Reliability adoption | no authority transfer implied |

Tracks must not directly write another track's tables.

## 4. Canonical DB and SQL sequence

- canonical directory: `database/journey-connect-db-v2.7`;
- SQL `01..52`: immutable closed baseline;
- SQL `25..26`: protected Recommendation P2 evaluation/release;
- SQL `27..28`: protected Search/Operations baseline;
- SQL `29..52`: closed Data Platform baseline;
- SQL `53+`: unallocated;
- RCA-1 Model A requires no DB object, SQL, role or grant;
- any discovered DB need blocks that sub-scope pending a separate SC allocation;
- historical SQL rewrite is prohibited.

## 5. Identity

| Scheme | Wire | Status |
|---|---|---|
| `platform_subject_v1` | `subject:<opaque-id>` | ACTIVE |
| `legacy_user_numeric_v1` | `user:<numeric-id>` | PROTECTED COMPATIBILITY |

The schemes are not equal. RCA-1 uses `SYNTHETIC_ONLY` identity binding.

```text
IDENTITY_MAPPING_OWNER: NOT_REQUIRED_FOR_MODEL_A_SYNTHETIC_ONLY
REAL_IDENTITY_MAPPING_OWNER: DEFERRED
IDENTITY_MAPPING_AUTHORITY: NONE
IDENTITY_MAPPING_STORAGE: STATIC_TEST_FIXTURE_ONLY
IDENTITY_MAPPING_RETENTION: FIXTURE_VERSION_LIFETIME
IDENTITY_MAPPING_DELETION: REMOVE_WITH_FIXTURE_VERSION
IDENTITY_MAPPING_INVALIDATION: FIXTURE_VERSION_INVALIDATION_ONLY
IDENTITY_MAPPING_AUDIT: HASHED_CASE_AND_CLASSIFICATION_ONLY
IDENTITY_MAPPING_PURPOSE_BINDING: RCA1_OFFLINE_RECONCILIATION_ONLY
IDENTITY_MAPPING_LOGGING_POLICY: NO_RAW_ID_OR_MAPPING_PAIR
IDENTITY_MAPPING_FAILURE_POLICY: FAIL_CLOSED
```

Absent, invalid, expired, deleted, mismatched, unauthorized-purpose and unauthorized-caller cases fail closed. Anonymous, nearest-user, alternate-subject fallback, raw ID logging and P2 row/hash rewrite are prohibited.

## 6. Time and versioning

- cross-track time uses `Instant`, `TIMESTAMPTZ` and UTC ISO-8601 `Z`;
- deterministic comparison uses explicit reference time;
- persisted `latest`, `current` and `default` version identifiers are prohibited;
- unknown required fields/enums and unsupported versions fail closed;
- normalization and verifier versions are recorded in evidence.

## 7. Contract and phase registry

| ID | Status | Meaning |
|---|---|---|
| `RCA` | ACTIVE WORKSTREAM | Recommendation Consumer Adoption; not a platform |
| `RCA-0` | COMPLETE / MERGED | Recommendation Data Consumer Contract & Fixture Alignment |
| `RCA-1` | ENTRY AUTHORIZED | Recommendation Data Shadow Reconciliation |
| `recommendation-data-consumer-alignment-v1` | ACTIVE | RCA-0 alignment contract |
| `recommendation-profile-input-consumer-v1` | ACTIVE | P1 consumer boundary |
| `experiment-outcome-input-consumer-v1` | ACTIVE | P2 consumer boundary |
| `recommendation-data-consumer-fixture-v1` | ACTIVE | deterministic fixture contract |
| `recommendation-shadow-reconciliation-v1` | RESERVED | RCA-1 offline reconciliation |
| `recommendation-shadow-reconciliation-evidence-v1` | RESERVED | redacted offline evidence |
| `recommendation-shadow-reconciliation-fixture-v1` | RESERVED | deterministic reconciliation cases |

`RP` means Reliability Platform and must not be used for Recommendation Platform or the RCA workstream.

## 8. Source authority

| Meaning | Authoritative source |
|---|---|
| P1 source | `RecommendationP1ProfileSource` |
| P1 result | `recommendation_p1_profile_snapshot` |
| P2 source | `RecommendationP2ObservationSource` |
| P2 experiment exposure | `recommendation_p2_experiment_exposure` |
| P2 dataset | `recommendation-evaluation-dataset-v1` |
| P2 metrics | `engagement_rate`, `fallback_rate` |
| Data P1 candidate | `recommendation-profile-input-v1`, non-authoritative |
| Data P2 candidate | `experiment-outcome-input-v1`, non-authoritative |

General exposure, behavior impression, P2 exposure, `view`, `hide` and `report` are not interchangeable.

## 9. RCA-1 execution authorization

Official phase:

```text
RCA-1 Recommendation Data Shadow Reconciliation
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
IDENTITY_MODE=SYNTHETIC_ONLY
CLASSIFICATION=JOINT_INTELLIGENCE_RELIABILITY_ADOPTION
```

Approved purpose: compare recorded authoritative P1/P2 snapshots with Data candidate projections in deterministic, non-production reference cases and classify differences at field, semantic and authority levels.

RCA-1 does not prove runtime readiness, production readiness, cutover readiness, complete equivalence, traffic safety or authority transfer.

Model B is `DEFERRED` to a separately approved non-production read-only phase. Model C is `BLOCKED` in RCA-1 and transferred to `RCA-2 Controlled Runtime Dark Read`.

## 10. P1 reconciliation contract

P1 is a separate lane. Required dimensions:

```text
EXACT_FIELD_PARITY
DERIVED_VALUE_PARITY
AGGREGATE_WINDOW_PARITY
ORDERING_NOT_COMPARABLE
EVENT_GRAIN_MISSING
EXPLICIT_PREFERENCE_MISSING
TRANSFORM_POLICY_MISSING
FINGERPRINT_SEMANTICS_PROTECTED
IDENTITY_BLOCKED
```

Exact and deterministic-derived comparable dimensions have zero mismatch tolerance in Model A. Ordering, event grain, explicit preferences, transform policy and current fingerprint semantics use categorical classification, not percentage thresholds. Aggregate projection must never be converted into a fake event stream.

P1 PASS means comparable fields match, expected gaps are explicitly classified, no unexpected mismatch exists and current P1 authority is unchanged. It is not full profile-source equivalence.

## 11. P2 reconciliation contract

P2 is a separate lane. Required dimensions:

```text
EXPOSURE_REFERENCE_PARITY
ASSIGNMENT_PARITY
SUBJECT_SESSION_RUN_PARITY
OUTCOME_WINDOW_PARITY
ENGAGEMENT_EVENT_PARITY
FALLBACK_BINDING_PARITY
STALE_UNEXPOSED_ASSIGNMENT_GAP
OBSERVATION_DEDUPE_GAP
CANONICAL_DATASET_HASH_PROTECTED
RELEASE_EVIDENCE_PROTECTED
IDENTITY_BLOCKED
```

Exact equality is required for exposure reference, assignment/version, synthetic subject/session/run binding, `604800`-second window, click/like/save/share event set and bound-run fallback.

One-observation identity is `(experimentRef, experimentVersion, subjectRef)` with exposure/run/session consistency. Stale-unexposed behavior, persisted dedupe equivalence and canonical dataset bytes/hash remain migration-protected dimensions. RCA-1 does not recalculate canonical P2 dataset bytes/hash and does not compare or modify release evidence.

P2 PASS carries marker:

```text
P2_SHADOW_RECONCILIATION_ONLY
CURRENT_P2_AUTHORITY_UNCHANGED
NO_AUTHORITY_TRANSFER
```

## 12. Reconciliation result taxonomy

Allowed:

```text
MATCH_EXACT
MATCH_DERIVED
EXPECTED_SEMANTIC_GAP
MIGRATION_REQUIRED
IDENTITY_MAPPING_REQUIRED
IDENTITY_SCHEME_MISMATCH
SOURCE_CHECKPOINT_MISMATCH
SOURCE_STALE
LINEAGE_MISMATCH
EXPOSURE_AUTHORITY_MISMATCH
OUTCOME_WINDOW_MISMATCH
FALLBACK_BINDING_MISMATCH
PROTECTED_AUTHORITY_DIFFERENCE
RECONCILIATION_INCONCLUSIVE
```

Forbidden:

```text
RUNTIME_READY
PRODUCTION_READY
CUTOVER_READY
AUTHORITATIVE
AUTHORITY_TRANSFERRED
```

## 13. Evidence, observability and failure

Evidence may contain synthetic references, hashed fixture identifiers, contract/version, comparison dimension, classification, normalized safe values, checkpoint metadata, lineage fingerprint, evidence timestamp and verifier version.

Raw user/subject IDs, email, access token, session secret, raw behavioral payload, unrestricted event history, identity mapping source, production content and P2 canonical dataset rows are prohibited.

Committed governance evidence is retained in Git history. Generated CI evidence is redacted and retained no longer than 90 days.

Offline counters are verification summaries, not production metrics or SLOs:

`reconciliation_case_count`, `p1_exact_match_count`, `p1_expected_gap_count`, `p1_unexpected_mismatch_count`, `p2_exact_match_count`, `p2_migration_required_count`, `p2_authority_mismatch_count`, `identity_blocked_count`, `checkpoint_mismatch_count`, `lineage_mismatch_count`, `inconclusive_count`.

Fail-closed conditions stop execution and discard generated candidate evidence. No runtime rollback exists in Model A.

## 14. Production activation gates

GATE-1 Data technical closure remains COMPLETE. RCA-0 is COMPLETE. RCA-1 Model A contributes offline comparison evidence only.

GATE-3 through GATE-9 remain unchanged and not authorized. No feature flag, scheduler, worker, DB credential, runtime dark read, production traffic or authority transfer is authorized.

## 15. Completion rule

RCA-1 completes only when both lanes execute separately and satisfy:

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

Completion does not mean runtime adoption, production adoption, cutover authorization, source deprecation or Data authority.

## 16. Absolute prohibitions

- SQL `01..52` rewrite or SQL `53+` use;
- Java/Kotlin production source change in the SC-3 governance PR;
- P1/P2 source, dataset, metric, hash or release change;
- aggregate-to-event fabrication;
- real identity mapping or raw identity evidence;
- Spring/repository/runtime/feature-flag/scheduler/worker wiring;
- runtime dark read or production traffic;
- authority transfer inferred from reconciliation;
- main direct push or merge without explicit user approval.
