# SC RCA-2 P1 Runtime Dark Read Decision

## Scope
Define independent P1 runtime comparison semantics.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; P1 baseline `RECONCILED_WITH_EXPECTED_GAPS`.

## Decision
```text
P1_RUNTIME_DARK_READ_ONLY
CURRENT_P1_AUTHORITY_UNCHANGED
P1_SHADOW_RESULT_NOT_SERVED
```
Observe authoritative/candidate normalized digest, size, shared fields, deterministic-derived fields, 7/30/90 windows, checkpoint, lineage, latency, timeout, exception, stale, duplicate, empty and ordering classification.

## Rationale
Runtime evidence must not reinterpret known semantic non-equivalence as regressions or adoption authority.

## Authority
Intelligence is accountable and blocking; Data supplies candidate/checkpoint/lineage; Operations owns runtime controls; SC owns entry/exit.

## Dependencies
Versioned P1 comparison contract and expected-gap classification.

## Runtime Environment
Isolated non-production only.

## Runtime Model
Independent P1 bounded post-response task and breaker.

## Feature Flag
P1 lane flag default OFF.

## Traffic Boundary
P1 stage independent; initial 0%.

## Primary/Shadow Authority
`RecommendationP1ProfileSource` and `recommendation_p1_profile_snapshot` remain authoritative.

## Timeout/Fallback
Any P1 shadow failure keeps primary.

## Credential/Network
P1 dependencies explicit and non-production only.

## Identity/Privacy
Synthetic/test-account only.

## P1 Result Boundary
Known gaps: `ORDERING_NOT_COMPARABLE`, `EVENT_GRAIN_MISSING`, `EXPLICIT_PREFERENCE_MISSING`, `TRANSFORM_POLICY_MISSING`, `FINGERPRINT_SEMANTICS_PROTECTED`. They are not unexpected mismatch. Aggregate-to-event fabrication is forbidden.

## P2 Result Boundary
P2 is not combined with or masked by P1.

## Checkpoint/Lineage
Mismatch/stale/incompatible values are separate classifications.

## Observability
P1 metrics and alerts use lane=P1 and bounded dimension/error labels.

## Rollback
P1 lane kill does not disable or conceal P2 evidence.

## DB/SQL Impact
None.

## Production Impact
No P1 serving, replacement or authority transfer.

## Verification
Implementation must test expected-gap exclusion and unexpected mismatch alerting; SC-5 records runtime as `NOT_EXECUTED`.

## Risks
Ordering and protected fingerprint semantics remain non-comparable.

## Exit Criteria
P1 results independently classified with primary preserved and shadow not served.

## Handoff
Intelligence approves the exact comparator and exit recommendation in the implementation PR.