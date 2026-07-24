# Journey Connect Track Governance V1

## 1. Document identity

| Field | Value |
|---|---|
| revision | `V1.3 / SC-2 POST-DP-CLOSURE` |
| status | `ACTIVE` |
| authoritative main | `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| SQL `53+` | `UNALLOCATED` |
| system contract | [Journey Connect System Contract V1](JOURNEY_CONNECT_SYSTEM_CONTRACT_V1.md) |

## 2. Track responsibilities

### Data Platform

Owns canonical platform events, ingestion and idempotency contracts, retry/quarantine/replay contracts, Data projections/snapshots, quality, lineage and Data-side cross-track validation evidence.

Does not own Recommendation calculation, Search ranking, moderation decisions, experiment metrics/releases or production activation.

Status: `TECHNICALLY CLOSED`.

### Intelligence Platform

Owns Recommendation profile/ranking/policy semantics, Search retrieval/ranking, Content Analysis, Trip Planning, model/prompt/policy versions and Intelligence run/snapshot/provenance.

Current P0/P1/P2 Recommendation packages and `jc-recommendation-core` remain protected.

### Operations Platform

Owns admin authorization, moderation/visibility/eligibility, operator audit, deployment, secrets, DB runtime access, worker/scheduler execution, monitoring delivery and lifecycle execution.

Operations may not rewrite historical Data or Recommendation evidence.

### Reliability Platform

Owns experiment definition/assignment semantics, authoritative exposure meaning, metrics, denominator/attribution, evaluation, SLI/SLO, release evidence, SHADOW/CANARY/LIVE/HOLD/ROLLBACK decisions, replay/backfill approval and recovery gates.

Current P2 physical implementation remains a protected compatibility arrangement.

### System Coordination

Owns the System Contract, contract/identity/exposure registries, SQL sequence allocation, integration order, breaking changes, common gates, final conflict classification and authority-transfer approval.

## 3. Current authoritative sequence

```text
Data Platform technical closure [COMPLETE]
→ RCA-0 Recommendation Data Consumer Contract & Fixture Alignment
→ RCA-1 shadow reconciliation proposal [separate SC approval required]
→ Intelligence Data Contract
→ Search Data Contract
→ Operations Runtime Enablement
→ Reliability Production Readiness
→ production activation gates
```

This sequence is not an automatic release plan. Contract work may be prepared before Operations/Reliability production readiness, but runtime activation and traffic remain gated.

## 4. RCA workstream

`RCA` means Recommendation Consumer Adoption. It is a cross-track workstream, not a new platform.

`RP` remains reserved for Reliability Platform and must not mean Recommendation Platform.

### RCA-0 ownership

| Lane | Responsible | Accountable | Required approval |
|---|---|---|---|
| P1 profile consumer | Intelligence | Intelligence | SC |
| P2 outcome consumer | Intelligence implementation lead permitted | Reliability | Reliability + SC |
| registry and authority | SC | SC | affected tracks |
| runtime operations | Operations | Operations | outside RCA-0 |

### RCA-0 allowed

- consumer-side immutable contract types;
- strict validators and compatibility classification;
- deterministic fixtures and test-only mappings;
- read-only evidence and protected regressions;
- no DB and no runtime wiring.

### RCA-0 forbidden

- source replacement or production Data reads;
- Spring bean/repository/worker/scheduler registration;
- SQL or role/grant changes;
- identity mapping implementation;
- shadow reconciliation or runtime enablement;
- production write, traffic, cutover or authority transfer.

## 5. Database governance

- SQL `01..52` is protected and immutable;
- SQL `53+` requires SC allocation before implementation;
- tracks must not create independent migration numbers or DB versions;
- forward migration is required for future DB behavior;
- every DB bundle must include owner, writer, reader, grants, retention, privacy, rollback/forward-fix and PostgreSQL 15/18 validation;
- existing P1/P2 tables, roles and grants are High-risk protected paths.

RCA-0 classification: `DB_CHANGE_NOT_REQUIRED`.

## 6. Change proposal gate

A proposal is required before:

- new common ID/enum/time/version;
- source or exposure authority change;
- identity mapping implementation;
- another track read/write path;
- snapshot/hash/canonicalization change;
- SQL/role/grant allocation;
- runtime source enablement or cutover;
- moderation/eligibility change;
- metric, attribution or release-state change.

Process:

```text
proposal
→ SC impact decision
→ registry/sequence reservation
→ implementation
→ track verification
→ cross-track contract test
→ handoff
→ integration approval
```

## 7. PR and branch separation

- one PR must not mix independent track write authorities;
- governance/allocation PRs are separate from implementation PRs;
- RCA-0 may contain both lane fixtures only because it changes no source or write authority; lane evidence and approvals must remain separate;
- main direct push is prohibited for system-track changes;
- no PR is merged without explicit user approval.

Recommended branch:

```text
agent/rca0-recommendation-data-consumer-contracts
```

## 8. Required verification

### Data

Schema/version, idempotency, lineage, quality, integration and protected SQL.

### Intelligence / P1

Current source non-regression, deterministic consumer validation, missing-semantics classification and no fake aggregate-to-event conversion.

### Reliability / P2

Exact assignment/exposure/run/subject/session semantics, seven-day click/like/save/share attribution, fallback binding, dataset/hash/release protection.

### Operations

For RCA-0, only protected production-config diff. Runtime tests are not applicable and must not be reported as PASS.

### Common

Exact tested SHA, registry uniqueness, identity fail-closed, no cross-track writes, no SQL `53+`, no production activation.

## 9. Integration refusal

Reject integration when any of the following occurs:

- duplicate or ambiguous registry ID;
- `RP` reused for Recommendation;
- current P1/P2 source or evidence changed;
- P2 exposure/metric meaning altered;
- Data aggregate inferred to be current P1 event stream;
- real identity join without an approved owner/policy;
- SQL `01..52` change or SQL `53+` use;
- runtime wiring in RCA-0;
- production control difference;
- unexecuted check represented as PASS;
- fixture compatibility represented as runtime or production readiness.

## 10. Canonical governance paths

- [SC Decision Register](SC-DECISION-REGISTER.md)
- [SC RACI](SC-RACI.md)
- [SC Platform Registry](SC-PLATFORM-REGISTRY.md)
- [SC Handoff](SC-HANDOFF.md)
- [SC-2 Reconciliation](SC-2-POST-DP-CLOSURE-NEXT-TRACK-BASELINE-RECONCILIATION.md)
- [RCA-0 implementation prompt](sc-next-track/12-RCA-0-IMPLEMENTATION-HANDOFF-PROMPT.md)
