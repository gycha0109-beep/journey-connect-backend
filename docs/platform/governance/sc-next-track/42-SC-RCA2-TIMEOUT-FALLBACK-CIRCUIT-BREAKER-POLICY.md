# SC RCA-2 Timeout Fallback and Circuit Breaker Policy

## Scope
Define finite resource, failure-isolation and breaker controls.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical.

## Decision
```text
SHADOW_CONNECTION_TIMEOUT_MS=100
SHADOW_READ_TIMEOUT_MS=300
SHADOW_TOTAL_TIMEOUT_MS=500
SHADOW_QUEUE_TIMEOUT_MS=50
SHADOW_CONTEXT_MAX_AGE_MS=1000
MAX_SHADOW_CONCURRENCY=4
MAX_SHADOW_QUEUE_DEPTH=100
RETRY_POLICY=NONE
LATE_RESULT_POLICY=DISCARD
CIRCUIT_BREAKER_REQUIRED=YES
GLOBAL_KILL_SWITCH_REQUIRED=YES
LANE_KILL_SWITCH_REQUIRED=YES
```

## Rationale
Finite budgets prevent shadow work from becoming a second production workload or extending primary latency.

## Authority
Operations owns thresholds and reset procedures; lane owners review lane health; SC approves changes.

## Dependencies
Dedicated executor, cancellation propagation, timeout metrics and audited kill switches.

## Runtime Environment
Isolated non-production only.

## Runtime Model
Post-response async tasks; expired context is never executed.

## Feature Flag
OFF prevents submission; kill switches override ON.

## Traffic Boundary
Initial 0%; no increase until overload and breaker tests pass.

## Primary/Shadow Authority
Primary timeout and response are unchanged.

## Timeout/Fallback
Timeout, exception, queue rejection, stale context, cancellation and open circuit all use `KEEP_PRIMARY_RESULT`. No retry.

## Credential/Network
Timeout applies to all external connection/read activity.

## Identity/Privacy
Timed-out or cancelled context is destroyed without raw evidence.

## P1 Result Boundary
P1 breaker is independent.

## P2 Result Boundary
P2 breaker is independent.

## Checkpoint/Lineage
Stale or incompatible checkpoints fail closed and count as shadow failure, not mismatch success.

## Observability
Track timeout, exception, rejection, late discard, active concurrency and breaker state by lane.

## Rollback
Breaker minimum sample `20`; failure-rate threshold `25%`; timeout-rate threshold `20%`; open `60s`; half-open probes `2`. Critical violations trigger global kill.

## DB/SQL Impact
None.

## Production Impact
None.

## Verification
Implementation must test CLOSED/OPEN/HALF_OPEN, queue saturation, cancellation, late discard and post-failure recovery. SC-5 records `NOT_EXECUTED`.

## Risks
Thresholds are initial non-production safety values, not production SLOs.

## Exit Criteria
Finite budgets, lane isolation, no retry storm and verified breaker/kill behavior.

## Handoff
Implement exactly these initial limits; changes require Operations and SC review.