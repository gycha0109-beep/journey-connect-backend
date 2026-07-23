# DP-7 Cross-track Integration Validation

## Status

`IMPLEMENTATION CANDIDATE / MAIN MERGE PENDING`

- authoritative base: `d18c91a28b271c9f9891b522c6371017a3d0dd79`;
- allocation PR #19: merged;
- SQL allocation: `48..52`;
- implementation branch: `agent/dp7-cross-track-integration-implementation`;
- production activation: prohibited.

## Implemented capability

DP-7 converts immutable Data snapshot evidence and an exact DP-6 quality verdict into deterministic cross-track checks and append-only compatibility evidence. It never invokes the target runtime.

### Java contracts

`jc-data-contracts/src/main/java/com/jc/data/contract/v1/integration` contains immutable run, check, mapping, identity, authority, privacy, retention, quality, verdict, persistence and fingerprint contracts. The full coordinator executes this stable order:

```text
quality
→ identity
→ authority
→ privacy
→ retention
→ fingerprint
→ target-track validator
→ deterministic verdict
```

Required validators:

- `DataRecommendationIntegrationValidator`
- `DataIntelligenceIntegrationValidator`
- `DataSearchIntegrationValidator`
- `CrossTrackIdentityValidator`
- `CrossTrackAuthorityValidator`
- `CrossTrackPrivacyValidator`
- `CrossTrackRetentionValidator`
- `CrossTrackQualityVerdictValidator`
- `CrossTrackFingerprintValidator`
- `FullCrossTrackIntegrationValidator`

The module remains pure Java 21. It has no Spring, JPA, JDBC, network call, system clock, random UUID, mutable global state or runtime activation path.

### SQL objects

SQL 48 creates policy, run, append-only status, check and anomaly foundations. SQL 49 creates mapping, identity, authority, privacy and retention evidence. SQL 50 creates quality binding, verdict and conflict evidence. SQL 51 creates hardened roles, canonical fingerprint helpers, atomic persistence and an aggregate-safe view. SQL 52 is rollback-only PostgreSQL validation.

## Verdict semantics

- blocker or required failure → `INCOMPATIBLE`
- required skipped or missing target contract/evidence → `INCONCLUSIVE`
- all required checks pass with a nonblocking authority-preservation condition → `CONDITIONALLY_COMPATIBLE`
- all required checks pass with exact `VALIDATED` quality and all boundaries → `COMPATIBLE`

`COMPATIBLE` is not production readiness.

## Actual target-track classifications

| Scope | Implemented result | Authority implication |
|---|---|---|
| `DATA_RECOMMENDATION_PROFILE` | `CONDITIONALLY_COMPATIBLE` | P1 source remains authoritative |
| `DATA_RECOMMENDATION_EXPERIMENT_OUTCOME` | `CONDITIONALLY_COMPATIBLE` | P2 exposure/evaluation authority remains authoritative |
| `DATA_INTELLIGENCE_INPUT` | `INCONCLUSIVE` | generic envelope does not establish domain mapping |
| `DATA_SEARCH_INPUT` | `INCONCLUSIVE` | no approved Data input contract; no document/index authority |
| boundary-only compatible fixture | `COMPATIBLE` | validation evidence only |

## Exact quality verdict binding

Persistence queries `data_snapshot_quality_verdict_v1` by the supplied verdict UUID and verifies:

- exact snapshot FK;
- `data-quality-policy-v1`;
- status `VALIDATED`;
- lowercase SHA-256 verdict fingerprint;
- no conflicting verdict for the snapshot.

Caller-provided quality status and caller-provided final integration verdict are ignored.

## Identity boundary

Supported namespaces are `subject:<opaque-id>` and `user:<numeric-id>`. The binding contains version, source, authoritative fingerprint, observed fingerprint, scope and owner. Fingerprints must match, scope must be `cross-track-integration`, owner must be Data, and automatic merge must be false. Search document IDs and Intelligence entity IDs are not identities.

## Authority boundary

DP-7 owns integration evidence only. Authority checks cover canonical event, adapter evidence, checkpoint, projection, snapshot, quality verdict, Recommendation decision/P2 exposure, Intelligence input/result, Search document/index and production traffic control. Any unapproved read, write, validation or production attempt fails closed.

## Privacy and retention boundary

Raw payload, PII, raw text, precise location and unrestricted identity/lineage are rejected. Safe-view evidence is aggregate-only. Target retention cannot exceed source retention. Every evidence row stores `cross_track_integration_evidence_90d`, `data-retention-policy-v1` and `expires_at`; purge and physical delete remain disabled.

## Fingerprints

Only the five SC-approved domains are used. Java canonicalization sorts map keys and contract mappings; checks are sorted by explicit order and code. SQL recomputes mapping, matrix, input, check and verdict fingerprints from authoritative snapshot and quality rows plus validated request fields. Runtime timestamps, IDs and build IDs are excluded.

## Atomic persistence

`persist_data_cross_track_integration_v1(jsonb)`:

1. validates exact source snapshot and quality verdict;
2. validates request structure and boundary evidence;
3. recomputes all approved fingerprints;
4. recomputes counts and final verdict;
5. locks the logical identity with `pg_advisory_xact_lock`;
6. returns `NEW`, `DUPLICATE` or `CONFLICT`;
7. inserts the complete evidence graph in one transaction.

A later insert failure rolls back the complete graph. Existing evidence is never updated.

## Independent review corrections

- removed unallocated identity/logical-identity fingerprint domain proposals; only approved domains remain;
- changed fingerprint failures from Recommendation-specific to a cross-track failure where the target is not Recommendation;
- excluded producer build ID from integration fingerprints and verified build-ID independence;
- prevented missing target contracts from receiving a `COMPLETED` status; the run records `FAILED` with an `INCONCLUSIVE` verdict and stable missing-contract code;
- made safe view execution owner-bounded instead of requiring raw table grants to the reader;
- added exact quality conflict detection and caller-verdict distrust;
- added transaction rollback and concurrent exactly-one-NEW fixtures;
- retained the prohibition on Search subject/exposure-to-document coercion.

## Non-responsibility

No production Recommendation write, Intelligence inference/runtime, Search document generation/index/routing/cutover, consumer, worker, scheduler, replay, backfill, rebuild, purge, identity repository, P2 exposure mutation, source/snapshot/quality mutation or traffic approval is implemented.
