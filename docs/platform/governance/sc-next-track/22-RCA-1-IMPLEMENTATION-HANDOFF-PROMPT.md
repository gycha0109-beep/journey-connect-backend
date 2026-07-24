# RCA-1 Implementation Handoff Prompt

## Scope

Implement `RCA-1 Recommendation Data Shadow Reconciliation` only after the SC-3 authorization PR is explicitly approved and merged.

At implementation start, query actual GitHub `main`. The SC-3 work-start baseline is `f802a105e46a62718616acaa7a3db6c172e7ed10` and RCA-0 exact-final-head is `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d`. Do not assume the implementation base equals either value.

If SC-3 is not merged, stop with `RCA1_ENTRY_BLOCKED_BY_SC3_MERGE`.

## Current Baseline

Required baseline markers:

```text
RCA0_CONTRACT_AND_FIXTURE_COMPLETE
DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE
CANONICAL_SQL=01..52_PROTECTED
SQL_53_PLUS=UNALLOCATED
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
```

Current authority:

```text
P1_SOURCE=RecommendationP1ProfileSource
P1_RESULT=recommendation_p1_profile_snapshot
P2_SOURCE=RecommendationP2ObservationSource
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
P2_DATASET=recommendation-evaluation-dataset-v1
P2_METRICS=engagement_rate,fallback_rate
```

## Decision

Implement only:

```text
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
IDENTITY_MODE=SYNTHETIC_ONLY
CLASSIFICATION=JOINT_INTELLIGENCE_RELIABILITY_ADOPTION
```

Model B and Model C are not authorized.

## Rationale

The implementation must prove deterministic comparator and classification behavior without DB/runtime/production dependencies.

## Authority

- Intelligence approves P1 semantics and expected gaps.
- Reliability approves P2 exposure/outcome/metric semantics and evidence integrity.
- Data is consulted for candidate contract/checkpoint/lineage fields.
- SC controls breaking changes and exit.
- Operations has no execution responsibility for Model A.

## Dependencies

Use existing RCA-0 contracts and fixtures without changing their expected classifications:

- `recommendation-data-consumer-alignment-v1`
- `recommendation-profile-input-consumer-v1`
- `experiment-outcome-input-consumer-v1`
- `recommendation-data-consumer-fixture-v1`
- 12 P1 RCA-0 scenarios
- 21 P2 RCA-0 scenarios

Reserve/use only approved RCA-1 IDs:

- `recommendation-shadow-reconciliation-v1`
- `recommendation-shadow-reconciliation-evidence-v1`
- `recommendation-shadow-reconciliation-fixture-v1`

## Allowed Changes

- pure Java immutable reconciliation records/enums;
- deterministic normalizer and lane comparators;
- recorded authoritative reference fixtures;
- Data candidate fixtures;
- synthetic identity cases;
- lane-specific expected/actual classifications;
- redacted JSON/TSV evidence;
- dependency-free runner and independent verifier;
- Recommendation core/backend protected regressions;
- RCA-1 documentation and minimal CI.

## Forbidden Changes

- RCA-0 contract/validator/fixture behavior changes;
- P1/P2 source or result changes;
- Spring/JPA/HTTP/DB/repository wiring;
- SQL/migration/role/grant;
- runtime reader, feature flag, worker, listener or scheduler;
- production config or traffic;
- real identity mapping;
- aggregate-to-event fabrication;
- P2 canonical bytes/hash calculation or rewrite;
- release evidence modification;
- authority transfer;
- main direct push or automatic merge.

## Identity/Privacy

Implement synthetic identities only.

Required cases:

- valid;
- absent;
- invalid;
- expired;
- deleted;
- mismatched;
- unauthorized purpose;
- unauthorized caller.

All failures are fail-closed. No anonymous, nearest-user or alternate-subject fallback. Evidence uses hashed fixture IDs and never includes raw mapping pairs.

## P1 lane

Required dimensions:

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

Rules:

1. zero mismatch tolerance for exact/shared and deterministic-derived fields;
2. 7/30/90 aggregate windows are explicit fixtures;
3. ordering, event grain and explicit preference are expected gaps;
4. transform/decay/saturation and current fingerprint remain protected;
5. no synthetic event stream reconstruction;
6. output a P1-only verdict and mismatch inventory.

## P2 lane

Required dimensions:

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

Rules:

1. exact P2 exposure authority only;
2. exact assignment/version/variant and synthetic subject/session/run/exposure binding;
3. exact `604800` seconds;
4. engagement is click/like/save/share only;
5. fallback comes from the bound Recommendation run only;
6. one-observation key is `(experimentRef, experimentVersion, subjectRef)` plus exposure/run/session consistency;
7. stale-unexposed and persisted dedupe equivalence are `MIGRATION_REQUIRED`;
8. canonical dataset bytes/hash are not recalculated;
9. release evidence is not read or modified;
10. general exposure, impression, view, hide or report contamination fails;
11. output a P2-only verdict and mismatch inventory.

## Result taxonomy

Use only:

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

Never use:

```text
RUNTIME_READY
PRODUCTION_READY
CUTOVER_READY
AUTHORITATIVE
AUTHORITY_TRANSFERRED
```

## Evidence

Allowed fields:

- hashed case ID;
- lane;
- contract/version;
- comparison dimension;
- classification;
- normalized synthetic safe expected/actual;
- checkpoint;
- lineage fingerprint;
- timestamp;
- verifier version;
- exact tested SHA.

Generated CI artifacts: maximum 90-day retention. No raw identity/payload/content/dataset row.

Required offline counters:

```text
reconciliation_case_count
p1_exact_match_count
p1_expected_gap_count
p1_unexpected_mismatch_count
p2_exact_match_count
p2_migration_required_count
p2_authority_mismatch_count
identity_blocked_count
checkpoint_mismatch_count
lineage_mismatch_count
inconclusive_count
```

These are verification counters, not production metrics or SLOs.

## DB/SQL Impact

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE=NO
NEW_VIEW=NO
NEW_ROLE=NO
NEW_GRANT=NO
```

If any DB object or query becomes necessary, stop with `RCA1_ENTRY_BLOCKED_BY_SQL_ALLOCATION`. Do not write SQL.

## Production Impact

None. Do not add runtime wiring, dark read, traffic, deployment, credentials, alerting or dashboard.

## Verification

The independent verifier must execute and record:

- exact work-start SHA and ancestor;
- SC-3 merge existence;
- unchanged RCA-0 evidence/fixtures/classifications;
- P1/P2 source/core/SQL/config protection;
- pure Java isolation;
- deterministic behavior under locale/timezone/map order/system clock variation;
- all P1 and P2 lane cases;
- synthetic identity fail-closed cases;
- result taxonomy;
- evidence redaction;
- core and backend regressions;
- exact final PR-head SHA.

PostgreSQL, runtime, canary, load, replay and production are `NOT_APPLICABLE` or `NOT_EXECUTED`, never PASS.

## Risks

Report separately:

- unresolved real identity governance;
- P1 non-comparable semantics;
- P2 stale/dedupe/hash migration dimensions;
- fixture-only freshness/checkpoint limitation;
- evidence retention/redaction risk.

## Exit Criteria

Only declare RCA-1 complete when:

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

Completion does not authorize runtime, production, cutover, source deprecation or Data authority.

## Handoff

Create a separate branch and draft PR. Do not merge without explicit user approval. Any final-head change invalidates prior evidence until the verifier and required regressions pass again.
