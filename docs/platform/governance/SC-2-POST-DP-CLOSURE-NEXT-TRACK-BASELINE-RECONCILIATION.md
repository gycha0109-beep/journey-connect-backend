# SC-2 Post-DP-Closure Next-Track Baseline Reconciliation

## Document identity

| Field | Value |
|---|---|
| decision ID | `sc-2-post-dp-closure-next-track-baseline-reconciliation-v1` |
| status | `READY_FOR_USER_REVIEW / MERGE_REQUIRED` |
| repository | `gycha0109-beep/journey-connect-backend` |
| authoritative main | `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77` |
| verified closure head | `478a15929db43b1b3d3fde4648a5027a36ee75da` |
| canonical SQL | `journey-connect-db-v2.7/01..52` |
| SQL `53+` | `UNALLOCATED` |
| production activation | `NOT_AUTHORIZED` |
| date | `2026-07-24` |

## Scope

This decision reconciles the repository after Data Platform technical closure. It fixes the next workstream name, ownership, first implementation boundary, SQL decision, production impact and cross-track approvals. It does not implement a consumer, change runtime sources, activate shadow traffic or reopen DP-0 through DP-7.

## Current Baseline

- `main` resolves to `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77`.
- PR #21 is merged by normal merge commit.
- compare `478a15929db43b1b3d3fde4648a5027a36ee75da...95dad33fd56a54d69e2497c11dc4e2e77d8d3a77` reports one merge commit and zero changed files.
- exact-head PR #21 workflows succeeded on `478a15929db43b1b3d3fde4648a5027a36ee75da`.
- merge-commit push workflows are not available and merge-commit local checkout was not executed; neither is represented as PASS.
- Data Platform is `DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE`.
- SQL `01..52` is the immutable closed Data baseline; SQL `53+` is unallocated.
- historical closure documents retain pre-merge evidence wording. This decision records the later post-merge authority without rewriting their phase-time evidence.

## Repository reconciliation findings

1. `JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md`, `JOURNEY_CONNECT_TRACK_GOVERNANCE_V1.md`, `SC-DECISION-REGISTER.md`, `SC-PLATFORM-REGISTRY.md` and `SC-HANDOFF.md` still contain earlier DP-1 or DP-7 phase-time state.
2. `SC-DATA-PLATFORM-TECHNICAL-CLOSURE.md` explicitly states that the later closure decision supplements those historical documents.
3. The actual P1 source remains `RecommendationP1ProfileSource`, reading `recommendation_behavior_event`, content facts and explicit preferences.
4. The actual P2 observation path remains `RecommendationP2ObservationSource`, binding `recommendation_p2_experiment_exposure`, `recommendation_run`, `recommendation_behavior_event` and the latest eligible P1 snapshot.
5. Data profile and experiment outcome projections exist as immutable shadow-only contracts but do not have runtime consumer authority.
6. Governance already uses `RP` for Reliability Platform. `RP` must not mean Recommendation Platform.

## Contract Impact

The following workstream and phase identifiers are registered by this decision:

| ID | Meaning | Status |
|---|---|---|
| `RCA` | Recommendation Consumer Adoption cross-track workstream; not a platform | `RESERVED` |
| `RCA-0` | Recommendation Data Consumer Contract & Fixture Alignment | `ENTRY CONDITIONALLY AUTHORIZED AFTER MERGE` |
| `recommendation-data-consumer-alignment-v1` | workstream contract | `RESERVED` |
| `recommendation-profile-input-consumer-v1` | P1-facing Data profile consumer boundary | `RESERVED` |
| `experiment-outcome-input-consumer-v1` | P2-facing Data outcome consumer boundary | `RESERVED` |
| `recommendation-data-consumer-fixture-v1` | deterministic compatibility fixture | `RESERVED` |

Reservation does not create a DB object, runtime source, production writer, new authority or release approval.

## Authority

### Workstream decision

`JOINT_INTELLIGENCE_RELIABILITY_ADOPTION`

RCA is a coordinated workstream, not a fifth platform.

| Area | Responsible | Accountable | Mandatory approval |
|---|---|---|---|
| P1 profile consumer meaning and adapter boundary | Intelligence | Intelligence | SC |
| P2 experiment outcome/exposure/metric compatibility | Reliability | Reliability | SC |
| shared fixture and no-cutover integration boundary | Intelligence implementation lead | SC | Reliability for P2 fixtures |
| registry, breaking change and authority transfer | SC | SC | affected owners |
| runtime execution, deployment, secrets and controls | Operations | Operations | Reliability/SC when activated |

Physical code in the recommendation package does not transfer semantic ownership.

## First implementation scope

`CONTRACT_AND_FIXTURE`

RCA-0 may implement only consumer-side types, strict validators, deterministic fixtures and tests that prove the Data contracts can be parsed and classified without changing current P1/P2 execution.

RCA-0 is limited to consumer contract adoption (`7.1`). Shadow reconciliation (`7.2`) is deferred to a separately approved RCA-1 phase. Runtime enablement, production write, traffic cutover and authority transfer remain prohibited.

## Allowed Changes

- consumer-side immutable types and validators;
- exact schema/version/required-field fail-closed behavior;
- deterministic JSON/TSV fixtures;
- current-source versus Data-contract semantic mapping tables;
- tests for unsupported identity, exposure authority, outcome window and fallback meaning;
- read-only compatibility classifications;
- documentation and machine-readable evidence;
- existing Recommendation regression execution.

## Forbidden Changes

- `RecommendationP1ProfileSource` replacement or wiring;
- `RecommendationP2ObservationSource` replacement or wiring;
- Spring bean, repository, scheduler, worker or feature-flag activation;
- production DB read path to Data projections;
- SQL `01..52` modification or SQL `53+` creation;
- P1/P2 snapshot, assignment, exposure, dataset, metric, gate or release writes;
- identity repository or real opaque-to-legacy join;
- production shadow, sampling, cohort, write or traffic changes;
- authority transfer or source cutover.

## Dependencies

- P1 lane consumes `recommendation-profile-input-v1` only as a non-authoritative candidate contract.
- P2 lane consumes `experiment-outcome-input-v1` only as a non-authoritative candidate contract and must preserve exact P2 exposure, seven-day `click/like/save/share`, and bound-run fallback semantics.
- Identity mapping physical ownership remains unresolved. RCA-0 must use synthetic fixture bindings or an unimplemented port reference and must fail closed on absent mapping.
- Operations runtime is not a prerequisite for RCA-0.
- Reliability approval is mandatory for P2 fixture semantics but Reliability production readiness is not a prerequisite for RCA-0.

## SQL allocation decision

`DB_CHANGE_NOT_REQUIRED`

- no SQL allocation is made;
- SQL `53+` remains `UNALLOCATED`;
- any later persistence, view, role or grant proposal requires a separate SC allocation decision before implementation.

## Production impact

```text
PRODUCTION_IMPACT: NONE
PRODUCTION_ACTIVATION: NOT_AUTHORIZED
```

Production shadow remains disabled, kill switch enabled, sampling `0 BPS`, cohort empty, Recommendation production write disabled, Intelligence activation disabled, Search indexing/cutover disabled and workers/schedulers absent or disabled.

## Verification

RCA-0 must prove:

1. current P1/P2 source classes remain unchanged;
2. SQL `01..52` hashes/files remain unchanged and SQL `53+` is absent;
3. unknown required fields/enums/versions fail closed;
4. Data profile projection cannot be represented as current event-level P1 source without an explicit new source version;
5. P2 exposure authority is exactly `recommendation_p2_experiment_exposure`;
6. outcome window is exactly 604800 seconds and engagement is only click/like/save/share;
7. fallback is derived only from the bound recommendation run;
8. identity mismatch does not fallback or auto-join;
9. current Recommendation P0/P1/P2 regressions pass;
10. no runtime configuration, production source or protected authority changes exist.

No unexecuted check may be recorded as PASS.

## Compatibility

| Boundary | RCA-0 result |
|---|---|
| Data profile contract parsing | implementation allowed |
| current P1 source replacement | not compatible / not authorized |
| Data outcome contract parsing | implementation allowed with Reliability-approved fixtures |
| current P2 dataset replacement | not compatible / not authorized |
| real identity join | blocked |
| shadow reconciliation | deferred |
| runtime consumer enablement | not authorized |
| production cutover | not authorized |

## Risks

- Data profile projection is aggregate-grain while current P1 source is event/fact-grain and includes explicit preferences.
- P2 Data outcome projection is exposure-grain while current evaluation dataset also governs assignment eligibility, stale assignment filtering and canonical dataset evidence.
- identity mapping owner, retention, deletion and audit remain unresolved.
- historical governance files can mislead later work unless this reconciliation PR is merged.
- a fixture-only PASS can be incorrectly promoted to runtime compatibility; RCA-0 documentation must prevent that inference.

## Handoff

The implementation handoff is `RCA-0-IMPLEMENTATION-HANDOFF-PROMPT.md`.

## Final decisions

```text
NEXT_TRACK: JOINT_INTELLIGENCE_RELIABILITY_ADOPTION
OFFICIAL_WORKSTREAM: Recommendation Consumer Adoption (RCA)
OFFICIAL_PHASE: RCA-0 Recommendation Data Consumer Contract & Fixture Alignment
FIRST_IMPLEMENTATION_SCOPE: CONTRACT_AND_FIXTURE
DB_IMPACT: DB_CHANGE_NOT_REQUIRED
PRODUCTION_IMPACT: NONE
PRODUCTION_ACTIVATION: NOT_AUTHORIZED
NEXT_TRACK_ENTRY: NEXT_TRACK_ENTRY_CONDITIONALLY_AUTHORIZED
CONDITION: this SC decision PR must be explicitly approved and merged
```
