# Event Retry, Quarantine and Replay V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `event-retry-quarantine-replay-v1` |
| 상태 | `ACTIVE / DP-3 IMPLEMENTATION AUTHORIZED` |
| 소유 | Data Platform |
| SC authority | `SC-DP3-ENTRY-DECISIONS.md` |

## State/evidence model

```text
received → validated → persisted
persisted → retry_scheduled → retry_claimed → retry_succeeded
persisted → retry_scheduled → retry_claimed → retry_failed → retry_scheduled
retry_failed → retry_exhausted → quarantined
persisted → quarantined → reviewed_retain
persisted → quarantined → reviewed_release_requested
```

Canonical source rows are immutable. Processing attempts, retry decisions, quarantine entries and reviews are append-only evidence. Mutable queue/lease control state is permitted only behind approved procedures and is not historical evidence.

Stable states:

- `retry_scheduled`
- `retry_claimed`
- `retry_succeeded`
- `retry_failed`
- `retry_exhausted`
- `quarantined`
- `reviewed_retain`
- `reviewed_release_requested`
- `manual_hold`

## Failure classes

Automatically retryable:

- `database_lock_timeout`
- `serialization_failure`
- `dependency_unavailable`
- `worker_interrupted`
- `rate_limited`

Immediate quarantine:

- `schema_unsupported`
- `source_hash_mismatch`
- `source_binding_invalid`
- `payload_unmappable`
- `payload_too_large`
- `privacy_policy_violation`
- `projection_invariant_failed`
- `lineage_source_missing`
- `manual_hold`

Unknown failure classes fail closed as `unclassified_failure` and are not retried automatically.

## Retry policy

Policy ID: `data-projection-retry-v1`.

- initial attempt: `1`
- maximum automatic retries: `5`
- maximum total executions: `6`
- delays: `1m`, `5m`, `30m`, `2h`, `12h`
- deterministic bounded scheduling jitter: `0..10%`
- jitter is derived from a privacy-safe work reference and never affects deterministic output
- retry `5` failure produces `retry_exhausted`
- three consecutive identical normalized failure signatures may quarantine early as `repeated_deterministic_failure`
- authorization, validation, privacy, fingerprint, lineage and invariant errors are never retried automatically

## Claim and lease

- processor role: `jc_data_retry_processor`
- quarantine reviewer: `jc_data_quarantine_reviewer`
- replay role: `jc_data_replay_executor`, with no replay execution grant in DP-3
- atomic claims use `SKIP LOCKED` or an equivalent PostgreSQL-safe primitive
- lease duration: `60 seconds`
- heartbeat: `20 seconds`
- maximum default claim batch: `100`
- expired leases may be reclaimed only by the approved claim procedure
- completion/failure requires matching claim token, processor instance reference and attempt reference
- stale or foreign claim completion is rejected

## Quarantine review

Review evidence may record retain or release-request decisions. DP-3 does not execute replay.

A release request requires:

- authorized reviewer capability
- operation/audit references
- stable reason code
- target schema/mapping/policy/consumer version
- successful dry-run evidence reference
- no identity, idempotency, privacy or source-integrity conflict

A release request is new append-only evidence. It does not modify source or quarantine history and does not authorize replay by itself.

## Replay

Selectors may bind explicit IDs, immutable source ranges, time ranges, producer/type, projection/version, snapshot range or quarantine batch. Selector snapshots are immutable.

Dry-run is mandatory for large batches, published snapshot impact, adapter/privacy/canonicalization changes or Reliability outcome data.

Projection identity:

```text
projectionName + projectionVersion + sourceEventRef
```

Same source/same version returns existing output. New projection versions may create new output. Replay never updates canonical source or protected evidence.

Replay execution remains outside DP-3 and requires a later SC grant decision.

## Observability

Required bounded metrics:

- scheduled/claimed/succeeded/failed/exhausted counts
- quarantine count by stable reason
- retry latency and age-to-success
- ready queue depth and oldest ready age
- active and expired leases
- stale claim rejection
- repeated failure signature count

Allowed dimensions: policy version, failure class, event family, projection name/version and result.

Prohibited metric/log content: actor refs, idempotency keys, canonical payloads, raw errors, tokens, unrestricted text and raw identity.

Operational alert thresholds are fixed in `SC-DP3-ENTRY-DECISIONS.md`; routing and activation belong to Operations.

## Retention

Retry attempts, quarantine entries and review evidence use `90-day` technical retention metadata. Automatic purge and physical deletion remain disabled.

## Stable errors

- `EVENT_QUARANTINED`
- `EVENT_RETRY_NOT_ALLOWED`
- `EVENT_RETRY_EXHAUSTED`
- `EVENT_CLAIM_STALE`
- `EVENT_REPLAY_NOT_ALLOWED`
- `EVENT_REPLAY_CONFLICT`
- `DATA_LINEAGE_BROKEN`
