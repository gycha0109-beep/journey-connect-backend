# RCA-2 Recommendation Data Controlled Runtime Dark Read Implementation Handoff Prompt

## Scope
Implement the SC-5-authorized isolated non-production dark-read boundary in a separate Draft PR. Do not implement production enablement or authority transfer.

## Current Baseline
Repository `gycha0109-beep/journey-connect-backend`; authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical.

## Decision
Implement only:
- default-off fail-closed feature flag;
- isolated non-production post-response orchestrator;
- dedicated bounded executor: concurrency 4, queue 100, queue wait 50ms, context age 1000ms;
- connect/read/total timeout 100/300/500ms, retry NONE, late discard;
- current-primary preservation and shadow no-serving assertions;
- lane breakers, lane/global kill switches;
- synthetic/test-account allowlist only;
- redacted digest comparison, checkpoint/lineage measurement;
- P1/P2 independent telemetry and classifications;
- non-production deployment/rollback verification;
- exact-head review packages.

## Rationale
RCA-2 gathers runtime evidence without adopting candidate results.

## Authority
Intelligence P1; Reliability P2; Data candidate/checkpoint/lineage; Operations runtime/deployment; Privacy/Security identity/redaction; SC entry/exit/SQL/rollout/authority.

## Dependencies
SC-5 merge after explicit approval, separate branch/PR, exact artifact/config, blocking approvals before nonzero traffic.

## Runtime Environment
`ISOLATED_NON_PRODUCTION_RUNTIME`; CI simulation required; production blocked.

## Runtime Model
`ASYNC_POST_RESPONSE_SHADOW`; task cannot join or delay primary response. No unbounded/common executor and no queue/event infrastructure.

## Feature Flag
Required/default OFF; unknown/missing/stale/expired OFF; refresh 30s, stale 120s, TTL 30d; deploy does not enable.

## Traffic Boundary
Start 0%; do not increase without explicit stage approval. Production 0%.

## Primary/Shadow Authority
Primary current P1/P2 only; shadow NONE. Never serve, blend, fallback, cache/write, emit event/notification, feed ranking or mutate response.

## Timeout/Fallback
Every failure keeps primary. Breaker: min 20, failure 25%, timeout 20%, open 60s, half-open 2 probes.

## Credential/Network
Short-lived non-production workload identity, secret manager, TLS, explicit deny-by-default allowlist. No production or write/owner credential.

## Identity/Privacy
Synthetic/test account only; encrypted allowlist, max 30d entry, 90d hashed audit, immediate invalidation/deletion, no raw IDs.

## P1 Result Boundary
Preserve five expected/protected gaps; no aggregate-to-event fabrication.

## P2 Result Boundary
Preserve exposure authority, 604800 window, click/like/save/share, bound fallback, two migration gaps and protected dataset/release scope.

## Checkpoint/Lineage
Collect opaque checkpoints, UTC capture, sequence, versions, artifact SHA and lineage. Do not invent live lag threshold; measurement only.

## Observability
Implement the 17 required low-cardinality metrics; metrics 30d, logs 14d, artifacts 90d; critical 100%, success detail <=10%; raw result/identity/credential retention NONE.

## Rollback
Verify flag OFF, lane kill, global disable, config rollback, deployment rollback, credential revoke and network revoke.

## DB/SQL Impact
No SQL/table/view/role/grant/persistent evidence. If required, stop with `RCA2_ENTRY_BLOCKED_BY_SQL_ALLOCATION`; do not create SQL `53+`.

## Production Impact
None. Production source, traffic, route, credential, identity, DB, serving, activation, cutover and authority transfer forbidden.

## Verification
Run CI simulation, flag fail-closed, no-response-mutation, no-write/event, executor saturation, timeouts/cancellation, breakers/kills, lane separation, redaction, checkpoint/lineage, rollback and protected regressions. Runtime checks cannot be claimed before execution.

## Risks
Async boundary, freshness threshold, production controls and open P1/P2 gaps remain.

## Exit Criteria
Meet all SC-5 exit markers on one exact head with blocking reviews.

## Handoff
Create a Draft PR; do not mark Ready or merge without explicit user approval. RCA-2 exit does not authorize production or transfer.