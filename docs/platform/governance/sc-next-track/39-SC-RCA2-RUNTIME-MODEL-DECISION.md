# SC RCA-2 Runtime Model Decision

## Scope
Select the controlled shadow invocation model.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical.

## Decision
`RCA2_RUNTIME_MODEL=ASYNC_POST_RESPONSE_SHADOW`. Use a dedicated bounded executor only after the authoritative response is committed. Model A is CI/test fallback. Model C is blocked pending queue/event contracts.

## Rationale
Post-response execution minimizes user-latency coupling without adding queue infrastructure or serving authority.

## Authority
Operations owns execution resources; Intelligence owns P1 semantics; Reliability owns P2 semantics; SC owns entry and exit.

## Dependencies
A separate implementation PR must prove the post-response boundary, bounded executor and task-loss evidence before enablement.

## Runtime Environment
Only `ISOLATED_NON_PRODUCTION_RUNTIME`; production environment blocked.

## Runtime Model
Concurrency `4`, queue depth `100`, queue wait `50ms`, maximum task age `1000ms`; no common/unbounded executor.

## Feature Flag
Required, default OFF, unknown/missing/stale/expired values resolve OFF.

## Traffic Boundary
Initial `0%`; production ceiling `0%`.

## Primary/Shadow Authority
Primary remains current P1/P2. Shadow authority is NONE and shadow is never served.

## Timeout/Fallback
Connect `100ms`, read `300ms`, total `500ms`, retry NONE, late result DISCARD, every failure keeps primary.

## Credential/Network
Non-production short-lived workload identity and allowlisted TLS route only.

## Identity/Privacy
Synthetic or explicit non-production test account only; actual production identity blocked.

## P1 Result Boundary
`P1_RUNTIME_DARK_READ_ONLY`; expected/protected gaps remain separate.

## P2 Result Boundary
`P2_RUNTIME_DARK_READ_ONLY`; exposure/window/event/fallback authority remains protected.

## Checkpoint/Lineage
Checkpoint and lineage required; live freshness threshold blocked pending measurement.

## Observability
Lane-separated execution, rejection, timeout, exception, latency and late-discard metrics.

## Rollback
Flag OFF, lane kill and global disable precede code rollback.

## DB/SQL Impact
None; SQL `53+` remains unallocated.

## Production Impact
None; production activation and authority transfer forbidden.

## Verification
SC-5 verifies the decision only. Runtime dark read is `NOT_EXECUTED`.

## Risks
The repository has no previously validated dedicated async boundary; implementation must not infer one.

## Exit Criteria
Future exit requires verified post-response isolation, finite resources and no primary latency/response mutation.

## Handoff
Implement in a separate Draft PR with feature flag OFF and traffic 0.