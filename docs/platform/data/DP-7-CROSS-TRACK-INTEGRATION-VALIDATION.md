# DP-7 Cross-track Integration Validation

## Status

`DP7_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

Authoritative baseline: `69b2f9619733e8e6068a23bb149c2aaf41f23fc9`.

- PR #18: merged;
- DP-6 implementation HEAD: `0e9b09283bad61faa830db1019d421c6e906fc7c`;
- DP-6 merge commit/current main: `69b2f9619733e8e6068a23bb149c2aaf41f23fc9`;
- canonical SQL: exactly `01..47`;
- SQL `48+`: unallocated and absent;
- DP-7 roles/policy: unallocated.

## Purpose

DP-7 is a validation-only boundary. It determines whether immutable Data projections and snapshots with an exact DP-6 quality verdict can be interpreted under Recommendation, Intelligence and Search contracts without violating schema, identity, authority, privacy, retention, lineage or fingerprint rules.

It does not connect production traffic or execute any target-track runtime.

## Preflight result

The actual repository confirms that PR #18 is merged and `main` equals its merge commit. The remaining open PRs are historical IP-12 attestation drafts and do not allocate DP-7.

The authoritative SC Decision Register and Platform Registry allocate through DP-6 only. SQL `48+` is `unallocated / SC ASSIGNMENT REQUIRED`. No DP-7 integration policy or integration writer/reader/function-owner role exists.

Consequently:

- SQL `01..47` remains unchanged;
- SQL `48+` is not created;
- no DB object, role, grant, function or view is added;
- no Java DP-7 implementation is represented as complete;
- implementation-ready contracts, matrices, taxonomy, evidence and allocation proposal are allowed.

## Contract inventory

### Data source contracts

| Contract/object | Actual status | Authority |
|---|---|---|
| `recommendation-profile-input-v1` | DP-5 shadow-only | Data facts; not current P1 source |
| `experiment-outcome-input-v1` | DP-5 shadow-only | Data facts with protected P2 exposure binding |
| `data-projection-snapshot-v1` | immutable DP-5 snapshot | Data |
| source checkpoint and lineage | immutable | Data |
| `data-quality-policy-v1` | implemented | Data |
| snapshot quality verdict | `VALIDATED / REJECTED / INCONCLUSIVE` | Data quality evidence only |

### Recommendation target contracts

Current P1 still consumes `recommendation_behavior_event`, content facts and explicit preferences. Current P2 consumes authoritative assignment, `recommendation_p2_experiment_exposure`, run, behavior and profile evidence and materializes `recommendation-evaluation-dataset-v1`.

Protected semantics:

- exposure authority: `recommendation_p2_experiment_exposure`;
- engagement: click/like/save/share within seven days after bound exposure;
- fallback: bound exposed `recommendation_run.run_status = fallback`;
- P2 identity: `user:<numeric-id>`;
- Data projections remain shadow-only;
- Recommendation production tables are not DP-7 write targets.

### Intelligence target contracts

The repository provides `intelligence-input-snapshot-v1`, a generic immutable envelope with snapshot ID, schema version, source refs, optional identity context, reference time, canonicalization version, content hash, payload size, privacy class and producer build ID.

No approved Data-specific Intelligence domain input mapping states which Data projection payload is a valid Recommendation, Search, Content or Planner input. Generic envelope compatibility is insufficient for semantic compatibility. Data quality status is not model/runtime confidence.

### Search target contracts

The repository contains Search contracts and Search-owned `search-document-projection-v1`. Its document identity is `post:<numeric-id>` and it contains source region/place, normalized title/body terms, visibility/publication/moderation/deletion state, source timestamp and Search content hash.

DP-5 profile and experiment outcome records use subject-centric and exposure-centric grain and do not satisfy Search document semantics. No approved Data-to-Search input contract is registered.

## Allocation-time compatibility verdicts

| Boundary | Verdict | Reason |
|---|---|---|
| Data → Recommendation profile | `CONDITIONALLY_COMPATIBLE` | facts/windows/lineage can support a future adapter; current P1 authority remains |
| Data → Recommendation experiment outcome | `CONDITIONALLY_COMPATIBLE` | exposure/window/metric semantics align; current P2 dataset remains authoritative |
| Data → Intelligence input | `INCONCLUSIVE` | generic envelope exists; domain mapping is absent |
| Data → Search input | `INCONCLUSIVE` | approved Data input contract is absent; direct DP-5 mapping is incompatible |
| Full DP-7 | `INCONCLUSIVE` technically and blocked procedurally | validators and persistence are not allocated or executed |

None authorizes production use.

## Integration matrix contract

The authoritative machine-readable matrix is `verification/dp7/DP7_INTEGRATION_MATRIX.tsv` with these columns:

```text
source_track
source_contract
source_schema_version
source_field
source_semantic
source_unit
source_authority
target_track
target_contract
target_schema_version
target_field
target_semantic
target_unit
target_authority
mapping_rule
required
nullable
identity_namespace
lineage_required
quality_requirement
privacy_class
retention_class
compatibility_status
failure_code
```

Statuses are `COMPATIBLE / INCOMPATIBLE / CONDITIONALLY_COMPATIBLE / NOT_APPLICABLE / UNVERIFIED`. `UNVERIFIED` never counts as compatible.

## Integration scopes

```text
DATA_RECOMMENDATION_PROFILE
DATA_RECOMMENDATION_EXPERIMENT_OUTCOME
DATA_INTELLIGENCE_INPUT
DATA_SEARCH_INPUT
IDENTITY_BOUNDARY
AUTHORITY_BOUNDARY
PRIVACY_BOUNDARY
RETENTION_BOUNDARY
FINGERPRINT_BOUNDARY
QUALITY_VERDICT_BOUNDARY
FULL
```

Each scope records `PASS / FAIL / SKIPPED / NOT_APPLICABLE`. Unexecuted scope is never PASS.

## Proposed run, check and verdict contracts

### Run

```text
integration_run_id
integration_scope
source_track
target_track
source_contract
source_schema_version
target_contract
target_schema_version
source_snapshot_ref
source_quality_verdict_ref
integration_policy_version
validator_version
validation_as_of
created_at
retention_class
retention_policy_version
expires_at
```

Append-only status: `STARTED / COMPLETED / FAILED / CONFLICTED`.

### Check

```text
integration_check_id
integration_run_ref
check_code
check_scope
source_reference
target_reference
expected_value
observed_value
severity
check_status
failure_code
evidence_fingerprint
created_at
```

Check status: `PASS / FAIL / SKIPPED / NOT_APPLICABLE`.
Severity: `INFO / WARNING / ERROR / BLOCKER`.

### Verdict

Allowed: `COMPATIBLE / INCOMPATIBLE / CONDITIONALLY_COMPATIBLE / INCONCLUSIVE`.

- any blocker or required fail → `INCOMPATIBLE`;
- required skipped → `INCONCLUSIVE`;
- target authority/schema unconfirmed → `INCONCLUSIVE`;
- required checks pass with nonblocking conditions → `CONDITIONALLY_COMPATIBLE`;
- all required checks pass with exact `VALIDATED` quality verdict and authority/privacy/identity boundaries → `COMPATIBLE`.

## Quality verdict boundary

Only `VALIDATED` may proceed. Required failures:

```text
quality_verdict_missing
quality_verdict_rejected
quality_verdict_inconclusive
quality_verdict_conflicted
quality_policy_unsupported
quality_verdict_fingerprint_mismatch
quality_snapshot_mismatch
```

The verdict is resolved by exact snapshot and logical identity, not arbitrary latest selection.

## Identity boundary

Supported namespaces remain `subject:<opaque-id>` and `user:<numeric-id>`. A binding requires version, source, fingerprint and scope. Search document IDs and Intelligence entity IDs are not user identities.

```text
cross_track_identity_binding_missing
cross_track_identity_binding_invalid
cross_track_identity_namespace_mismatch
cross_track_identity_scope_mismatch
cross_track_identity_authority_violation
```

## Authority boundary

DP-7 validates but does not create or modify canonical events, adapter evidence, checkpoints, projections, snapshots, quality verdicts, Recommendation decisions, P2 exposures, Intelligence results, Search documents/indexes or traffic controls.

```text
cross_track_read_authority_violation
cross_track_write_authority_violation
cross_track_validation_authority_violation
cross_track_production_authority_violation
cross_track_object_ownership_conflict
```

## Privacy and retention boundary

Targets may not receive raw payloads, raw text/query, credentials, email/phone/address, exact location or unrestricted identity mapping. Lineage access is purpose-bound. Target evidence cannot silently outlive source authority.

```text
cross_track_privacy_class_mismatch
cross_track_pii_exposure
cross_track_raw_payload_exposure
cross_track_lineage_access_violation
cross_track_retention_conflict
cross_track_deletion_semantic_conflict
cross_track_reidentification_risk
```

Proposed evidence uses `cross_track_integration_evidence_90d`, `data-retention-policy-v1`, explicit expiry and no automatic purge.

## Fingerprint boundary

```text
integration-input-sha256-v1
integration-check-evidence-sha256-v1
integration-mapping-sha256-v1
integration-verdict-sha256-v1
cross-track-contract-matrix-sha256-v1
```

They bind source/target track and contract, schema versions, source snapshot and quality verdict fingerprints, mapping/integration policy versions and check/boundary results. They exclude execution time, row IDs, UUIDs, build IDs, locale, timezone and ordering artifacts.

## NEW / DUPLICATE / CONFLICT

Logical identity:

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

- new → `NEW`;
- same identity and same input/verdict fingerprints → `DUPLICATE`;
- same identity and different fingerprint → `CONFLICT / CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT`.

This is designed but not implemented or executed.

## Proposed Java implementation after allocation

Preferred package:

```text
jc-data-contracts/src/main/java/com/jc/data/contract/v1/integration/**
```

Required contracts:

```text
CrossTrackIntegrationDefinition
CrossTrackIntegrationRun
CrossTrackIntegrationScope
CrossTrackIntegrationCheck
CrossTrackIntegrationStatus
CrossTrackIntegrationSeverity
CrossTrackContractMapping
CrossTrackAuthorityRule
CrossTrackPrivacyRule
CrossTrackRetentionRule
CrossTrackIntegrationVerdict
CrossTrackIntegrationFailure
CrossTrackIntegrationPersistenceOutcome
```

Required validators:

```text
DataRecommendationIntegrationValidator
DataIntelligenceIntegrationValidator
DataSearchIntegrationValidator
CrossTrackIdentityValidator
CrossTrackAuthorityValidator
CrossTrackPrivacyValidator
CrossTrackRetentionValidator
CrossTrackFingerprintValidator
FullCrossTrackIntegrationValidator
```

The implementation must be pure Java 21 with `-Xlint:all -Werror`, immutable inputs, deterministic ordering and no Spring, JPA, JDBC, network, controller, worker, scheduler, replay, backfill, runtime invocation, direct system clock, random UUID or mutable global state.

## Independent review findings

1. Same field names do not establish the same meaning.
2. `VALIDATED` is input-quality evidence, not production approval or confidence.
3. Recommendation metric and P2 exposure authority remain exact.
4. Generic Intelligence envelope does not hide a missing domain contract.
5. DP-5 profile/outcome records are not Search documents.
6. Identity namespaces are not auto-joined.
7. Target ambiguity yields `INCONCLUSIVE`.
8. Safe-view design excludes reidentification dimensions.
9. Target retention cannot silently outlive source authority.
10. No production write, runtime, index or cutover path is allowed.
11. Unexecuted checks remain `NOT_EXECUTED`, `UNVERIFIED` or `INCONCLUSIVE`.

## Explicit non-responsibility

This phase does not implement Recommendation write/result generation, Intelligence inference/runtime activation, Search indexing/routing/cutover, worker, scheduler, replay, backfill, rebuild, purge, identity repository, identity auto-join, P2 exposure mutation, metric change, source/projection/snapshot/verdict mutation, serving approval or traffic.

## Current decision

`DP7_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

A separate implementation PR may start only after explicit allocation review and merge. Its base must be that allocation merge commit.
