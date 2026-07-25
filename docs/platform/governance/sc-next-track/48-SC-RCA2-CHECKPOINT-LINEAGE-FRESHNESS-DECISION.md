# SC RCA-2 Checkpoint Lineage and Freshness Decision

## Scope
Define live comparability metadata without inventing a lag threshold.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; RCA-1B used deterministic zero-lag fixtures.

## Decision
```text
CHECKPOINT_REQUIRED=YES
LINEAGE_FINGERPRINT_REQUIRED=YES
RUNTIME_FRESHNESS_POLICY=BLOCKED_PENDING_MEASUREMENT
CLOCK_SOURCE=UTC_MONOTONIC_CAPTURE
INCOMPATIBLE_CHECKPOINT_POLICY=FAIL_CLOSED_KEEP_PRIMARY
```
Required metadata: source/candidate opaque checkpoint, monotonic sequence, captured-at UTC, schema version, candidate contract version, deployment version, artifact SHA and lineage fingerprint.

## Rationale
Zero-lag fixture policy cannot be copied to asynchronous runtime, and no evidence supports a live tolerance.

## Authority
Data owns checkpoint/lineage/freshness definitions; lane owners classify effects; Operations owns clocks/telemetry; SC approves a future threshold.

## Dependencies
Non-production measurement evidence with distributions by lane and deployment.

## Runtime Environment
Measurement is isolated non-production only.

## Runtime Model
Capture primary context before task submission and candidate metadata at execution; expired context is discarded.

## Feature Flag
Freshness measurement requires enabled lane flag; stale policy cannot be bypassed by flag.

## Traffic Boundary
Initial 0%; stage increase requires measurement evidence.

## Primary/Shadow Authority
Stale/incompatible candidate never replaces primary.

## Timeout/Fallback
Missing, reversed, incompatible or expired checkpoint fails closed and keeps primary.

## Credential/Network
Metadata collection cannot broaden object access.

## Identity/Privacy
Checkpoint and lineage contain no raw subject identifiers.

## P1 Result Boundary
Known P1 semantic gaps remain separate from freshness mismatch.

## P2 Result Boundary
P2 authority mismatch remains separate and critical.

## Checkpoint/Lineage
No `MAX_ALLOWED_LAG` is approved. Record measured lag buckets and propose a later Data/Operations/SC decision.

## Observability
Checkpoint mismatch, lineage mismatch, stale candidate and lag histogram use bounded labels.

## Rollback
Repeated incompatibility opens the lane breaker; redaction/authority failure triggers global kill.

## DB/SQL Impact
None.

## Production Impact
No production freshness policy or validation.

## Verification
SC-5 verifies required fields and blocked threshold. Runtime measurement is `NOT_EXECUTED`.

## Risks
Without live measurements, stale classification beyond structural incompatibility remains unresolved.

## Exit Criteria
Future RCA-2 exit requires measured distributions, explicit threshold decision and fail-closed tests.

## Handoff
Instrument measurement only; do not invent or hard-code an acceptance lag.