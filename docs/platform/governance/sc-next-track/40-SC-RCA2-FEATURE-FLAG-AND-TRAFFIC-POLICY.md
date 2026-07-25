# SC RCA-2 Feature Flag and Traffic Policy

## Scope
Define fail-closed enablement and rollout ceilings.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical.

## Decision
`FEATURE_FLAG_REQUIRED=YES`, `FEATURE_FLAG_DEFAULT=OFF`, `FAIL_CLOSED_ON_UNKNOWN_FLAG=YES`, `INITIAL_TRAFFIC_PERCENT=0`, `MAX_PRODUCTION_DARK_READ_PERCENT=0_UNTIL_SEPARATE_APPROVAL`.

## Rationale
Deploy, enable and traffic increase must be independently reversible decisions.

## Authority
Operations owns storage and refresh; SC owns ceiling changes; lane owners approve lane enablement.

## Dependencies
Versioned environment configuration, audit trail and global/lane kill switches.

## Runtime Environment
Flag scope is isolated non-production only.

## Runtime Model
Flag evaluation precedes bounded task submission; OFF never allocates a shadow task.

## Feature Flag
Owner Operations; environment-scoped storage; refresh `30s`; stale after `120s`; maximum authorization TTL `30d`; unknown/malformed/expired/stale values OFF; audit retention `90d`; no local default enable.

## Traffic Boundary
Stage 0 `0%`; Stage 1 `1%`; Stage 2 `10%`; Stage 3 `50%`; Stage 4 `100%` isolated non-production only. Every increase requires prior-stage technical PASS, minimum observation evidence and blocking approvals. Automatic rollout forbidden.

## Primary/Shadow Authority
Traffic sampling never changes primary authority or makes shadow visible.

## Timeout/Fallback
Rejected, disabled or expired flag state keeps primary and emits bounded telemetry only.

## Credential/Network
Flag enablement cannot grant credentials or network routes.

## Identity/Privacy
Eligible cohort is synthetic or approved non-production test-account allowlist only.

## P1 Result Boundary
P1 flag and kill switch are lane-specific.

## P2 Result Boundary
P2 flag and kill switch are lane-specific; P1 status cannot mask P2.

## Checkpoint/Lineage
Traffic increase requires checkpoint/lineage evidence; freshness threshold remains measurement-only.

## Observability
Record flag version, stage, lane and rejection class without subject identifiers.

## Rollback
Immediate FLAG_OFF, lane kill or global disable; verify execution count reaches zero.

## DB/SQL Impact
None.

## Production Impact
Production traffic remains `0%`; production activation not authorized.

## Verification
SC-5 checks policy markers only; runtime flag tests and canary are `NOT_EXECUTED`.

## Risks
No production percentage or canary duration is approved by this document.

## Exit Criteria
Future exit requires fail-closed flag tests, traffic ceiling enforcement and audited rollback.

## Handoff
Implement default-off configuration and keep all stages at 0 until explicit approval.