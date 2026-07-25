# SC RCA-2 Deployment and Rollback Policy

## Scope
Define deployment separation, promotion and rollback hierarchy.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; no runtime deployment exists.

## Decision
Primary service deployment, shadow artifact deployment, flag enablement and traffic increase are separate audited operations. Exact artifact digest/config version/environment promotion record and previous-version pin are required.

## Rationale
A deployment must not silently activate shadow traffic.

## Authority
Operations owns deployment/rollback; SC owns entry and traffic ceilings; blocking owners approve enablement.

## Dependencies
Isolated non-production environment, health/readiness checks and rollback runbook.

## Runtime Environment
Only non-production manifests may be implemented in RCA-2; SC-5 implements none.

## Runtime Model
Shadow component may be co-deployed only when logically/resource isolated and default OFF.

## Feature Flag
Deploy does not enable. Enable and percentage change are separate actions.

## Traffic Boundary
Initial 0%; manual stage changes only.

## Primary/Shadow Authority
Rollback never changes primary result authority.

## Timeout/Fallback
Health checks include executor capacity, breaker, flag and dependency reachability; dependency failure keeps primary.

## Credential/Network
Secrets/routes are separately provisioned and separately revocable.

## Identity/Privacy
Promotion requires current test-account allowlist and redaction verification.

## P1 Result Boundary
P1 lane can be deployed/disabled independently.

## P2 Result Boundary
P2 lane can be deployed/disabled independently.

## Checkpoint/Lineage
Deployment version and artifact SHA are included in evidence.

## Observability
Readiness requires dashboard/alert availability before traffic.

## Rollback
```text
LEVEL_1=FLAG_OFF
LEVEL_2=LANE_KILL_SWITCH
LEVEL_3=GLOBAL_SHADOW_DISABLE
LEVEL_4=CONFIG_ROLLBACK
LEVEL_5=DEPLOYMENT_ROLLBACK
LEVEL_6=CREDENTIAL_REVOKE
LEVEL_7=NETWORK_ROUTE_REVOKE
```
Each level requires trigger, owner, procedure, verification, recovery criteria and audit evidence.

## DB/SQL Impact
None.

## Production Impact
No production manifest, deployment or promotion approval.

## Verification
Implementation must verify all rollback levels in non-production; SC-5 records them `NOT_EXECUTED`.

## Risks
Code rollback alone is insufficient if flags, credentials or routes remain active.

## Exit Criteria
Artifact/config pinning, health/readiness, every rollback level and zero-execution verification pass.

## Handoff
Create non-production deployment assets only in the separate implementation PR.