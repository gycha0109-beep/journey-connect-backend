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

DP-7 is a validation-only boundary. It determines whether immutable Data projections and snapshots that have a valid DP-6 quality verdict can be interpreted under Recommendation, Intelligence and Search contracts without violating schema, identity, authority, privacy, retention, lineage or fingerprint rules.

It does not connect production traffic or execute a target-track runtime.

## Preflight result

### Repository state

The actual repository confirms that PR #18 is merged and that `main` equals its merge commit. The two remaining open PRs are historical IP-12 attestation drafts and do not allocate DP-7.

### SC allocation

The authoritative SC Decision Register and Platform Registry allocate through DP-6 only. SQL `48+` is explicitly `unallocated / SC ASSIGNMENT REQUIRED`. No DP-7 integration policy or integration writer/reader/function-owner roles exist.

Consequently:

- SQL `01..47` must remain unchanged;
- SQL `48+` must not be created;
- no DB implementation, role, grant, function or safe view may be added;
- no Java DP-7 implementation may be represented as complete;
- implementation-ready contracts, matrices, taxonomy and allocation proposal are allowed.

## Contract inventory

### Data source contracts

| Contract/object | Actual status | Authority |
|---|---|---|
| `recommendation-profile-input-v1` | DP-5 shadow-only | Data facts; not current P1 runtime source |
| `experiment-outcome-input-v1` | DP-5 shadow-only | Data facts with protected P2 exposure binding |
| `data-projection-snapshot-v1` | immutable DP-5 snapshot | Data |
| DP-5 source checkpoint/lineage | immutable | Data |
| `data-quality-policy-v1` | implemented | Data |
| DP-6 snapshot quality verdict | `VALIDATED / REJECTED / INCONCLUSIVE` | Data quality evidence only |

### Recommendation target contracts

The current Recommendation P1 runtime still consumes `recommendation_behavior_event`, content facts and explicit preferences to produce `recommendation_p1_profile_snapshot`. The current P2 runtime consumes authoritative assignment/exposure/run/behavior/profile evidence and materializes `recommendation-evaluation-dataset-v1`.

Protected Recommendation semantics:

- P2 exposure authority: `recommendation_p2_experiment_exposure`;
- engagement: click/like/save/share within seven days after bound exposure;
- fallback: bound exposed `recommendation_run.run_status = fallback`;
- identity: current P2 uses `user:<numeric-id>`;
- Data profile/outcome projections remain shadow-only;
- Recommendation production tables are not DP-7 write targets.

### Intelligence target contracts

The repository provides a generic immutable `intelligence-input-snapshot-v1` envelope with snapshot ID, schema version, source references, optional identity context, reference time, canonicalization version, content hash, payload size, privacy class and producer build ID.

The repository does not provide an approved Data-specific Intelligence input mapping that states which Data projection payload is a valid Recommendation/Search/Content/Planner feature or context contract. Generic envelope compatibility is insufficient to claim semantic compatibility. Quality status must remain separate from model/runtime confidence.

### Search target contracts

The repository contains Search contracts and an existing Search-owned `search-document-projection-v1`. Its stable document identity is `post:<numeric-id>`; it carries region, place, normalized title/body terms, visibility/publication/moderation/deletion state, source timestamp and deterministic content hash.

That projection is built from protected post/region/Operations eligibility authority. DP-5 profile and experiment outcome records have different subject-centric and exposure-centric grain and do not satisfy Search document semantics. No approved Data-to-Search input contract is registered.

## Allocation-time compatibility verdicts

| Boundary | Verdict | Reason |
|---|---|---|
| Data → Recommendation profile | `CONDITIONALLY_COMPATIBLE` | facts/windows/lineage align with a future adapter, but P1 authority/cutover is not approved |
| Data → Recommendation experiment outcome | `CONDITIONALLY_COMPATIBLE` | exposure/window/engagement/fallback semantics align, but current P2 dataset remains authoritative |
| Data → Intelligence input | `INCONCLUSIVE` | generic envelope exists; target domain mapping is absent |
| Data → Search input | `INCONCLUSIVE` | approved Data input contract is absent; direct DP-5-to-search-document mapping is incompatible |
| Full DP-7 | `INCONCLUSIVE` technically, blocked procedurally | required validators/persistence are not allocated or executed |

None of these verdicts authorizes production use.

## Integration matrix contract

The machine-readable matrix uses the following required columns:

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

Allowed matrix statuses:

```text
COMPATIBLE
INCOMPATIBLE
CONDITIONALLY_COMPATIBLE
NOT_APPLICABLE
UNVERIFIED
```

`UNVERIFIED` never counts as `COMPATIBLE`.

## Integration scopes

The proposed implementation must independently record:

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

Each scope uses `PASS / FAIL / SKIPPED / NOT_APPLICABLE`. An unexecuted scope cannot be PASS.

## Proposed run, check and verdict contracts

### Integration run

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

Status evidence is append-only: `STARTED / COMPLETED / FAILED / CONFLICTED`.

### Integration check

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

### Integration verdict

Allowed:

```text
COMPATIBLE
INCOMPATIBLE
CONDITIONALLY_COMPATIBLE
INCONCLUSIVE
```

Rules:

- any `BLOCKER` or required `FAIL` → `INCOMPATIBLE`;
- required `SKIPPED` → `INCONCLUSIVE`;
- target authority/schema unconfirmed → `INCONCLUSIVE`;
- required checks pass but nonblocking conditions remain → `CONDITIONALLY_COMPATIBLE`;
- all required checks pass, exact snapshot has a valid `VALIDATED` verdict and all authority/privacy/identity boundaries pass → `COMPATIBLE`.

## Quality verdict boundary

Only `VALIDATED` may be consumed. The proposed validator must reject:

```text
quality_verdict_missing
quality_verdict_rejected
quality_verdict_inconclusive
quality_verdict_conflicted
quality_policy_unsupported
quality_verdict_fingerprint_mismatch
quality_snapshot_mismatch
```

It must resolve the verdict by exact snapshot reference and logical identity, not by selecting an arbitrary latest row.

## Identity boundary

Supported namespaces remain:

```text
subject:<opaque-id>
user:<numeric-id>
```

A cross-track binding requires version, source, fingerprint and scope. Same-named fields do not imply the same identity. Search document ID and Intelligence entity ID are not user identities.

Failure taxonomy:

```text
cross_track_identity_binding_missing
cross_track_identity_binding_invalid
cross_track_identity_namespace_mismatch
cross_track_identity_scope_mismatch
cross_track_identity_authority_violation
```

## Authority boundary

DP-7 may validate but may not create or modify canonical events, adapter evidence, checkpoints, projections, snapshots, quality verdicts, Recommendation decisions, P2 exposures, Intelligence results, Search documents/indexes or traffic controls.

Failure taxonomy:

```text
cross_track_read_authority_violation
cross_track_write_authority_violation
cross_track_validation_authority_violation
cross_track_production_authority_violation
cross_track_object_ownership_conflict
```

## Privacy and retention boundary

Target contracts may not receive raw payloads, raw user text/query, credentials, email/phone/address, exact location or unrestricted identity mapping. Lineage access must be purpose-bound. Target evidence may not silently outlive its source authority.

Failure taxonomy:

```text
cross_track_privacy_class_mismatch
cross_track_pii_exposure
cross_track_raw_payload_exposure
cross_track_lineage_access_violation
cross_track_retention_conflict
cross_track_deletion_semantic_conflict
cross_track_reidentification_risk
```

Proposed DP-7 evidence uses `cross_track_integration_evidence_90d`, `data-retention-policy-v1`, explicit expiry and no automatic purge.

## Fingerprint boundary

Proposed versioned fingerprint contracts:

```text
integration-input-sha256-v1
integration-check-evidence-sha256-v1
integration-mapping-sha256-v1
integration-verdict-sha256-v1
cross-track-contract-matrix-sha256-v1
```

They bind source/target track and contract, schema versions, source snapshot and quality verdict fingerprints, mapping/integration policy versions and boundary/check results. They exclude execution time, row IDs, UUIDs, build IDs, locale, timezone and ordering artifacts.

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

- new identity → `NEW`;
- same identity and same input/verdict fingerprints → `DUPLICATE`;
- same identity and different input/verdict fingerprints → `CONFLICT / CROSS_TRACK_INTEGRATION_VERDICT_CONFLICT`.

This contract is designed but not implemented or executed in this allocation phase.

## Proposed Java implementation boundary after allocation

Preferred package:

```text
jc-data-contracts/src/main/java/com/jc/data/contract/v1/integration/**
```

The implementation must be pure Java 21 with `-Xlint:all -Werror`, explicit time and identifiers, immutable inputs, deterministic ordering and no Spring, JPA, JDBC, network, controller, worker, scheduler, replay, backfill, runtime invocation, direct system clock, random UUID or mutable global state.

Required contracts and validators are listed in the DP-7 handoff and allocation evidence. None is added by this PR.

## Independent review findings

The allocation design was re-read from six adversarial perspectives:

1. **same field name, different meaning** — profile `subjectRef`, Search `document_id` and Intelligence entity/subject references are explicitly separated;
2. **quality equals approval** — `VALIDATED` is only an input eligibility condition, never production readiness or confidence;
3. **Recommendation metric drift** — engagement and fallback definitions remain exact and P2 exposure remains authoritative;
4. **generic Intelligence envelope overclaim** — semantic compatibility remains `INCONCLUSIVE` without a Data-specific target mapping;
5. **Search projection overreach** — DP-5 profile/outcome records are not Search documents; no index/rebuild/cutover call is permitted;
6. **unexecuted checks reported as PASS** — allocation evidence uses `NOT_EXECUTED`, `UNVERIFIED` or `INCONCLUSIVE`, never PASS for runtime validation.

## Explicit non-responsibility

This phase does not implement production Recommendation write, Recommendation result generation, Intelligence inference/runtime activation, Search indexing/routing/cutover, worker, scheduler, replay, backfill, automatic rebuild, purge, identity repository, identity auto-join, P2 exposure mutation, metric change, source/projection/snapshot/verdict mutation, serving approval or traffic.

## Current decision

`DP7_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

A separate implementation PR may start only after the SC allocation PR is explicitly reviewed and merged. Its base must be that allocation merge commit, not the current proposal head.
