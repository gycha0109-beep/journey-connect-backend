# SC RCA-2 Operations Reliability and Approval Matrix

## Scope
Assign blocking and required approvals.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; no approval is inferred from technical evidence.

## Decision
| Role | State | Scope |
|---|---|---|
| Intelligence | `BLOCKING_APPROVAL` | P1 comparator, gaps, mismatch, exit |
| Reliability | `BLOCKING_APPROVAL` | P2 authority/window/event/fallback, failure/evidence, exit |
| Data | `REQUIRED` | candidate contract, checkpoint, lineage, freshness, compatibility |
| Operations | `BLOCKING_APPROVAL` | environment, executor, flag, traffic, credential, network, timeout, breaker, deployment, rollback, observability |
| Privacy/Security | `BLOCKING_APPROVAL` | identity, redaction, credential, retention, audit, incident response |
| System Coordination | `BLOCKING_APPROVAL` | entry/exit, registry, SQL, authority, rollout ceiling, no-transfer |

## Rationale
Runtime work crosses semantic, operational and privacy boundaries.

## Authority
Each role approves only its scope; physical implementation location transfers no authority.

## Dependencies
Exact-head review packages and empty unresolved critical failures.

## Runtime Environment
Operations approval is mandatory before deployment or traffic.

## Runtime Model
Operations and SC approve executor/resource boundary.

## Feature Flag
Operations implements; SC approves ceiling; lane owners approve lane enablement.

## Traffic Boundary
Every nonzero stage requires all blocking approvals current for the exact artifact/config.

## Primary/Shadow Authority
Intelligence/Reliability and SC verify no serving or transfer.

## Timeout/Fallback
Operations and Reliability approve failure behavior.

## Credential/Network
Operations and Privacy/Security are blocking.

## Identity/Privacy
Privacy/Security is blocking; Data and lane owners are consulted.

## P1 Result Boundary
Intelligence blocking.

## P2 Result Boundary
Reliability blocking.

## Checkpoint/Lineage
Data required; lane owners and Operations consulted.

## Observability
Operations responsible; Reliability and Privacy/Security approve integrity/redaction.

## Rollback
Operations executes; relevant blocking owner and SC authorize reset.

## DB/SQL Impact
SC blocking for any changed requirement.

## Production Impact
No role is assigned production activation authority by SC-5.

## Verification
Machine evidence records statuses as required/blocking, not APPROVED.

## Risks
Stale approval after artifact/config change is invalid.

## Exit Criteria
All exact-head packages reviewed; no approval inferred automatically.

## Handoff
Implementation PR must present separate review evidence for every row.