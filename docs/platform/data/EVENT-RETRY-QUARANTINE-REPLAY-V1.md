# Event Retry, Quarantine and Replay V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `event-retry-quarantine-replay-v1` |
| 상태 | `RECOVERED / ACTIVE DESIGN / IMPLEMENTATION_NOT_STARTED` |
| 소유 | Data Platform |

## State/evidence model

```text
received → validated → persisted → projected
received → rejected
persisted → projection_failed → retry_scheduled → projected
persisted → quarantined → reviewed → replay_requested → replayed
```

상태를 source row overwrite로만 표현하지 않는다. canonical event, ingestion/projection attempts, retry schedule, quarantine/review, replay request/dry-run/execution, build와 snapshot manifest는 append-only records다.

## Failure classes

| class | examples | handling |
|---|---|---|
| validation | unsupported schema/type, bad ref/time, payload/privacy violation | canonical event 미생성, stable error, automatic retry 금지 |
| transient | DB/lock timeout, worker shutdown, serialization retry | source 유지, new attempt/retry evidence |
| permanent | deterministic mapping impossible, hash mismatch, missing lineage, invariant failure | quarantine, no infinite retry |

## Retry

Default projection retry proposal: 1m, 5m, 30m, 2h, 12h; max 5 attempts. Delay policy is versioned. Jitter affects scheduling only, not output. Deterministic/integrity errors quarantine immediately; exhausted retries use `retry_exhausted`.

Quarantine reasons are lowercase snake_case:

```text
schema_unsupported
source_hash_mismatch
source_binding_invalid
payload_unmappable
payload_too_large
privacy_policy_violation
projection_invariant_failed
retry_exhausted
lineage_source_missing
manual_hold
```

Manual release requires authorized capability, operation/audit refs, new mapping/schema/policy version, successful dry-run and no identity/idempotency/privacy conflict. Source is not edited.

## Replay

Selectors may bind explicit IDs, immutable source ranges, time ranges, producer/type, projection/version, snapshot range or quarantine batch. Selector snapshot is immutable.

Dry-run is mandatory for large batches, published snapshot impact, adapter/privacy/canonicalization changes, or Reliability outcome data. It reports counts, expected existing/conflict/quarantine, hash impact, consumers and sampled differences.

Projection identity:

```text
projectionName + projectionVersion + sourceEventRef
```

Same source/same version returns existing. New projection version may create new output. Replay never updates source/protected evidence.

Replay evidence records request/actor/operation, selector hash, producer/consumer/schema/canonicalization versions, build ID, counts, parent/new snapshot, mismatch and final status.

Replay classes:

- `exact_replay`: identical deterministic output required
- `semantic_replay`: equivalent contract meaning required
- `evidence_replay`: evidence reconstruction without bit-level promise

Replay success never changes source authority.

## Backfill

Every backfill binds:

- `backfillRunRef`
- source selector/range and immutable hash
- contract/schema/canonicalization/producer/consumer versions
- deterministic partition key/order
- checkpoints/watermarks
- pause/stop/retry/resume policy
- partial failure/quarantine counts
- completion criteria and output snapshot refs

Resume is idempotent. Same range/version may be rerun for verification; published output is superseded by a new immutable snapshot, not overwritten. Backfill completion and consumer cutover are separate decisions.

## Stable errors

`EVENT_QUARANTINED`, `EVENT_REPLAY_NOT_ALLOWED`, `EVENT_REPLAY_CONFLICT`, `DATA_LINEAGE_BROKEN`.
