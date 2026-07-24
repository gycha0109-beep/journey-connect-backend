# System Coordination Handoff

## Status

`RCA0_CONTRACT_AND_FIXTURE_COMPLETE / RCA1_ENTRY_AUTHORIZED`

## Authoritative baseline

- repository: `gycha0109-beep/journey-connect-backend`;
- authoritative main/work-start: `f802a105e46a62718616acaa7a3db6c172e7ed10`;
- PR #23: merged;
- RCA-0 exact-final-head: `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d`;
- RCA-0 merge tree: identical to exact-final-head tree;
- Data Platform: technically closed;
- SQL `01..52`: implemented and protected;
- SQL `53+`: absent and unallocated;
- production activation: not authorized;
- current P1/P2 authority: unchanged.

## Official phase and model

```text
RCA-1 Recommendation Data Shadow Reconciliation
CLASSIFICATION=JOINT_INTELLIGENCE_RELIABILITY_ADOPTION
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
IDENTITY_MODE=SYNTHETIC_ONLY
```

RCA is a cross-track workstream. `RP` remains Reliability Platform.

## Purpose

RCA-1 compares recorded authoritative P1/P2 reference snapshots with Data candidate projections in deterministic non-production cases and classifies differences at field, semantic and authority levels.

It does not prove runtime, production, cutover, complete equivalence, user-traffic safety or authority transfer.

## Current authority

```text
P1_SOURCE=RecommendationP1ProfileSource
P1_RESULT=recommendation_p1_profile_snapshot
P2_SOURCE=RecommendationP2ObservationSource
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
P2_DATASET=recommendation-evaluation-dataset-v1
P2_METRICS=engagement_rate,fallback_rate
```

Data candidate and RCA consumer contracts remain non-authoritative.

## P1 decision

- entry: `CONDITIONALLY_APPROVED`;
- exact/shared and deterministic-derived dimensions: zero mismatch tolerance;
- ordering, event grain, explicit preference, transform policy and fingerprint semantics: categorical expected/protected gaps;
- fake aggregate-to-event reconstruction: prohibited;
- P1 PASS: lane-only reconciliation evidence, not full source equivalence.

## P2 decision

- entry: `CONDITIONALLY_APPROVED`;
- exact required: exposure, assignment/version, synthetic subject/session/run, 604800-second window, click/like/save/share and bound fallback;
- stale-unexposed assignment, persisted one-observation dedupe and canonical dataset hash: migration-protected;
- canonical bytes/hash: not recalculated;
- release evidence: non-modification protection only;
- mixed general exposure/impression/view/hide/report: authority mismatch failure;
- P2 PASS marker: `P2_SHADOW_RECONCILIATION_ONLY / NO_AUTHORITY_TRANSFER`.

## Identity and privacy

Model A uses synthetic identities only. No physical identity mapping owner, store, repository or port implementation is authorized. Real identity governance remains deferred.

Evidence uses hashed fixture IDs and redacted normalized values. Raw user IDs, opaque subject IDs, session secrets, payload history, mapping pairs and canonical dataset rows are prohibited.

## DB and production

```text
DB_CHANGE=NONE
SQL_ALLOCATION=NOT_REQUIRED
NEW_TABLE_REQUIRED=NO
NEW_VIEW_REQUIRED=NO
NEW_ROLE_REQUIRED=NO
NEW_GRANT_REQUIRED=NO
RUNTIME_WIRING=NOT_AUTHORIZED
PRODUCTION_IMPACT=NONE
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
```

A discovered DB requirement blocks that sub-scope and requires a new allocation proposal.

## Prerequisites

| Track | RCA-1 Model A prerequisite |
|---|---|
| Intelligence | required for P1 semantics and implementation |
| Reliability | required for P2 semantics and acceptance |
| Data | consulted for candidate contract/checkpoint/lineage |
| SC | required for entry, exit and breaking changes |
| Operations | consulted; execution credentials not required |
| Privacy/Security | policy review; no real identity material |

## Verification truth

SC-3 verifies governance, baseline, contracts, fixture inventory, source authority, SQL/config protection, decision uniqueness and diff boundaries.

The following are not executed by SC-3 and are not PASS: actual RCA-1 comparison, PostgreSQL, runtime, canary, load, replay and production.

## Documents

- [SC-3 master decision](sc-next-track/SC-3-RCA-1-ENTRY-AUTHORIZATION-AND-BASELINE-ALLOCATION.md)
- [execution model](sc-next-track/13-SC-RCA1-EXECUTION-MODEL-DECISION.md)
- [P1 equivalence](sc-next-track/14-SC-RCA1-P1-RECONCILIATION-AUTHORITY-AND-EQUIVALENCE.md)
- [P2 equivalence](sc-next-track/15-SC-RCA1-P2-RECONCILIATION-AUTHORITY-AND-EQUIVALENCE.md)
- [identity governance](sc-next-track/16-SC-RCA1-IDENTITY-MAPPING-GOVERNANCE.md)
- [evidence/privacy](sc-next-track/17-SC-RCA1-EVIDENCE-AND-PRIVACY-POLICY.md)
- [prerequisite matrix](sc-next-track/18-SC-RCA1-OPERATIONS-RELIABILITY-PREREQUISITE-MATRIX.md)
- [DB/SQL decision](sc-next-track/19-SC-RCA1-DB-SQL-IMPACT-DECISION.md)
- [verification plan](sc-next-track/20-SC-RCA1-VERIFICATION-PLAN.md)
- [exit and RCA-2 boundary](sc-next-track/21-SC-RCA1-EXIT-CRITERIA-AND-RCA2-HANDOFF.md)
- [implementation prompt](sc-next-track/22-RCA-1-IMPLEMENTATION-HANDOFF-PROMPT.md)

## Follow-up order

1. review and merge SC-3 only after explicit user approval;
2. implement RCA-1 Model A in a separate branch/PR;
3. obtain Intelligence P1 and Reliability P2 acceptance;
4. evaluate RCA-1 exit criteria;
5. separately propose Model B if DB evidence is still needed;
6. separately propose RCA-2 for runtime dark read.

## Current gate

```text
RCA1_ENTRY_AUTHORIZED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

SC-3 is governance-only. The PR must remain unmerged until explicit user approval.
