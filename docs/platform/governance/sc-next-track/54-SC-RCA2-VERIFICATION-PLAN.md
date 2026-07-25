# SC RCA-2 Verification Plan

## Scope
Define governance and future implementation verification truth.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical.

## Decision
SC-5 runs an independent static verifier on exact PR head and records out-of-scope runtime checks as `NOT_EXECUTED`.

## Rationale
Governance authorization must not be mistaken for executed runtime evidence.

## Authority
SC owns verifier and entry evidence; implementation owners supply later runtime evidence.

## Dependencies
Full Git history, 20 required documents, machine-readable TSVs and protected repository baseline.

## Runtime Environment
Verify singular `ISOLATED_NON_PRODUCTION_RUNTIME` decision; do not execute it.

## Runtime Model
Verify singular `ASYNC_POST_RESPONSE_SHADOW` and finite executor limits.

## Feature Flag
Verify required/default OFF/fail-closed/stale/TTL/kill markers.

## Traffic Boundary
Verify initial and production traffic 0 and finite non-production stages.

## Primary/Shadow Authority
Verify current primary, shadow NONE, serving/mutation/write/event/feedback forbidden.

## Timeout/Fallback
Verify finite timeout/queue/concurrency, retry NONE, keep-primary fallback, breaker and kill switches.

## Credential/Network
Verify owner/scope/storage/TTL/TLS/allowlist and production route blocked.

## Identity/Privacy
Verify synthetic/test-account only, lifecycle decisions, no raw IDs and fail closed.

## P1 Result Boundary
Verify P1 markers and expected-gap inventory.

## P2 Result Boundary
Verify P2 markers, migration gaps and protected authority inventory.

## Checkpoint/Lineage
Verify required metadata and `BLOCKED_PENDING_MEASUREMENT` freshness policy.

## Observability
Verify 17 metrics, bounded labels, retention and raw-data prohibitions.

## Rollback
Verify seven ordered rollback levels.

## DB/SQL Impact
Verify SQL `01..52`, absent `53+`, no DB/migration/source diff and no persistent assets.

## Production Impact
Verify production activation/traffic/route/identity/authority transfer remain unauthorized.

## Verification
Statuses: `PASS`, `FAIL`, `NOT_EXECUTED`, `NOT_APPLICABLE`. Runtime dark read, feature flag runtime, credentials, production route, canary, load, replay, production validation, actual identity, activation and transfer are never PASS in SC-5.

## Risks
A changed head invalidates previous evidence.

## Exit Criteria
Verifier result PASS, empty failures, governance-only diff and exact tested SHA.

## Handoff
The implementation PR extends this plan with actual runtime tests without rewriting SC-5 history.