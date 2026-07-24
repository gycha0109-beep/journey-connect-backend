# Journey Connect System Contract V1

## 1. Document identity

| Field | Value |
|---|---|
| contract ID | `jc-system-contract-v1` |
| revision | `V1.3 / SC-2 POST-DP-CLOSURE` |
| status | `ACTIVE / DATA_PLATFORM_TECHNICAL_CLOSURE_ALIGNED` |
| authoritative main | `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77` |
| verified closure head | `478a15929db43b1b3d3fde4648a5027a36ee75da` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| SQL `53+` | `UNALLOCATED` |
| date | `2026-07-24` |

This contract governs shared identity, time, version, source authority, exposure, database sequence and breaking changes across Data, Intelligence, Operations, Reliability and System Coordination.

## 2. Authoritative state

- PR #21 is merged by normal merge commit at `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77`.
- The closure head and merge commit have identical file trees.
- Data Platform DP-0 through DP-7 is technically closed.
- Closure exact-head CI belongs to `478a15929db43b1b3d3fde4648a5027a36ee75da`.
- main push CI is not available and merge-commit local checkout was not executed.
- technical closure is not production readiness or production approval.

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
| canonical platform event, ingestion, idempotency, retry, quarantine, replay contracts, projection, quality and lineage | Data | approved Data functions/roles only |
| recommendation profile, score, rank, diversity, exploration, policy and run meaning | Intelligence | current recommendation packages and `jc-recommendation-core` protected |
| experiment assignment, P2 exposure, metric, evaluation, release and rollback meaning | Reliability | current recommendation P2 package/role is protected compatibility arrangement |
| moderation, visibility, eligibility, operator audit, deployment, secrets and runtime execution | Operations | may not rewrite historical run/snapshot/Data evidence |
| contract, identity, exposure and sequence registries; integration order; authority transfer | System Coordination | approval authority, not feature implementation monopoly |

Tracks must not directly write another track's tables.

## 4. Canonical DB and SQL sequence

- canonical directory: `database/journey-connect-db-v2.7`;
- SQL `01..52`: immutable closed baseline;
- SQL `25..26`: protected Recommendation P2 evaluation/release;
- SQL `27..28`: protected Search projection and Operations eligibility baseline;
- SQL `29..52`: closed Data Platform implementation and validation;
- SQL `53+`: unallocated;
- every new DB behavior requires an SC-allocated forward migration;
- historical SQL rewrite is prohibited;
- Flyway activation is not implied;
- PostgreSQL 15/18 verification is required for any future DB phase.

## 5. Identity

Cross-track entity references use `<entity-type>:<source-id>`.

Identity schemes:

| Scheme | Wire | Status |
|---|---|---|
| `platform_subject_v1` | `subject:<opaque-id>` | ACTIVE |
| `legacy_user_numeric_v1` | `user:<numeric-id>` | PROTECTED COMPATIBILITY |

The schemes are not equal. Automatic conversion, string inference, anonymous fallback and P2 row/hash rewrite are prohibited.

A physical identity mapping requires a single write owner, purpose binding, read allowlist, access audit, version, effective/invalidation times, deletion policy, retention policy and replay behavior. The physical owner remains unresolved.

## 6. Time

- Java/Kotlin cross-track time: `Instant`;
- DB: `TIMESTAMPTZ`;
- JSON: UTC ISO-8601 `Z`;
- deterministic computation uses explicit `referenceTime`;
- offset-less local time is prohibited at cross-track boundaries.

## 7. Versioning

Meaning changes require a new explicit version. Persisted `latest`, `current` and `default` identifiers are prohibited.

Version dimensions include:

- `contractVersion`;
- `schemaVersion`;
- `policyVersion`;
- `metricDefinitionVersion`;
- `canonicalizationVersion`;
- `modelVersion`;
- `promptVersion`;
- `producerVersion` / `consumerVersion`;
- `producerBuildId` / `evaluatorBuildId`.

Unknown optional fields may be ignored only when the contract permits meaning-preserving addition. Unknown required fields, required enums or unsupported versions fail closed.

## 8. Contract registry

Existing Data and Intelligence contracts remain registered in `SC-PLATFORM-REGISTRY.md`.

Post-closure RCA reservations:

| ID | Status | Meaning |
|---|---|---|
| `RCA` | RESERVED | Recommendation Consumer Adoption workstream; not a platform |
| `RCA-0` | CONDITIONAL ENTRY | Recommendation Data Consumer Contract & Fixture Alignment |
| `recommendation-data-consumer-alignment-v1` | RESERVED | workstream alignment contract |
| `recommendation-profile-input-consumer-v1` | RESERVED | P1-facing consumer boundary |
| `experiment-outcome-input-consumer-v1` | RESERVED | P2-facing consumer boundary |
| `recommendation-data-consumer-fixture-v1` | RESERVED | deterministic fixture contract |

`RP` means Reliability Platform and must not be used for Recommendation Platform.

## 9. Source authority

| Meaning | Authoritative source |
|---|---|
| P0/P1 behavior fact | `recommendation_behavior_event` |
| current P1 profile source | `RecommendationP1ProfileSource` path |
| current P1 result | `recommendation_p1_profile_snapshot` |
| general Recommendation exposure | `recommendation_exposure_event` and candidate rows |
| behavior impression | recommendation behavior `impression`; not P2 denominator |
| P2 assignment | `recommendation_p2_experiment_assignment` |
| P2 experiment exposure | `recommendation_p2_experiment_exposure` |
| P2 dataset | `recommendation-evaluation-dataset-v1` |
| P2 fallback | bound exposed `recommendation_run.run_status` |
| Data profile candidate | `recommendation-profile-input-v1`, non-authoritative |
| Data outcome candidate | `experiment-outcome-input-v1`, non-authoritative |
| Search projection | Search-owned derived projection, not Data authority |

General exposure, behavior impression and P2 experiment exposure must not be merged into one denominator.

## 10. P1/P2 protection

Without a new contract/dataset version, reconciliation, replay plan, full regression and SC approval, no track may:

- replace `RecommendationP1ProfileSource`;
- rewrite P1 snapshots;
- change P2 assignment/exposure/dataset/evaluation/gate/release writes;
- change P2 engagement event set, seven-day attribution, fallback or denominator;
- rewrite canonical bytes, hashes, row identities or release evidence;
- promote a Data projection to authority.

## 11. RCA-0 boundary

Official classification: `JOINT_INTELLIGENCE_RELIABILITY_ADOPTION`.

Official phase: `RCA-0 Recommendation Data Consumer Contract & Fixture Alignment`.

Allowed:

- immutable consumer contract types;
- strict validators and compatibility classifiers;
- deterministic fixtures;
- P1/P2 semantic mapping evidence;
- no-runtime tests and protected regressions.

Prohibited:

- DB/SQL change;
- Spring/runtime/repository wiring;
- Data projection DB reads;
- real identity mapping;
- shadow reconciliation;
- production write, traffic or authority change.

RCA-0 entry is conditional on explicit merge of the SC-2 decision PR.

## 12. Production activation gates

| Gate | Objective | Status after Data closure |
|---|---|---|
| GATE-1 | Data technical closure | COMPLETE |
| GATE-2 | target consumer contract readiness | PARTIAL |
| GATE-3 | runtime execution plane | NOT_READY |
| GATE-4 | observability | NOT_READY |
| GATE-5 | security/deployment access | PARTIAL |
| GATE-6 | SLI/SLO, recovery and release reliability | NOT_READY |
| GATE-7 | production shadow authorization | NOT_AUTHORIZED |
| GATE-8 | consumer adoption | NOT_AUTHORIZED |
| GATE-9 | production cutover | NOT_AUTHORIZED |

RCA-0 may contribute contract evidence only. It does not complete GATE-2 or advance GATE-3 through GATE-9.

## 13. Breaking changes

The following always require SC review and a new version or migration plan:

- identity scheme or mapping behavior;
- event/fingerprint/canonicalization semantics;
- source or exposure authority;
- Recommendation profile or P2 dataset source;
- metric numerator, denominator or attribution;
- DB sequence, role or grant;
- moderation/eligibility behavior;
- runtime source enablement, write or traffic cutover.

## 14. Completion rule

A track phase is complete only when scope, owner, versions, privacy, protected authority, tests, exact tested SHA, rollback/forward-fix and handoff are documented. Unexecuted checks are never PASS.

## 15. Absolute prohibitions

- SQL `01..52` rewrite;
- unallocated SQL `53+` use;
- cross-track direct write;
- hidden source cutover;
- automatic identity join;
- P1/P2 evidence rewrite;
- exposure-source conflation;
- production activation inferred from technical completion;
- main direct push or merge without explicit user approval.
