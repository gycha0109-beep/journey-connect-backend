# SC RCA-2 Runtime Environment Decision

## Scope

Select the RCA-2 execution environment.

## Current Baseline

Work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; PR #27 merged with identical tree.

## Decision

Environment A is required for CI simulation. Environment B `ISOLATED_NON_PRODUCTION_RUNTIME` is approved. Environment C production dark read is blocked.

## Rationale

Environment B tests runtime isolation without production data or authority.

## Authority

Operations owns the environment; SC owns entry and production gates.

## Dependencies

Separate implementation PR and blocking approvals before nonzero traffic.

## Runtime Environment

No production data, identity, credential, route or traffic.

## Runtime Model

Bounded async post-response only.

## Feature Flag

Required and default OFF.

## Traffic Boundary

Initial 0%; production 0%.

## Primary/Shadow Authority

Primary current P1/P2; shadow NONE and not served.

## Timeout/Fallback

100/300/500 ms, concurrency 4, queue 100, retry NONE, keep primary.

## Credential/Network

Short-lived non-production workload identity and explicit TLS allowlist only.

## Identity/Privacy

Synthetic or explicit non-production test account only.

## P1 Result Boundary

Independent P1 lane; authority unchanged.

## P2 Result Boundary

Independent P2 lane; authority unchanged and no transfer.

## Checkpoint/Lineage

Required; freshness threshold blocked pending measurement.

## Observability

Low-cardinality, redacted, lane-separated telemetry.

## Rollback

Flag, lane/global kill, config/deploy/credential/network rollback.

## DB/SQL Impact

None; SQL `53+` remains unallocated.

## Production Impact

None; production activation not authorized.

## Verification

Environment decision singularity and no-production markers are verified; runtime is not executed.

## Risks

Non-production deployment and credentials remain unimplemented.

## Exit Criteria

Environment isolation, teardown, deny-by-default network and no production route must be proven.

## Handoff

Implement only Environment B in a separate Draft PR.