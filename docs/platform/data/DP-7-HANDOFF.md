# DP-7 Handoff

## Status

`DP7_IMPLEMENTATION_BLOCKED_BY_SC_ALLOCATION`

## Baseline

- authoritative `main`: `69b2f9619733e8e6068a23bb149c2aaf41f23fc9`;
- PR #18: merged;
- DP-6 implementation HEAD: `0e9b09283bad61faa830db1019d421c6e906fc7c`;
- DP-6 merge commit: `69b2f9619733e8e6068a23bb149c2aaf41f23fc9`;
- SQL `01..47`: occupied and protected;
- SQL `48+`: unallocated and absent;
- DP-7 roles/policy: unallocated.

## Delivered in this allocation phase

- actual Recommendation, Intelligence and Search contract inventory;
- machine-readable field/semantic/unit/authority integration matrix;
- authority matrix;
- privacy/retention matrix;
- stable integration scope, status, severity and verdict rules;
- Recommendation/Intelligence/Search failure taxonomy;
- quality verdict, identity, authority, privacy, retention and fingerprint boundaries;
- `NEW / DUPLICATE / CONFLICT` logical identity and conflict contract;
- SQL `48..52`, role and policy allocation proposal;
- protected-diff/static verification design;
- exact-head contract-regression workflow;
- machine-readable DP-7 evidence.

## Allocation-time compatibility findings

| Boundary | Result | Runtime effect |
|---|---|---|
| Data → Recommendation profile | `CONDITIONALLY_COMPATIBLE` | none; current P1 source remains authoritative |
| Data → Recommendation experiment outcome | `CONDITIONALLY_COMPATIBLE` | none; current P2 dataset/exposure authority remains authoritative |
| Data → Intelligence | `INCONCLUSIVE` | none; Data-specific target input mapping absent |
| Data → Search | `INCONCLUSIVE` | none; Data input contract absent and direct DP-5 document mapping incompatible |
| identity | design fixed, implementation not executed | no join or mapping repository |
| authority | design fixed, implementation not executed | no cross-track writes |
| privacy/retention | design fixed, implementation not executed | no persistence or safe view |
| quality verdict | exact `VALIDATED` boundary designed | no target activation |
| fingerprint/conflict | domains and identity proposed | no DB/Java implementation |

## Proposed implementation allocation

Subject to explicit SC review and merge:

```text
SQL 48  cross-track run/check foundation
SQL 49  mapping and identity/authority/privacy/retention evidence
SQL 50  verdict and conflict evidence
SQL 51  atomic persistence, roles and aggregate safe view
SQL 52  PostgreSQL 15/18 rollback validation
```

Proposed roles:

```text
jc_data_integration_writer
jc_data_integration_reader
jc_data_integration_function_owner
```

Proposed policy and contracts:

```text
data-cross-track-integration-policy-v1
data-cross-track-integration-run-v1
data-cross-track-integration-check-v1
data-cross-track-contract-mapping-v1
data-cross-track-authority-matrix-v1
data-cross-track-privacy-retention-matrix-v1
data-cross-track-integration-verdict-v1
```

## Proposed pure Java implementation after allocation

Location:

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

The implementation must remain pure Java 21, immutable, deterministic and free of Spring, JPA, JDBC, network, controller, worker, scheduler, replay, backfill, target runtime execution, system clock, random UUID and mutable global state.

## Golden fixture requirements after allocation

### Recommendation

- valid profile mapping;
- valid experiment outcome mapping;
- missing field, unsupported schema, window mismatch, identity mismatch;
- rejected/inconclusive quality verdict;
- P2 exposure mismatch and metric semantic violation.

### Intelligence

- valid generic envelope plus approved domain mapping;
- missing target contract, unsupported schema, lineage missing;
- raw PII, quality/confidence confusion, retention conflict;
- runtime activation attempt.

### Search

- valid future approved Data input mapping;
- missing contract, unsupported schema, unstable document ID;
- region/content/tag mismatch;
- retention/deletion conflict, invalid quality verdict;
- production indexing and cutover attempt.

### Common

- identity namespace, authority, privacy and fingerprint violations;
- duplicate, conflict and deterministic repetition;
- required check skipped and missing evidence.

Each fixture must carry source/target contracts, source snapshot, exact quality verdict, mapping policy, identity binding, authority/privacy/retention policy, expected checks/verdict/fingerprints/failure code.

## Independent review corrections embedded in design

- similar field names do not imply equivalent semantics;
- Data quality is not production approval or model confidence;
- Recommendation P2 denominator and fallback meaning remain unchanged;
- generic Intelligence envelope cannot hide a missing domain contract;
- DP-5 profile/outcome grain cannot be treated as a Search document;
- identity namespaces are never auto-joined;
- target contract ambiguity yields `INCONCLUSIVE`;
- safe-view design excludes reidentification dimensions;
- target retention cannot silently outlive source authority;
- no hidden production write/runtime/index/cutover path is allowed;
- unexecuted validation is never PASS.

## Verification truth state

At this allocation phase:

- SQL `48+`: not created;
- PostgreSQL 15/18 DP-7 fixture: not executed;
- Java DP-7 implementation: not created or executed;
- concurrency/role/safe-view runtime checks: not executed;
- static allocation and protected diff: required on the final PR head;
- existing Data, Recommendation, Intelligence, Search and backend protection regressions: required on the final PR head through the dedicated allocation workflow.

## Protected state

- canonical SQL `01..47` unchanged;
- production Recommendation write disabled;
- Intelligence runtime/model execution absent;
- Search index/cutover unchanged;
- `/api/v1/explore` legacy response authority preserved;
- production shadow disabled;
- kill switch enabled;
- sampling `0 BPS`;
- cohort empty;
- worker absent and scheduler disabled;
- replay/backfill/rebuild/purge unauthorized.

## Next action

1. review this allocation PR;
2. do not merge automatically;
3. if explicitly approved and merged, use its merge commit as the next authoritative base;
4. create a separate DP-7 implementation PR using only approved SQL `48..52`, roles, policy and Java boundary;
5. rerun exact-head Java, PostgreSQL 15/18, Recommendation, Intelligence, Search, backend and SC gates before any implementation-complete verdict.
