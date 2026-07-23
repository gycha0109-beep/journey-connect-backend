# SC DP-7 Cross-track Integration Validation Allocation

## Status

- Decision: `APPROVED / MERGED`
- Implementation authority: `GRANTED`
- Allocation PR: `#19 / MERGED`
- Allocation HEAD: `50e5ab25d60422907181eeaaa3a6b9b638877378`
- Authoritative allocation main: `d18c91a28b271c9f9891b522c6371017a3d0dd79`
- Implementation branch: `agent/dp7-cross-track-integration-implementation`
- Implementation state: `IMPLEMENTATION CANDIDATE / MAIN MERGE PENDING`

The merge of PR #19 activated the entry rule of the allocation proposal. This document now records the approved boundary; it does not itself claim that the implementation PR has been merged.

## Purpose

DP-7 may persist validation-only evidence that determines whether a Data projection snapshot with an exact DP-6 `VALIDATED` quality verdict is compatible with a Recommendation, Intelligence or Search contract.

```text
Data snapshot + exact quality verdict + target contract + boundary policy
→ deterministic checks
→ append-only evidence
→ COMPATIBLE / INCOMPATIBLE / CONDITIONALLY_COMPATIBLE / INCONCLUSIVE
```

None of these verdicts grants serving, traffic, runtime, production write, Search indexing or cutover authority.

## Approved SQL allocation

- SQL 48: `48_cross_track_integration_validation_foundation.sql`
- SQL 49: `49_cross_track_contract_mapping_and_boundary_evidence.sql`
- SQL 50: `50_cross_track_integration_verdict_and_conflict.sql`
- SQL 51: `51_cross_track_integration_persistence_roles_and_safe_view.sql`
- SQL 52: `52_cross_track_integration_validation.sql`

SQL `01..47` remains protected and unchanged. SQL `53+` remains unallocated.

## Approved roles

- `jc_data_integration_writer`: execute only `persist_data_cross_track_integration_v1(jsonb)`.
- `jc_data_integration_reader`: select only `data_cross_track_integration_safe_metrics_v1`.
- `jc_data_integration_function_owner`: `NOLOGIN`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`, `NOBYPASSRLS`; fixed `search_path`; minimum source reads and integration-evidence inserts.

Writer direct table writes, reader raw evidence reads and PUBLIC access are prohibited.

## Approved contracts and policy

- `data-cross-track-integration-policy-v1`
- `data-cross-track-integration-run-v1`
- `data-cross-track-integration-check-v1`
- `data-cross-track-contract-mapping-v1`
- `data-cross-track-authority-matrix-v1`
- `data-cross-track-privacy-retention-matrix-v1`
- `data-cross-track-integration-verdict-v1`
- conflict code: `CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT`

## Approved fingerprint domains

| Logical evidence | Domain |
|---|---|
| integration input | `integration-input-sha256-v1` |
| check evidence | `integration-check-evidence-sha256-v1` |
| contract mapping | `integration-mapping-sha256-v1` |
| integration verdict | `integration-verdict-sha256-v1` |
| contract matrix | `cross-track-contract-matrix-sha256-v1` |

No additional DP-7 fingerprint domain is authorized. Execution time, row ID, random UUID, build ID, locale, timezone, insertion order and physical row order are excluded.

## Logical identity and atomic outcome

```text
source_snapshot_ref
+ source_track
+ target_track
+ source_contract
+ target_contract
+ integration_scope
+ validator_version
+ integration_policy_version
```

- absent identity → `NEW`
- same identity and same input/verdict → `DUPLICATE`
- same identity with different input or verdict → `CONFLICT`

The approved persistence function must use a deterministic logical hash, transaction advisory lock and unique constraint. Conflict evidence is appended; the existing verdict is never rewritten.

## Quality boundary

Only an exact, authoritative, non-conflicted DP-6 `VALIDATED` verdict under `data-quality-policy-v1` may be persisted as an integration candidate. Missing, rejected, inconclusive, conflicted, unsupported, snapshot-mismatched or malformed verdicts are blocked before integration evidence creation.

## Target-track findings retained

- Data → Recommendation profile: `CONDITIONALLY_COMPATIBLE`; current P1 authority remains.
- Data → Recommendation experiment outcome: `CONDITIONALLY_COMPATIBLE`; authoritative P2 exposure and current evaluation dataset remain.
- Data → Intelligence: `INCONCLUSIVE` while no Data-specific Intelligence domain mapping exists.
- Data → Search: `INCONCLUSIVE` while no approved Data-to-Search input contract exists; subject/exposure records are not Search documents.

## Protected boundary

- no Recommendation decision/result/write or metric change;
- no P2 assignment/exposure mutation;
- no Intelligence model/result/runtime activation;
- no Search document generation/index write/routing/cutover;
- no worker, scheduler, replay, backfill, rebuild, purge or identity repository;
- production shadow remains disabled, kill switch enabled, sampling `0 BPS`, cohort empty and `/api/v1/explore` legacy authority unchanged.
