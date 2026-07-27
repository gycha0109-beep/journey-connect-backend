# SC OP-3 External Dependency Work Orders

## Authoritative baseline

- merged SC package: `fcd930550eb0f8b4c529ac53fb8f2aa9bce767a9`
- programme issue: `#36`
- work orders: `#37` through `#43`

## Current control state

- `OP3_ENTRY=BLOCKED`
- `STAGE1_ENABLEMENT=BLOCKED`
- `EFFECTIVE_NONPRODUCTION_TRAFFIC_PERCENT=0`
- `PRODUCTION_TRAFFIC_PERCENT=0`
- `PRIMARY_RESULT_AUTHORITY=CURRENT_P1_P2_ONLY`
- `CANDIDATE_SERVING=FORBIDDEN`

## Work-order allocation

| Issue | Accountable role | Scope | Current state |
|---|---|---|---|
| #37 | Observability Owner | metrics backend, dashboard, retention policy | BLOCKED |
| #38 | Incident Response Owner | alert delivery, acknowledgement, incident command | BLOCKED |
| #39 | Security/Data Owner | bounded access material, revocation drill, test allowlist | BLOCKED |
| #40 | Platform Owner | non-production route, deny proof, route withdrawal drill | BLOCKED |
| #41 | Recommendation Owner | source, protocol, version, adapter integration | BLOCKED |
| #42 | Release Owner | deployment rollback drill | NOT_EXECUTED |
| #43 | Operations Owner | manual operator, independent approver, evidence location | UNASSIGNED |

## Completion rule

A role assignment is not completion. Each issue must close with runtime or environment evidence accepted under `61-SC-OP3-EVIDENCE-ACCEPTANCE-CONTRACT.md`.

All seven work orders must be complete before SC may reassess OP-3 entry. Work-order completion does not itself grant execution authority.

## Prohibited shortcuts

The following do not satisfy a work order by themselves:

- application configuration text
- mock-only or localhost-only tests
- screenshots without immutable resource identity
- issue closure without attached evidence
- generated placeholders presented as deployed resources
- repository files containing protected values

While any work order remains open, traffic and feature enablement remain forbidden.
