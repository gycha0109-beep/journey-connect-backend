# SC RCA-2 P2 Runtime Dark Read Decision

## Scope
Define independent P2 runtime comparison semantics.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; P2 baseline `RECONCILED_WITH_MIGRATION_GAPS`.

## Decision
```text
P2_RUNTIME_DARK_READ_ONLY
CURRENT_P2_AUTHORITY_UNCHANGED
P2_SHADOW_RESULT_NOT_SERVED
NO_AUTHORITY_TRANSFER
P2_EXPOSURE_AUTHORITY=recommendation_p2_experiment_exposure
OUTCOME_WINDOW_SECONDS=604800
ENGAGEMENT_EVENTS=click,like,save,share
FALLBACK_SOURCE=BOUND_RECOMMENDATION_RUN_ONLY
ONE_OBSERVATION_KEY=experimentRef,experimentVersion,subjectRef
```

## Rationale
P2 runtime evidence must preserve experiment authority and never become a second exposure/outcome source.

## Authority
Reliability is accountable and blocking; Data supplies candidate/checkpoint/lineage; Operations owns failure controls; SC owns entry/exit.

## Dependencies
Versioned P2 comparison contract and protected taxonomy.

## Runtime Environment
Isolated non-production only.

## Runtime Model
Independent P2 bounded post-response task and breaker.

## Feature Flag
P2 lane flag default OFF.

## Traffic Boundary
P2 stage independent; initial 0%.

## Primary/Shadow Authority
Current P2 source/exposure/dataset/metrics remain authoritative.

## Timeout/Fallback
Any P2 shadow failure keeps primary and emits no exposure or outcome.

## Credential/Network
Protected dataset/hash/release surfaces remain inaccessible.

## Identity/Privacy
Subject/session/run/exposure binding only for synthetic/test-account context.

## P1 Result Boundary
P1 status cannot mask P2 failure.

## P2 Result Boundary
Observe exposure reference, assignment/version/variant, binding, window, engagement filter, fallback, stale assignment, duplicate observation, checkpoint, lineage, latency, timeout and exception. Preserve `STALE_UNEXPOSED_ASSIGNMENT_GAP`, `OBSERVATION_DEDUPE_GAP`, `CANONICAL_DATASET_HASH_PROTECTED`, `RELEASE_EVIDENCE_PROTECTED`.

## Checkpoint/Lineage
P2 exact classification requires compatible checkpoint and lineage.

## Observability
Authority mismatch, unsupported event and unbound fallback are critical P2 alerts.

## Rollback
P2 lane kill is independent; authority violations trigger global kill.

## DB/SQL Impact
None.

## Production Impact
No exposure/outcome mutation, candidate serving or transfer.

## Verification
Implementation must prove no event emission and exact P2 protection; SC-5 runtime is `NOT_EXECUTED`.

## Risks
Two migration gaps remain open and cannot be reclassified as runtime success.

## Exit Criteria
P2 independently classified, current authority unchanged, shadow not served and no transfer.

## Handoff
Reliability approves P2 comparator, evidence integrity and exit recommendation.