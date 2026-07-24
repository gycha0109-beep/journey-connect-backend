# SC RCA-1 Operations and Reliability Prerequisite Matrix

## Scope

Allocate cross-track prerequisites for RCA-1 using `f802a105e46a62718616acaa7a3db6c172e7ed10`.

## Current Baseline

Model A has no runtime, DB credential, feature flag, scheduler or production control dependency.

## Decision

| Track | Model A | Model B | Model C |
|---|---|---|---|
| Intelligence | REQUIRED | REQUIRED | REQUIRED |
| Reliability | REQUIRED for P2 | REQUIRED | REQUIRED |
| Data | CONSULTED | REQUIRED for query/checkpoint | REQUIRED |
| SC | REQUIRED | REQUIRED | REQUIRED |
| Operations | CONSULTED / execution NOT_REQUIRED | REQUIRED | REQUIRED |
| Privacy/Security | CONSULTED | REQUIRED | REQUIRED |

Model A is approved. Model B is deferred. Model C is blocked.

## Rationale

P1 and P2 semantics require their owners even offline. Operations controls are only needed when credentials or runtime execution exist.

## Authority

Roles follow Track Governance and RACI.

## Dependencies

Intelligence P1 acceptance, Reliability P2 acceptance and SC exact-head exit decision.

## Allowed Changes

Approval records and lane-specific acceptance evidence.

## Forbidden Changes

Operations credentials or deployment work in RCA-1 Model A.

## Identity/Privacy

Privacy review is limited to synthetic/redacted evidence. Real identity requires a new approval.

## DB/SQL Impact

Model A none. Model B would require read-only environment controls but no production DB. Model C is outside scope.

## Production Impact

None.

## Verification

Ensure no runtime prerequisite is marked complete and no Model B/C permission is inferred.

## Risks

Shared implementation ownership may obscure P2 accountability; Reliability sign-off is mandatory.

## Exit Criteria

P1 and P2 acceptance owners are explicit and no operational authority is silently assigned.

## Handoff

RCA-1 implementation PR must include separate P1 and P2 reviewer/acceptance sections.
