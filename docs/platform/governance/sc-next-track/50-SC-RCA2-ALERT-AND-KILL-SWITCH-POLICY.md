# SC RCA-2 Alert and Kill Switch Policy

## Scope
Define alert classes and automatic disable conditions.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; production activation remains unauthorized.

## Decision
Global and lane kill switches are mandatory. Immediate global kill candidates: redaction failure, production response mutation, candidate DB write attempt, P2 authority mismatch, production route detection, traffic-ceiling breach. Lane breaker/kill candidates: timeout/exception threshold, queue rejection, checkpoint/lineage incompatibility, unexpected P1 mismatch, unsupported P2 event, unbound fallback.

## Rationale
Critical violations must stop shadow work before human diagnosis.

## Authority
Operations executes kill; Privacy/Security owns redaction incidents; Reliability owns P2 authority incidents; SC controls reset authorization.

## Dependencies
Alert routing, runbook, immutable audit and zero-execution verification.

## Runtime Environment
Isolated non-production only.

## Runtime Model
Kill state is checked before submission and before external access.

## Feature Flag
Global/lane kill overrides every feature flag and cohort decision.

## Traffic Boundary
Ceiling breach triggers global kill; no automatic re-enable.

## Primary/Shadow Authority
Mutation or serving signal is a critical incident.

## Timeout/Fallback
Breaker thresholds use minimum 20 samples, 25% failure or 20% timeout, open 60s and two half-open probes.

## Credential/Network
Unexpected route/credential class triggers global kill and revoke.

## Identity/Privacy
Raw identity or redaction failure triggers global kill and evidence quarantine.

## P1 Result Boundary
Unexpected P1 mismatch may disable P1 only unless authority/mutation is implicated.

## P2 Result Boundary
Exposure authority mismatch, unsupported event or unbound fallback disables P2 and may trigger global kill.

## Checkpoint/Lineage
Structural incompatibility opens the affected lane breaker.

## Observability
Alert evidence includes lane, bounded class, flag/deployment/artifact version and timestamp only.

## Rollback
Reset requires root cause, clean verification, Operations execution and relevant blocking owner plus SC approval.

## DB/SQL Impact
None.

## Production Impact
None.

## Verification
Implementation must inject every alert condition and verify zero further execution. SC-5 records `NOT_EXECUTED`.

## Risks
A kill switch without independent verification is not an accepted control.

## Exit Criteria
Global/lane kill tested, audit complete, reset protected and no automatic restart.

## Handoff
Implement kill checks outside candidate computation and before any network/DB access.