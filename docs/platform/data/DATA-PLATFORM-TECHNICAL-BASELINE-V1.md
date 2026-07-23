# Data Platform Technical Baseline V1

## Baseline identity

| Field | Value |
|---|---|
| contract | `data-platform-technical-baseline-v1` |
| repository | `gycha0109-beep/journey-connect-backend` |
| branch/commit | `main` / `c528f6fb0942389b70a348cb9aa672eb7819a392` |
| closure date | `2026-07-24` |
| roadmap | `DP-0..DP-7` |
| SQL | `journey-connect-db-v2.7/01..52` |
| unallocated | `53+` |
| Java | `jc-data-contracts` / `com.jc.data.contract.v1` |
| production | `DISABLED / NOT AUTHORIZED` |

This baseline is technical evidence, not deployment or traffic approval.

## Completed phases

| Phase | Objective | Outputs | Contracts | SQL | Validation | Limitation | Handoff |
|---|---|---|---|---|---|---|---|
| DP-0 | foundation | architecture/event/idempotency/retry/lineage/retention docs | `jc-data-platform-contract-foundation-v1` | none | SC/static | historical contract phase | later DP |
| DP-1 | event validation | immutable Java types/validators | `dp-1-event-domain-types-validation-v1` | none | Java21 | ingress absent | Operations |
| DP-2 | store/idempotency | canonical event/attempt/conflict | `platform-event-v1` | 29..31 | PG15/18+concurrency | worker absent | Operations |
| DP-3 | retry/quarantine | claim/lease/retry/quarantine/review | `data-projection-retry-v1` | 32..34 | PG15/18+concurrency | scheduler/replay absent | Ops/Reliability |
| DP-4 | P0 adapter | deterministic source→shadow mapping | adapter v1 | none | Java/Recommendation | not canonical source | Recommendation |
| DP-4.5 | adapter evidence | run/output/failure/conflict | evidence v1 | 35..37 | PG15/18+concurrency | shadow only | Recommendation |
| DP-5 | projection/snapshot | checkpoint/profile/outcome/snapshot/lineage | `data-projection-snapshot-v1` | 38..42 | PG15/18+concurrency | shadow only | target tracks |
| DP-6 | quality/lineage | run/check/metric/anomaly/verdict/rebuild | `data-quality-policy-v1` | 43..47 | PG15/18+Java | not release approval | Reliability |
| DP-7 | integration validation | mapping/authority/privacy/retention/verdict | integration policy v1 | 48..52 | PG15/18+target regressions | conditional/inconclusive | target tracks |

## Canonical object inventory

| Object | Owner | Storage | Schema/policy | Writer | Reader | Retention | Append-only | Production |
|---|---|---|---|---|---|---|---|---|
| canonical event | Data | `data_platform_event_v1` | `platform-event-v1` | event function | approved reader | 365d metadata | yes | worker absent |
| idempotency evidence | Data | SQL29..30 evidence | fingerprint v1 | function owner | approved reader | 30d/90d | yes | technical |
| retry/quarantine | Data | SQL32..33 | retry v1 | retry processor | reviewer/safe | 90d | yes | scheduler absent |
| adapter evidence | Data | run/output/failure/conflict | DP4.5 | function owner | safe view | 90d | yes | shadow |
| checkpoint | Data | `data_source_checkpoint_v1` | projection v1 | projection owner | approved | 90d | yes | no worker |
| projection records | Data | profile/outcome tables | contract v1 | projection owner | target comparison | 90d | yes | shadow |
| snapshot | Data | `data_projection_snapshot_v1` | snapshot v1 | projection owner | quality/integration | 90d | yes | not serving |
| lineage | Data | `data_projection_lineage_v1` | lineage v1 | projection owner | audit/quality | 90d | yes | evidence |
| quality run | Data | `data_quality_validation_run_v1` | policy v1 | quality owner | safe view | 90d | yes | no scheduler |
| quality verdict | Data | `data_snapshot_quality_verdict_v1` | policy v1 | quality owner | integration/Reliability | 90d | yes | not release |
| integration run | Data | `data_cross_track_integration_run_v1` | policy v1 | integration owner | safe view | 90d | yes | validation |
| integration verdict | Data | `data_cross_track_integration_verdict_v1` | policy v1 | integration owner | target/SC | 90d | yes | not activation |

## Contract inventory

| Contract | Version | Producer | Consumer | Authority | Compatibility | Migration | Production |
|---|---|---|---|---|---|---|---|
| event | `platform-event-v1` | Data | Data store | Data | active | new version | worker absent |
| schema | `user-behavior-event-v1` | Data | validator | Data | active | new schema | worker absent |
| canonical JSON | `platform-event-canonical-json-v1` | Data | fingerprint | Data | exact | new version | technical |
| event fingerprint | `platform-event-fingerprint-sha256-v1` | Data | idempotency | Data | exact | new domain | technical |
| adapter | `dp-4-recommendation-event-adapter-v1` | Data | shadow | Recommendation source retained | shadow compatible | new mapper | shadow |
| profile | `recommendation-profile-input-v1` | Data | Recommendation candidate | split | `CONDITIONALLY_COMPATIBLE` | parity+approval | not adopted |
| outcome | `experiment-outcome-input-v1` | Data | Recommendation/Reliability | split | `CONDITIONALLY_COMPATIBLE` | exact metric parity | not adopted |
| snapshot | `data-projection-snapshot-v1` | Data | quality/integration | Data | active | new schema/policy | shadow |
| quality | `data-quality-policy-v1` | Data | integration/Reliability | Data validation | active | new policy | not release |
| integration | `data-cross-track-integration-policy-v1` | Data | target review | Data validation | active | new policy | not activation |
| Recommendation dataset | `recommendation-evaluation-dataset-v1` | Recommendation | P2 | Recommendation/Reliability | authoritative | target migration | protected |
| Intelligence envelope | `intelligence-input-snapshot-v1` | Intelligence | Intelligence | Intelligence | generic only | Data-specific contract | inactive |
| Search projection | `search-document-projection-v1` | Search | Search | Search | target authoritative | Data-to-Search contract | no Data mapping |

## Policy/fingerprint/validation

Policies: `data-retention-policy-v1`, `data-projection-retry-v1`, projection/feature policies, `data-quality-policy-v1`, `data-cross-track-integration-policy-v1`. Fingerprint semantic change requires a new domain/version; old fingerprints are not reinterpreted.

Main push CI at `c528f6fb0942389b70a348cb9aa672eb7819a392` is `NOT_AVAILABLE`; main tree equivalence to `affb561eeeb7b1eb9cabb44e5d29b9378194934d` is verified; PR #20 exact-head gates passed. Closure PR exact-head gates are required. Unexecuted checks are never PASS.
