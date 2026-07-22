# DP-3 Retry and Quarantine Processing Foundation

## Status

`IMPLEMENTED / VALIDATION PENDING EXACT-HEAD CI`

## Baseline

- authoritative main: `badd05fb2b16b33dd4e275e302098e0e61ed2d32`
- DP-2 merge: `0ff67aaf9a86b61be2b41c431a570a9f0d460f7c`
- SC entry: `SC-DP3-ENTRY-DECISIONS.md`
- canonical SQL protected: `01..31`
- DP-3 allocation: `32..34`

## Purpose

DP-3 adds a PostgreSQL-safe foundation for retrying projection work, quarantining unsafe or exhausted work, claiming jobs atomically, recovering expired leases, preserving append-only processing evidence, separating processor and reviewer capabilities, and exposing bounded operational metrics. It does not activate a scheduler, execute replay, connect a Spring worker, or cut over Recommendation/Search projections.

## Implementation

### Java contract

`jc-data-contracts` adds:

- `RetryPolicyV1`
- `RetryFailureClassV1`
- `QuarantineReasonV1`
- `RetryDecisionV1`
- `ProcessingOutcomeV1`
- `FailureSignatureV1`
- `LeaseBoundaryV1`

The policy is `data-projection-retry-v1`: initial execution plus five automatic retries, maximum six total executions, delays 1m/5m/30m/2h/12h, deterministic 0..10% scheduling jitter, 60-second lease and 20-second heartbeat contract. Unknown failure classes fail closed to `unclassified_failure`.

### SQL 32

Creates:

- mutable procedure-owned work/lease state
- append-only retry schedule evidence
- append-only claim and heartbeat evidence
- append-only completed processing attempt evidence
- append-only quarantine and review evidence
- append-only stale-claim rejection evidence
- 90-day retention metadata without deletion executor

Canonical events and DP-2 idempotency bindings are referenced read-only and remain immutable.

### SQL 33

Implements:

- deterministic jitter and delay functions
- idempotent work registration
- atomic `FOR UPDATE SKIP LOCKED` claim
- owner-only heartbeat
- exactly-once success completion
- retry scheduling and exhaustion quarantine
- immediate quarantine for non-retryable and unknown failures
- early quarantine after three consecutive equal normalized failure signatures
- append-only retain/release-request review evidence
- processor/reviewer/replay capability separation
- bounded observability and quarantine reason views

### SQL 34

Rollback-only smoke coverage:

- success exactly once
- duplicate success rejection
- lease expiry and reclaim
- stale/foreign heartbeat and completion rejection
- retry scheduling
- immediate and unknown-failure quarantine
- six-execution exhaustion
- repeated-signature early quarantine
- append-only enforcement
- role/grant boundaries
- observability and purge/replay absence

## Security and privacy

- no raw payload duplication
- no token, idempotency key, raw identity or stack trace in retry evidence
- bounded stable failure code and SHA-256 normalized signature only
- processor has function execution but no direct table mutation
- reviewer has safe views and append-only review function only
- replay executor receives no replay or processing execution grant
- PUBLIC has no Data retry/quarantine access
- SECURITY DEFINER functions use a fixed search path and narrow owner

## Observability

Views expose only bounded dimensions and aggregates: scheduled/ready/claimed/succeeded/failed/retry/exhausted counts, quarantine reason counts, active/expired leases, oldest-ready age, retry latency, age-to-success, stale rejection count, and repeated signature count. Actor/session/request/payload/idempotency values are not metric dimensions.

## Protection

- SQL `01..31` unchanged
- DP-2 event store and idempotency semantics unchanged
- no production Java/Kotlin or configuration change
- no Recommendation/Search/Intelligence source change
- no `/api/v1/explore` change
- scheduler, replay, shadow, traffic and cutover remain disabled

## Validation plan

- Java 21, `-Xlint:all -Werror`, `:jc-data-contracts:check`
- PostgreSQL 15 and 18 SQL `01..34`
- concurrency, lease, stale token and role/grant fixtures
- Recommendation PostgreSQL 15/18 regression
- Recommendation Java Core regression
- Backend/IP-12.5 and SC baseline gates
- protected diff verification

## Residual risks and next boundary

A real worker identity, scheduler, operational alert routing, processor telemetry export, replay execution and projection-specific business logic remain outside DP-3. The next stage requires a separate SC decision and must not infer production activation from these contracts.
