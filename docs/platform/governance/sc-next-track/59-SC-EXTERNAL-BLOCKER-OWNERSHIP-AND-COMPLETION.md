# SC External Blocker Ownership and Completion Criteria

Named human owners are not yet supplied. SC therefore assigns accountable role owners without inventing individuals.

| Blocker | Accountable role | Completion evidence |
|---|---|---|
| Metrics backend | Observability Owner | Endpoint/query evidence, retention and cardinality policy, exact environment binding |
| Dashboard deployment | Observability Owner | Deployed dashboard URL or immutable identifier, panel query verification, access evidence |
| Critical/warning alert route | Incident Response Owner | Synthetic alert delivery, acknowledgement record, escalation path |
| Workload credential | Security Owner | Issuance metadata, scope/audience/TTL, secret reference, no raw secret in repository |
| Credential revoke drill | Security Owner | Revocation timestamp, failed post-revoke access, recovery procedure |
| Non-production network route | Platform Owner | Approved route inventory, production deny proof, egress controls |
| Network revoke drill | Platform Owner | Route withdrawal evidence and failed post-revoke connection |
| Test identity allowlist | Security/Data Owner | Default-deny store, bounded expiry, revocation and audit evidence |
| Candidate source/protocol/version | Recommendation Owner | Signed source decision, protocol/schema version, compatibility result |
| Candidate adapter | Recommendation Owner | Read-only/non-serving integration test and failure fallback evidence |
| Deployment rollback drill | Release Owner | Previous artifact restore or redeploy evidence, health verification, elapsed time |
| Manual enablement operator | Operations Owner | Named operator, access scope, two-person confirmation procedure |
| Incident command | Incident Response Owner | Named IC/on-call route and stop authority |

## Ownership rule

Role assignment is not completion. Each row remains blocked until its evidence is attached to the OP-3 package and verified against the exact candidate head.