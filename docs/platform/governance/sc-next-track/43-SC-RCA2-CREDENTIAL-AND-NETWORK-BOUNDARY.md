# SC RCA-2 Credential and Network Boundary

## Scope
Define least-privilege non-production access.

## Current Baseline
Authoritative work-start `3efbf96ebf25ae1645a62f35269c4b569425a9ca`; PR #27 merged; RCA-1B exact-final-head `dbb6b5397ad0fe675856b195e280faf9a0f3030c`; merge tree identical.

## Decision
`RUNTIME_CREDENTIAL_REQUIRED=YES_NONPRODUCTION_ONLY`; owner Operations; short-lived environment-specific workload identity; secret-manager storage; maximum TTL `1h`; rotation on issuance/expiry; TLS 1.2+; deny-by-default network allowlist; `PRODUCTION_ROUTE_ALLOWED=NO`.

## Rationale
RCA-1B’s ephemeral test role cannot become a persistent runtime credential.

## Authority
Operations owns credential/network controls; Privacy/Security is blocking approver; SC controls production route entry.

## Dependencies
Approved non-production service identity, secret manager, network policy and revocation procedure.

## Runtime Environment
Isolated non-production only.

## Runtime Model
Credential acquisition occurs per deployment identity, never per user payload.

## Feature Flag
Flag ON cannot create or broaden access.

## Traffic Boundary
Traffic remains 0 until credential and route negative tests pass.

## Primary/Shadow Authority
Access grants observation only and does not grant serving or write authority.

## Timeout/Fallback
Credential absence, expiry, rotation failure or route denial fails closed and keeps primary.

## Credential/Network
Hard-coded/repository credentials, broad DB credentials, owner/superuser, write grants, production endpoints and wildcard egress are forbidden. Audit credential version and decision, never secret value or endpoint token.

## Identity/Privacy
Credential identity is workload identity, not user identity.

## P1 Result Boundary
P1 access is explicit allowlist only.

## P2 Result Boundary
P2 protected dataset/hash/release objects remain inaccessible.

## Checkpoint/Lineage
Only approved checkpoint/lineage contract endpoints may be reached.

## Observability
Record allow/deny/error class and credential version with bounded cardinality.

## Rollback
Revoke credential, remove route and verify shadow execution reaches zero.

## DB/SQL Impact
No new persistent role/grant. If one is required, return `RCA2_ENTRY_BLOCKED_BY_SQL_ALLOCATION`; no SQL is created.

## Production Impact
None; production credential and route blocked.

## Verification
Actual credential and route tests are `NOT_EXECUTED` in SC-5.

## Risks
Non-production identity provider and endpoint inventory are not yet implemented.

## Exit Criteria
Least privilege, expiry, rotation, audit, TLS, allowlist and revocation verified.

## Handoff
Implement credentials/network only in the RCA-2 implementation PR after Operations and Privacy/Security approval.