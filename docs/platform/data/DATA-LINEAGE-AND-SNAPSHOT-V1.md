# Data Lineage and Snapshot V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `data-lineage-snapshot-v1` |
| 상태 | `RECOVERED / ACTIVE DESIGN / IMPLEMENTATION_NOT_STARTED` |
| 소유 | Data Platform |

## Authority and snapshot classes

- source snapshot: immutable reference to authoritative source facts/evidence
- derived snapshot: versioned output built from an explicit source set
- shadow snapshot: derived comparison artifact without runtime authority

Derived/cache/index/model output does not automatically become authoritative.

## Minimum lineage fields

```text
datasetId or snapshotId
contractVersion
schemaVersion
canonicalizationVersion
producerVersion
consumerVersion
producerBuildId
sourceRefs
sourceVersions
referenceTime
createdAt
contentHash
lineagePolicyVersion
supersedesRef
replayAttemptRef
backfillRunRef
```

Additional manifests bind source selector/range, ordering, partitioning, watermark/checkpoint, record counts, excluded/quarantined counts and privacy class.

## Source set and ordering

- every derived snapshot records the complete deterministic source set or immutable selector+watermark that resolves it
- sourceRefs are canonicalized in contract-defined stable order
- business arrays retain semantic order
- same source set + same versions + same referenceTime must reproduce the same contentHash for deterministic projections
- missing source or ordering ambiguity fails closed with lineage error

## Content hash and immutability

Content hash covers versioned canonical snapshot bytes. Hash algorithm/version must be explicit for each snapshot contract. Past source/derived snapshots and hashes are not rewritten. Correction creates a new snapshot with `supersedesRef`; prior evidence remains auditable and access-controlled.

## Minimum datasets

| Dataset | Status/authority |
|---|---|
| `validated-behavior-stream-v1` | future Data-owned derived stream |
| `user-behavior-aggregate-v1` | future Data-owned aggregate |
| `recommendation-profile-input-v1` | shadow-only until P1 reconciliation/approval |
| `search-analytics-input-v1` | future versioned analytics input; not Search index authority |
| `experiment-outcome-input-v1` | shadow-only until P2 exact reconciliation/approval |
| `data-quality-report-v1` | Data quality evidence |

## Replay/backfill lineage

Replay and backfill create new attempt/run evidence with producer/consumer versions, original source/snapshot refs, selector hash, mismatch classification, counts and new output refs. They cannot mutate source authority or silently replace consumer input.

## Consumer compatibility

Each snapshot manifest identifies supported consumer contract/version and compatibility result. Unknown required field/enum/schema fails closed. Dual-read/cutover requires a documented compatibility matrix, reconciliation, rollback and owner/SC approval.

## P1/P2 protection

- current P1 direct source remains authoritative.
- current P2 dataset/exposure/metric/release evidence remains authoritative.
- Data shadow snapshots do not rewrite current rows, bytes, hashes, identity or release evidence.
