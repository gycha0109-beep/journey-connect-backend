# SC Decision Register

## Document identity

| Field | Value |
|---|---|
| contract ID | `sc-decision-register-v1` |
| status | `ACTIVE / SC-2 RCA-0 DECISIONS PENDING MERGE` |
| authoritative main | `95dad33fd56a54d69e2497c11dc4e2e77d8d3a77` |
| updated | `2026-07-24` |

## Historical and active decisions

| Decision ID | Decision | Status | Basis / restriction |
|---|---|---|---|
| `SC-DP1-001` | original reconciled baseline `01..28` | SUPERSEDED BY DATA CLOSURE | phase-time decision preserved |
| `SC-DP1-004` | module `jc-data-contracts` | APPROVED / IMPLEMENTED | Data contract module |
| `SC-DP1-005` | package `com.jc.data.contract.v1` | APPROVED / IMPLEMENTED | current convention |
| `SC-DP1-011` | identity mapping physical owner/deletion | UNRESOLVED | real join prohibited |
| `SC-DP2-001` | `platform-event-fingerprint-sha256-v1` | APPROVED / IMPLEMENTED | P0 fingerprint not reused |
| `SC-DP2-002` | SQL `29..31` | APPROVED / IMPLEMENTED | event store/idempotency |
| `SC-DP3-001` | SQL `32..34` | APPROVED / IMPLEMENTED | retry/quarantine |
| `SC-DP45-001` | SQL `35..37` | APPROVED / IMPLEMENTED | adapter shadow evidence |
| `SC-DP5-001` | SQL `38..42` | APPROVED / IMPLEMENTED | projection/snapshot |
| `SC-DP5-003` | profile/outcome projections are shadow-only | APPROVED / ACTIVE | no source authority |
| `SC-DP5-004` | P2 exposure authority is `recommendation_p2_experiment_exposure` | APPROVED / ACTIVE | no general exposure substitution |
| `SC-DP6-001` | SQL `43..47` | APPROVED / IMPLEMENTED | quality/lineage |
| `SC-DP7-001` | SQL `48..52` | APPROVED / IMPLEMENTED / MERGED | PR #19 allocation, PR #20 implementation |
| `SC-DP7-002` | Data integration roles | APPROVED / IMPLEMENTED | writer/reader/NOLOGIN owner |
| `SC-DP7-003` | `data-cross-track-integration-policy-v1` and related contracts | APPROVED / IMPLEMENTED | validation only |
| `SC-DP7-004` | Recommendation profile/outcome conditionally compatible | APPROVED / ACTIVE | current P1/P2 authority retained |
| `SC-DP7-005` | Data-specific generic Intelligence mapping absent | UNRESOLVED / INCONCLUSIVE | separate Intelligence contract required |
| `SC-DP7-006` | Data-to-Search contract absent | UNRESOLVED / INCONCLUSIVE | separate Search contract required |
| `SC-DP-CLOSE-001` | DP-0 through DP-7 technical roadmap | COMPLETE | PR #21 merged |
| `SC-DP-CLOSE-002` | canonical SQL `01..52` | IMMUTABLE BASELINE | historical rewrite prohibited |
| `SC-DP-CLOSE-003` | SQL `53+` | UNALLOCATED | SC assignment required |
| `SC-DP-CLOSE-004` | production activation | NOT_AUTHORIZED | GATE-2 through GATE-9 independent |
| `SC-DP-CLOSE-005` | closure head and merge tree | VERIFIED IDENTICAL | zero changed files |
| `SC-DP-CLOSE-006` | main push CI | NOT_AVAILABLE | not PASS |
| `SC-DP-CLOSE-007` | merge-commit local checkout | NOT_EXECUTED | not PASS |

## SC-2 next-track decisions

| Decision ID | Decision | Status | Basis / restriction |
|---|---|---|---|
| `SC-RCA-001` | next official workstream is Recommendation Consumer Adoption | APPROVED BY SC / MERGE REQUIRED | cross-track workstream, not platform |
| `SC-RCA-002` | official classification `JOINT_INTELLIGENCE_RELIABILITY_ADOPTION` | APPROVED BY SC / MERGE REQUIRED | P1 Intelligence, P2 Reliability |
| `SC-RCA-003` | `RP` remains Reliability Platform | APPROVED / PROTECTED | Recommendation Platform prohibited |
| `SC-RCA-004` | phase `RCA-0 Recommendation Data Consumer Contract & Fixture Alignment` | CONDITIONAL ENTRY | after this decision PR merges |
| `SC-RCA-005` | first implementation scope `CONTRACT_AND_FIXTURE` | APPROVED BY SC / MERGE REQUIRED | consumer contract adoption only |
| `SC-RCA-006` | shadow reconciliation is RCA-1 and separately gated | NOT_AUTHORIZED | identity and parity decisions required |
| `SC-RCA-007` | P1 current source and snapshot authority unchanged | APPROVED / PROTECTED | no source replacement |
| `SC-RCA-008` | P2 assignment/exposure/dataset/metric/release authority unchanged | APPROVED / PROTECTED | Reliability semantics |
| `SC-RCA-009` | RCA-0 identity behavior is synthetic fixture or port reference only | APPROVED | no real mapping/join |
| `SC-RCA-010` | RCA-0 DB decision | `DB_CHANGE_NOT_REQUIRED` | SQL `53+` remains unallocated |
| `SC-RCA-011` | RCA-0 production impact | `NONE / NOT_AUTHORIZED` | gates unchanged |
| `SC-RCA-012` | RCA-0 entry verdict | `NEXT_TRACK_ENTRY_CONDITIONALLY_AUTHORIZED` | explicit user approval and merge required |
| `SC-RCA-013` | contract IDs reserved | APPROVED BY SC / MERGE REQUIRED | no runtime or DB authority |

Reserved RCA-0 contract IDs:

- `recommendation-data-consumer-alignment-v1`
- `recommendation-profile-input-consumer-v1`
- `experiment-outcome-input-consumer-v1`
- `recommendation-data-consumer-fixture-v1`

## Final current decision

```text
DATA_PLATFORM_TECHNICAL_CLOSURE_COMPLETE
NEXT_TRACK=JOINT_INTELLIGENCE_RELIABILITY_ADOPTION
OFFICIAL_PHASE=RCA-0 Recommendation Data Consumer Contract & Fixture Alignment
FIRST_SCOPE=CONTRACT_AND_FIXTURE
DB_CHANGE=NOT_REQUIRED
PRODUCTION_ACTIVATION=NOT_AUTHORIZED
ENTRY=CONDITIONALLY_AUTHORIZED_AFTER_SC-2_MERGE
```
