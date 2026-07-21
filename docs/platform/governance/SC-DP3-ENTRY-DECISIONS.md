# SC DP-3 Entry Decisions

## Status

`APPROVED / DP-3 IMPLEMENTATION AUTHORIZED AFTER MERGE`

## Baseline

- DP-2 implementation PR: `#8`
- DP-2 implementation HEAD: `f6c45a86ee21beb0d7a12e931c73ca887effdf18`
- DP-2 merge commit: `0ff67aaf9a86b61be2b41c431a570a9f0d460f7c`
- DP-2 result: `DP2_IMPLEMENTATION_COMPLETE`

## DP-3 scope

DP-3 implements retry scheduling, atomic work claiming, quarantine evidence, controlled manual review contracts and processor observability. It does not activate a production scheduler, implement HTTP ingestion, modify canonical events, perform replay execution, create identity mappings, cut over projections or write to another track.

## SQL allocation

- SQL `32`: retry schedule, processing-attempt and quarantine evidence objects
- SQL `33`: atomic claim/lease/complete/fail/quarantine procedures and least-privilege grants
- SQL `34`: PostgreSQL 15/18 smoke, concurrency, lease-expiry and authority tests
- SQL `35+`: unallocated

SQL `01..31` remain protected.

## State model

Stable wire states:

- `retry_scheduled`
- `retry_claimed`
- `retry_succeeded`
- `retry_failed`
- `retry_exhausted`
- `quarantined`
- `reviewed_retain`
- `reviewed_release_requested`
- `manual_hold`

Canonical source rows remain immutable. Processing attempts, retry decisions, quarantine entries and reviews are append-only evidence. A queue/lease control row may change only through approved procedures and must not be used as historical evidence.

## Retry classification

Automatically retryable classes:

- `database_lock_timeout`
- `serialization_failure`
- `dependency_unavailable`
- `worker_interrupted`
- `rate_limited`

Immediate non-retryable quarantine classes:

- `schema_unsupported`
- `source_hash_mismatch`
- `source_binding_invalid`
- `payload_unmappable`
- `payload_too_large`
- `privacy_policy_violation`
- `projection_invariant_failed`
- `lineage_source_missing`
- `manual_hold`

Unknown failure class is fail-closed and quarantined as `unclassified_failure`; it is never retried automatically.

## Retry budget

Policy ID: `data-projection-retry-v1`.

- initial processing attempt is attempt `1`
- maximum automatic retries after the initial attempt: `5`
- maximum total executions: `6`
- retry delays: `1m`, `5m`, `30m`, `2h`, `12h`
- jitter: deterministic bounded scheduling jitter of `0..10%`, derived from a privacy-safe work reference; jitter changes schedule only
- after retry `5` fails: terminal `retry_exhausted` quarantine
- three consecutive failures with the same normalized failure signature may quarantine early as `repeated_deterministic_failure`
- authorization, validation, privacy, fingerprint, lineage and invariant failures are never retried automatically

## Claim and lease

- processor role: `jc_data_retry_processor`
- quarantine review role: `jc_data_quarantine_reviewer`
- replay role remains `jc_data_replay_executor` with no replay execution grant in DP-3
- atomic claim uses row locking with `SKIP LOCKED` or an equivalent PostgreSQL-safe primitive
- default lease: `60 seconds`
- heartbeat interval: `20 seconds`
- expired leases may be reclaimed only by the approved claim procedure
- default claim batch maximum: `100`
- completion/failure must bind the claim token, processor instance reference and attempt reference
- stale or foreign claim completion is rejected

## Quarantine review

DP-3 may record review evidence but does not execute replay or release into production processing.

Release-request prerequisites:

- authorized reviewer capability
- operation and audit references
- explicit reason code
- target schema/mapping/policy/consumer version
- successful dry-run evidence reference
- no identity, idempotency, privacy or source-integrity conflict

A release request creates new evidence. It does not mutate the canonical source, delete quarantine history or authorize replay by itself.

## Observability contract

Required metrics:

- retry scheduled, claimed, succeeded, failed and exhausted counts
- quarantine count by stable reason
- retry latency and age-to-success
- ready queue depth
- oldest ready age
- active lease count
- expired lease reclaim count
- stale claim rejection count
- repeated failure signature count

Required dimensions are bounded to policy version, failure class, event family, projection name/version and result. Actor refs, idempotency keys, payloads, raw errors, tokens and unrestricted text are prohibited dimensions/log fields.

Required alerts for a future operational integration:

- oldest ready age exceeds `15 minutes`
- retry exhausted rate exceeds `1%` over `15 minutes` with at least `100` attempts
- quarantine rate exceeds `2%` over `15 minutes` with at least `100` attempts
- expired lease reclaim count exceeds `10` over `10 minutes`
- repeated deterministic failure signature reaches `20` events over `10 minutes`

DP-3 implements metric/evidence contracts and tests only. Production alert routing belongs to Operations.

## Retention

Retry attempts, quarantine entries and review evidence use the approved `90-day` technical retention metadata. Automatic purge and physical deletion remain disabled.

## Protected authority

- no canonical source mutation
- no SQL `01..31` modification
- no Recommendation/Search/Intelligence runtime or authority change
- no projection cutover
- no identity mapping/join
- no replay execution grant
- no production scheduler activation
- production shadow remains disabled
- kill switch remains enabled
- sampling remains `0 BPS`
- cohort remains empty
- Search cutover remains not started

## Entry decision

`DP-3 ENTRY: AUTHORIZED AFTER THIS SC DECISION PR IS MERGED`
