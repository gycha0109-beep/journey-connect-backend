# SC RCA-1 Exit Criteria and RCA-2 Handoff Boundary

## Scope

Define what RCA-1 completion means and what is transferred to later phases from `f802a105e46a62718616acaa7a3db6c172e7ed10`.

## Current Baseline

RCA-1 is entry-authorized but not implemented or executed.

## Decision

RCA-1 exit requires:

```text
P1_RECONCILIATION_EXECUTED
P1_DIFFERENCES_CLASSIFIED
P2_RECONCILIATION_EXECUTED
P2_DIFFERENCES_CLASSIFIED
IDENTITY_BOUNDARY_ENFORCED
PROTECTED_AUTHORITY_UNCHANGED
NO_PRODUCTION_TRAFFIC
NO_AUTHORITY_TRANSFER
```

RCA-1 completion does not mean runtime adoption, production adoption, cutover authorization, old-source deprecation or Data authority.

RCA-1B is the only candidate for non-production read-only DB reconciliation and requires a separate SC/Operations proposal.

RCA-2 is `Controlled Runtime Dark Read`; it requires Operations deployment/credentials/feature flag/timeout/fallback/observability, Reliability guardrails, privacy/security review and a separate production-impact decision.

## Rationale

Offline evidence cannot justify runtime or production conclusions.

## Authority

SC approves exit and next-phase entry. Intelligence and Reliability approve their lanes. Operations owns RCA-2 execution.

## Dependencies

Completed lane evidence and exact-head implementation verification.

## Allowed Changes

Handoff documents and future proposals only after RCA-1 exit review.

## Forbidden Changes

Automatic transition from RCA-1 PASS to Model B/C, source deprecation, traffic or authority transfer.

## Identity/Privacy

Real identity mapping remains a prerequisite for any real-user Model B/C scope.

## DB/SQL Impact

RCA-1 none. RCA-1B/RCA-2 must separately decide.

## Production Impact

RCA-1 none. RCA-2 impact is undecided and not authorized.

## Verification

Exit report must list lane results, expected gaps, migration-required dimensions, identity mode and exact tested SHA.

## Risks

Stakeholders may read “shadow reconciliation complete” as cutover-ready. Required markers must explicitly deny that inference.

## Exit Criteria

The criteria above plus SC review and explicit user-approved merge of the implementation PR.

## Handoff

No RCA-2 work begins from this document alone.
