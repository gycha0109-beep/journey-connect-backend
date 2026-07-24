# RCA-0 Implementation Handoff Prompt

Use the following prompt in the separate implementation chat after the SC-2 reconciliation PR is explicitly approved and merged.

---

Journey Connect `RCA-0 Recommendation Data Consumer Contract & Fixture Alignment` implementation task.

Repository:

`gycha0109-beep/journey-connect-backend`

Before changing files, fetch actual `main` and confirm it contains the merged SC-2 post-DP-closure reconciliation decision. The work-start commit must be recorded exactly. If the SC-2 decision is not merged, stop with `RCA0_ENTRY_BLOCKED_BY_SC_DECISION_MERGE` and do not implement.

## Authoritative baseline

- Data Platform: `DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE`
- canonical SQL: `01..52` protected
- SQL `53+`: unallocated
- production activation: not authorized
- current P1 source: `RecommendationP1ProfileSource`
- current P1 result: `recommendation_p1_profile_snapshot`
- current P2 exposure authority: `recommendation_p2_experiment_exposure`
- current P2 dataset: `recommendation-evaluation-dataset-v1`
- current P2 metrics: existing `engagement_rate` and `fallback_rate`
- Data candidates: `recommendation-profile-input-v1`, `experiment-outcome-input-v1`
- Data candidates remain shadow/non-authoritative

## Official workstream and ownership

- workstream: `RCA`
- phase: `RCA-0 Recommendation Data Consumer Contract & Fixture Alignment`
- classification: `JOINT_INTELLIGENCE_RELIABILITY_ADOPTION`
- implementation lead: Intelligence
- P1 semantic owner: Intelligence
- P2 experiment/outcome semantic owner: Reliability
- registry/breaking-change/authority owner: System Coordination
- runtime/deployment owner: Operations, outside this phase

`RP` means Reliability Platform. Do not introduce Recommendation Platform or use RP for Recommendation.

## Objective

Implement a DB-free, runtime-disabled consumer boundary that can parse, validate and classify the two Data candidate contracts against protected Recommendation P1/P2 semantics using deterministic fixtures.

This phase proves only contract and fixture behavior. It does not perform shadow reconciliation, runtime adoption, production write, traffic cutover or authority transfer.

## Required implementation boundary

Preferred isolation:

```text
jc-backend/src/main/java/com/jc/backend/recommendation/dataadoption/
jc-backend/src/test/java/com/jc/backend/recommendation/dataadoption/
jc-backend/src/test/resources/recommendation-data-adoption/
verification/rca0/
docs/platform/recommendation/ or docs/platform/intelligence/ RCA-0 documents
```

Use pure Java immutable types and validators. Do not register Spring components. Do not add `@Component`, `@Service`, `@Repository`, controller, scheduler, worker, listener, configuration properties or DB access.

Do not make `jc-recommendation-core` depend on Spring, JPA, HTTP, DB, environment or system clock.

## Contracts to reserve and implement

- `recommendation-data-consumer-alignment-v1`
- `recommendation-profile-input-consumer-v1`
- `experiment-outcome-input-consumer-v1`
- `recommendation-data-consumer-fixture-v1`

Use existing Data contract types as source truth where module dependency boundaries allow. Do not copy a Data contract into a second authoritative schema. If direct module dependency is inappropriate, implement an adapter/reader boundary with explicit version mapping and tests proving field equivalence.

## P1 lane requirements

Validate the current Data `RecommendationProfileInputProjection` fields:

- subject reference;
- projection as-of time;
- source checkpoint;
- profile schema and projection policy versions;
- 7/30/90-day window;
- interaction counts;
- recent region/content/tag references;
- engagement and negative signals;
- source event count;
- source lineage and record fingerprints.

Classify every current P1 requirement as exact, derivable, missing, incompatible or authority-protected.

The implementation must explicitly detect that the Data aggregate projection does not automatically reproduce:

- current event-grain ordering and timestamps;
- explicit preferences;
- exact `BehaviorProfileEvent` partition behavior;
- feature-vocabulary transform;
- decay and saturation inputs;
- current profile snapshot fingerprint semantics.

Do not construct a fake `BehaviorProfileEvent` stream from aggregate counts. Do not wire or replace `RecommendationP1ProfileSource`.

## P2 lane requirements

Validate the current Data `ExperimentOutcomeInputProjection` fields:

- experiment and version;
- variant;
- exact P2 exposure reference;
- bound recommendation run;
- subject and session;
- exposure timestamp;
- outcome window exactly `604800` seconds;
- clicked, liked, saved and shared;
- fallback observed;
- outcome event references;
- checkpoint, source count and lineage fingerprints.

The implementation must preserve:

- `recommendation_p2_experiment_exposure` as exposure/denominator authority;
- engagement only from click/like/save/share after exposure within seven days;
- fallback only from the bound exposed recommendation run;
- assignment/version/subject identity;
- stale-unexposed assignment behavior as a missing migration dimension;
- one-observation dedupe and canonical dataset bytes/hash as protected migration dimensions;
- existing evaluation and release evidence.

General recommendation exposure, behavior impression, view, hide and report must never become P2 exposure or engagement through this adapter.

Do not wire or replace `RecommendationP2ObservationSource` or `recommendation-evaluation-dataset-v1`.

## Identity and privacy

- `subject:<opaque-id>` and `user:<numeric-id>` remain separate.
- real identity mapping is not implemented in RCA-0.
- use synthetic fixture bindings or an unimplemented `IdentityMappingReadPort` reference only.
- absent, invalid, expired or mismatched mapping must fail closed.
- never fallback to anonymous or another subject.
- do not log raw numeric/opaque mappings.
- do not rewrite P2 rows or hashes.

## Required result taxonomy

Use stable, versioned results. At minimum distinguish:

```text
COMPATIBLE_FOR_FIXTURE_VALIDATION
CONDITIONALLY_COMPATIBLE
MIGRATION_REQUIRED
INCOMPATIBLE_SCHEMA
INCOMPATIBLE_REQUIRED_FIELD
INCOMPATIBLE_REQUIRED_ENUM
UNSUPPORTED_CONTRACT_VERSION
IDENTITY_MAPPING_REQUIRED
IDENTITY_SCHEME_MISMATCH
EXPOSURE_AUTHORITY_MISMATCH
OUTCOME_WINDOW_MISMATCH
PROTECTED_AUTHORITY_CHANGE_REQUIRED
```

Do not emit `RUNTIME_READY`, `PRODUCTION_READY`, `AUTHORITATIVE` or `CUTOVER_APPROVED`.

## Required deterministic fixtures

P1:

1. valid 7-day profile projection;
2. valid 30-day profile projection;
3. valid 90-day profile projection;
4. unsupported schema version;
5. invalid activity window;
6. missing subject/checkpoint/lineage;
7. aggregate-to-event-stream conversion attempt rejected;
8. explicit preference requirement classified missing;
9. identity mapping missing/mismatch.

P2:

1. valid exact P2 exposure outcome;
2. click/like/save/share combinations;
3. non-P2 exposure rejected;
4. behavior impression rejected as exposure;
5. view/hide/report rejected as engagement;
6. window other than 604800 rejected;
7. unbound fallback rejected;
8. subject/session/run/exposure mismatch rejected;
9. stale assignment and dataset hash dimensions classified migration-required;
10. identity mapping missing/mismatch.

All fixtures must be stable across locale, timezone, map iteration order and system clock.

## Verification

Create an independent RCA-0 verifier and machine-readable evidence. It must check:

- exact authoritative work-start SHA;
- registered contract IDs and no RP naming conflict;
- required fixture set and no duplicate scenarios;
- lane-specific expected classifications;
- current P1/P2 source files unchanged;
- `jc-recommendation-core` protected;
- SQL `01..52` unchanged and SQL `53+` absent;
- no Spring/runtime/repository/DB/config additions in the adoption package;
- no production profile/control changes;
- current Recommendation core and backend regressions;
- executed, not-executed and not-applicable states are distinct.

Run only tests that exist and record exact commands and exact tested SHA. Do not claim PostgreSQL, shadow, canary, load, replay or production validation unless actually executed.

## Allowed changes

- pure Java consumer contract types;
- validators and compatibility classifiers;
- deterministic fixture readers;
- test resources and contract tests;
- non-production verification scripts/evidence;
- RCA-0 documentation and handoff;
- minimal CI path coverage for the new verifier/tests.

## Forbidden changes

- SQL or migration;
- current P1/P2 source wiring;
- Spring bean registration;
- DB repository/query/view/role/grant;
- worker/scheduler/listener;
- feature flag or production config;
- production shadow/sampling/cohort/write/traffic;
- replay/backfill/rebuild/purge;
- identity mapping implementation;
- metric, exposure, dataset, hash or release semantics;
- Search/Content/Planner implementation;
- main direct push or PR merge.

## Required documents

At minimum:

1. RCA-0 implementation report;
2. P1 consumer compatibility matrix;
3. P2 outcome compatibility matrix;
4. identity/privacy boundary report;
5. protected-authority report;
6. verification summary;
7. RCA-0 handoff with RCA-1 blockers.

Each document must contain Scope, Current Baseline, Contract Impact, Authority, Dependencies, Allowed Changes, Forbidden Changes, Verification, Compatibility, Risks and Handoff.

## Completion classification

RCA-0 may be completed only when both lane fixtures and protected regressions pass on the exact final PR head.

Permitted final classification:

```text
RCA0_CONTRACT_AND_FIXTURE_COMPLETE
NEXT_PHASE: RCA-1 SHADOW RECONCILIATION REQUIRES SEPARATE SC APPROVAL
DB_CHANGE: NONE
SQL_ALLOCATION: NOT_REQUIRED
PRODUCTION_IMPACT: NONE
PRODUCTION_ACTIVATION: NOT_AUTHORIZED
CURRENT_P1_P2_AUTHORITY_UNCHANGED
```

If P1 or P2 semantics remain incomplete, report the lane-specific blocker and do not collapse it into a combined PASS.

Create a branch and PR. Do not merge without explicit user approval.

## Chat report format

완료

- authoritative main
- work-start SHA
- implemented contracts and fixtures
- P1 result
- P2 result
- identity result
- protected regression result
- DB impact
- production impact
- PR status

문제

- missing/incompatible semantics
- identity blocker
- unexecuted verification
- residual risk

다음 작업

- RCA-1 proposal requirements
- user approval requirement
- merge eligibility

최종 판정

`<RCA0 result>`

---
