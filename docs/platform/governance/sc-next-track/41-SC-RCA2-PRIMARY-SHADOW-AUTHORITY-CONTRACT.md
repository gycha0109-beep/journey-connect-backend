# SC RCA-2 Primary and Shadow Authority Contract

## Scope
Define the immutable serving boundary.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical.

## Decision
```text
PRIMARY_RESULT_SOURCE=CURRENT_AUTHORITATIVE_SOURCE
PRIMARY_RESULT_MUTATION=FORBIDDEN
SHADOW_RESULT_AUTHORITY=NONE
SHADOW_RESULT_SERVING=FORBIDDEN
SHADOW_RESULT_FALLBACK=FORBIDDEN
SHADOW_RESULT_CACHE_WRITE=FORBIDDEN
SHADOW_RESULT_DATABASE_WRITE=FORBIDDEN
SHADOW_RESULT_EVENT_EMISSION=FORBIDDEN
SHADOW_RESULT_NOTIFICATION=FORBIDDEN
SHADOW_RESULT_RANKING_FEEDBACK=FORBIDDEN
SHADOW_RESULT_USER_VISIBLE=NO
SHADOW_FAILURE_USER_IMPACT=NONE
```

## Rationale
RCA-2 measures candidate behavior; it does not adopt candidate authority.

## Authority
Intelligence and Reliability retain lane authority. SC alone may open a later authority-transfer review.

## Dependencies
Primary and shadow values must remain structurally separated through orchestration, evidence and tests.

## Runtime Environment
Isolated non-production only.

## Runtime Model
Shadow starts after primary response commit and cannot join the response future.

## Feature Flag
Default OFF; flag controls observation only.

## Traffic Boundary
Initial 0%; no production traffic.

## Primary/Shadow Authority
Candidate quality cannot trigger replacement, correction, blending or alternate fallback.

## Timeout/Fallback
All shadow failure modes return no shadow value and preserve primary.

## Credential/Network
Shadow credentials cannot access primary mutation surfaces.

## Identity/Privacy
Identity may be used only for approved non-production purpose binding.

## P1 Result Boundary
Current `RecommendationP1ProfileSource` and `recommendation_p1_profile_snapshot` remain authoritative.

## P2 Result Boundary
Current `RecommendationP2ObservationSource`, exposure authority, dataset and metrics remain authoritative.

## Checkpoint/Lineage
Mismatch changes classification, never the primary response.

## Observability
Only normalized digests and classifications may be emitted.

## Rollback
Any response mutation signal triggers global kill and incident review.

## DB/SQL Impact
No write or persistent evidence object.

## Production Impact
None; serving and transfer forbidden.

## Verification
Implementation must prove response bytes/status and primary side effects are identical with shadow enabled or disabled; SC-5 does not execute this test.

## Risks
Accidental future composition of primary and shadow objects is a critical violation.

## Exit Criteria
No primary mutation, no served shadow, no DB write, no event and no authority transfer.

## Handoff
Keep authority assertions executable in the separate implementation PR.