# RCA-2 Circuit Breaker and Kill-Switch Report

`ACTUAL_WORK_START_SHA=ed5708bd4da12eaea8180043f5cd7f6eb13c3099`

**Focus:** Lane isolation and global fail-closed control

## Scope

RCA-2 isolated non-production Recommendation Data controlled runtime dark read only. No production activation, candidate serving, source replacement, DB write, event emission, or authority transfer.

## Current Baseline

Actual work-start SHA: `ed5708bd4da12eaea8180043f5cd7f6eb13c3099`. PR #28 is the SC-5 authorization merge. SC-5 exact-final-head: `a3e7045c42bf854967263f8911389afd96fda4f4`; its tree is equivalent to the work-start merge tree.

## Implementation

Dedicated `com.jc.backend.recommendation.rca2` runtime, post-commit response hook, bounded executor, fail-closed flag refresh, lane breakers, kill switches, synthetic/test-account identity policy, redacted evidence, metrics, contract-only candidate adapter, and independent verifier.

## Authority

`PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY`; `SHADOW_RESULT_AUTHORITY=NONE`; `SHADOW_RESULT_SERVING=FORBIDDEN`; `AUTHORITY_TRANSFER=FORBIDDEN`.

## Dependencies

Current `RecommendationP1ProfileSource`, `RecommendationP2ObservationSource`, Spring Servlet/Security commit wrapper, Micrometer, and isolated contract adapters. RCA-1B query registry remains test-only.

## Runtime Environment

`RCA2_EXECUTION_ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME`; Spring profile excludes `prod` and `production`.

## Runtime Model

`RCA2_RUNTIME_MODEL=ASYNC_POST_RESPONSE_SHADOW`; primary response commit precedes task submission; no primary future join.

## Feature Flag

Required, default OFF, fail-closed on missing/unknown/malformed/expired/stale/unverified config. Refresh 30s, stale 120s, max TTL 30 days.

## Traffic Boundary

`INITIAL_TRAFFIC_PERCENT=0`; `PRODUCTION_TRAFFIC_PERCENT=0`; `MAX_PRODUCTION_DARK_READ_PERCENT=0`. No nonzero traffic is activated by this PR.

## Primary/Shadow Authority

Primary serialized response remains unchanged for success, mismatch, timeout, exception, queue rejection, breaker open, kill, identity block, checkpoint mismatch, lineage mismatch, and stale candidate.

## Timeout/Fallback

Queue timeout 50ms, max task age 1000ms, total timeout 500ms, connection/read contracts 100/300ms, retry NONE, late result DISCARD, every failure keeps primary.

## Circuit Breaker

Independent P1/P2 CLOSED/OPEN/HALF_OPEN breakers; 20 minimum samples, 25% failure, 20% timeout, 60s open, two half-open probes. Global kill blocks both lanes.

## Credential/Network

Operations-owned, secret-manager, non-production-only, max TTL 3600s, read-only, TLS, deny-by-default explicit allowlist, production route forbidden. Actual credential and route tests are NOT_EXECUTED.

## Identity/Privacy

Synthetic or explicitly allowlisted hashed test account only. Actual production identity, anonymous fallback, nearest-user fallback, alternate subject fallback, raw ID logging, and production identity join are blocked.

## P1 Result

`P1_RUNTIME_DARK_READ_ONLY`; contract adapter comparison preserves `ORDERING_NOT_COMPARABLE`, `EVENT_GRAIN_MISSING`, `EXPLICIT_PREFERENCE_MISSING`, `TRANSFORM_POLICY_MISSING`, and `FINGERPRINT_SEMANTICS_PROTECTED` as expected/protected gaps.

## P2 Result

`P2_RUNTIME_DARK_READ_ONLY`; current exposure authority/window/event/fallback/observation contracts are asserted. `STALE_UNEXPOSED_ASSIGNMENT_GAP` and `OBSERVATION_DEDUPE_GAP` remain migration gaps.

## Checkpoint/Lineage

Opaque checkpoint, monotonic sequence, UTC captured-at, source/candidate/schema/deployment versions, artifact SHA, and SHA-256 lineage fingerprint are required. Freshness is measurement-only; no live lag PASS threshold exists.

## Observability

17 required metrics plus three blocked-side-effect counters, low-cardinality labels only, explicit latency buckets, metric owner Operations/Reliability review, raw result/identity/credential retention NONE.

## Evidence

`verification/rca2/run_rca2_verification.py`, machine-readable evidence template, exact-head CI artifact, JUnit XML, and Gradle reports. Exact tested SHA is resolved from `GITHUB_SHA` and never guessed.

## Deployment

Only `application-rca2-isolated-nonproduction.yml`; flag OFF, traffic 0, global kill ON, no endpoint, no secret, no production namespace/host/DB route, no automatic promotion.

## Rollback

Seven levels: flag OFF, lane kill, global disable, config rollback, deployment rollback, credential revoke, network route revoke. Levels 6-7 are contract simulation and NOT_EXECUTED against real infrastructure.

## DB/SQL Impact

`DB_CHANGE=NONE`; `SQL_ALLOCATION=NOT_REQUIRED`; canonical SQL `01..52` protected; SQL `53+` absent; no table/view/role/grant/migration.

## Production Impact

None. Production profile activation, traffic, credentials, identity, network, DB validation, canary, load, replay, cutover, serving, and authority transfer are not authorized.

## Verification

Exact-head CI runs RCA-2 tests, current Recommendation/backend regression, IP-12.5 protected readiness, static production/SQL guards, response-mutation and no-write/no-event checks, and independent evidence generation.

## Risks

The candidate adapter is an isolated non-production application-contract adapter, not a production Data DB query. Live freshness threshold, actual credentials/routes, production traffic, and authority transfer remain separately gated.

## Exit Criteria

Complete only when exact-head CI and verifier pass while defaults remain OFF/0 and all prohibited production states remain NOT_EXECUTED or NOT_APPLICABLE.

## Handoff

Next stage requires separate System Coordination approval. `APPROVAL_STATUS=PENDING_USER_REVIEW`; this PR remains Draft and must not be automatically merged.
