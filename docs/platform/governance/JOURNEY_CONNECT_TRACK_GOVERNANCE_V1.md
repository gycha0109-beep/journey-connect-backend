# Journey Connect Track Governance V1

## 1. Document identity

| Field | Value |
|---|---|
| revision | `V1.4 / SC-3 RCA-1 ENTRY` |
| status | `ACTIVE` |
| authoritative main | `f802a105e46a62718616acaa7a3db6c172e7ed10` |
| RCA-0 exact-final-head | `d33f7e152d0e40999ed8dc3f16c0a3f0bb980a9d` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| SQL `53+` | `UNALLOCATED` |
| system contract | [Journey Connect System Contract V1](JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md) |

## 2. Track responsibilities

### Data Platform

Owns Data candidate projections, checkpoint/lineage metadata and Data contract interpretation. Data does not decide Recommendation profile semantics, P2 metric semantics or authority transfer.

Status: `TECHNICALLY CLOSED`.

### Intelligence Platform

Owns P1 semantic comparison, expected-gap interpretation, feature vocabulary, decay/saturation meaning and P1 reconciliation acceptance. Current P1 source/result remain protected.

### Reliability Platform

Owns P2 exposure/outcome/metric semantics, dedupe/hash/release protection, P2 mismatch acceptance and reconciliation result integrity. `RP` remains reserved for Reliability Platform.

### Operations Platform

Owns runtime environment, credentials, feature flags, scheduler, monitoring, rollback and production controls. Operations execution is `NOT_REQUIRED` for Model A, consulted for protection checks, required for any future Model B environment and mandatory for Model C.

### System Coordination

Owns scope, registry, authority, breaking-change classification, phase entry/exit and cross-track decisions.

## 3. Current authoritative sequence

```text
Data Platform technical closure [COMPLETE]
→ RCA-0 Recommendation Data Consumer Contract & Fixture Alignment [COMPLETE / MERGED]
→ RCA-1 Recommendation Data Shadow Reconciliation [ENTRY AUTHORIZED / MODEL A]
→ optional RCA-1B Non-production Read-only Reconciliation [DEFERRED / SEPARATE SC APPROVAL]
→ RCA-2 Controlled Runtime Dark Read [NOT AUTHORIZED]
→ Operations Runtime Enablement
→ Reliability Production Readiness
→ production activation gates
```

This sequence is not a release plan.

## 4. RCA workstream

`RCA` means Recommendation Consumer Adoption. It is a cross-track workstream, not a new platform.

`RP` remains reserved for Reliability Platform and must not mean Recommendation Platform.

Official classification: `JOINT_INTELLIGENCE_RELIABILITY_ADOPTION`.

## 5. RCA-1 ownership

| Lane / control | Responsible | Accountable | Required approval |
|---|---|---|---|
| P1 comparison implementation | Intelligence | Intelligence | SC |
| P1 acceptance | Intelligence | Intelligence | SC |
| P2 comparison implementation | Intelligence lead permitted | Reliability | Reliability + SC |
| P2 acceptance and evidence integrity | Reliability | Reliability | SC |
| Data contract interpretation | Data | Data | affected lane owner |
| synthetic identity fixture | implementation team | SC | no real identity owner required |
| registry, entry and exit | SC | SC | affected tracks |
| runtime execution | Operations | Operations | outside RCA-1 Model A |

Physical code location does not transfer semantic ownership.

## 6. RCA-1 execution model

```text
RCA1_EXECUTION_MODEL=MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION
IDENTITY_MODE=SYNTHETIC_ONLY
```

Model A is `APPROVED`.

Model B is `DEFERRED`; it requires a non-production PostgreSQL environment, read-only role, transaction read-only, statement timeout, row limit, no migration, reproducible test data and Operations credential approval.

Model C is `BLOCKED` in RCA-1 and assigned to RCA-2.

## 7. RCA-1 allowed

- pure Java/offline comparison types and deterministic normalization;
- recorded authoritative snapshots and Data candidate fixtures;
- RCA-0 fixture extension without changing RCA-0 expected classifications;
- lane-specific comparison and result taxonomy;
- synthetic fixture identity;
- redacted machine-readable evidence and offline counters;
- source/core/SQL/config protected regressions;
- implementation in a separate PR after SC-3 merge.

## 8. RCA-1 forbidden

- P1/P2 source replacement;
- Spring, repository, DB query, worker, scheduler, listener or feature-flag wiring;
- SQL, migration, DB role or grant;
- real identity mapping;
- fake aggregate-to-event reconstruction;
- canonical P2 dataset/hash recalculation or release evidence modification;
- runtime dark read;
- production traffic, cutover, source deprecation or authority transfer.

## 9. Lane separation

P1 and P2 must produce independent lane verdicts and independent mismatch inventories. A combined green status cannot conceal a lane failure.

P1 non-comparable dimensions must be expected-gap classifications. P2 exposure/window/fallback mismatches are failures, while stale assignment, persisted dedupe and canonical hash remain migration-protected unless a future phase is approved.

## 10. Database governance

- SQL `01..52` is protected and immutable;
- SQL `53+` remains unallocated;
- RCA-1 Model A: `DB_CHANGE=NONE`, `SQL_ALLOCATION=NOT_REQUIRED`;
- no new table, view, role or grant;
- a discovered DB requirement blocks that sub-scope with `RCA1_ENTRY_BLOCKED_BY_SQL_ALLOCATION`.

## 11. Verification governance

Required for the SC-3 governance PR:

- exact authoritative work-start SHA;
- PR #23 merge and RCA-0 exact-final-head ancestry/tree;
- RCA-0 handoff/contracts/12 P1/21 P2 fixtures;
- current P1/P2 authority markers;
- SQL `01..52`, SQL `53+` absence and production defaults;
- single Model A and synthetic-only decisions;
- complete lane taxonomies and evidence/privacy policy;
- governance-only diff and unchanged historical RCA-0 evidence;
- exact final PR-head verifier execution.

Unexecuted PostgreSQL, shadow comparison, runtime, canary, load, replay and production checks are not PASS.

## 12. Entry and exit

Entry verdict:

```text
RCA1_ENTRY_AUTHORIZED
IMPLEMENTATION_REQUIRES_SEPARATE_PR
```

Exit requires both lane executions, classified differences, enforced synthetic identity boundary, unchanged authority, no production traffic and no authority transfer.

## 13. PR and branch separation

- SC-3 governance/allocation PR is separate from RCA-1 implementation;
- implementation uses a new branch after SC-3 merge;
- current branch recommendation: `agent/sc-rca1-entry-authorization`;
- no main direct push;
- no merge without explicit user approval.

## 14. Integration refusal

Reject implementation or integration if any of these occur:

- `RP` naming conflict;
- P1/P2 source or evidence change;
- P2 exposure/window/event/fallback meaning change;
- aggregate-to-event fabrication;
- real identity join;
- SQL change or SQL `53+` allocation;
- runtime wiring;
- production config change;
- unexecuted check reported as PASS;
- reconciliation represented as runtime/production/cutover readiness;
- authority transfer marker.

## 15. Canonical governance paths

- [SC Decision Register](SC-DECISION-REGISTER.md)
- [SC RACI](SC-RACI.md)
- [SC Platform Registry](SC-PLATFORM-REGISTRY.md)
- [SC Handoff](SC-HANDOFF.md)
- [SC-3 master decision](sc-next-track/SC-3-RCA-1-ENTRY-AUTHORIZATION-AND-BASELINE-ALLOCATION.md)
- [RCA-1 implementation prompt](sc-next-track/22-RCA-1-IMPLEMENTATION-HANDOFF-PROMPT.md)
