# SC RCA-1B Execution Environment Decision

## Scope

Select the only environment authorized for RCA-1B implementation. No environment is provisioned by this document.

## Current Baseline

Work-start `b2e7a5c316c6f6ee543ccedf35bca65353ab3aa4`; RCA-1 exact-final-head `38896b2a37180633870282e9d9e305d9c9fbbf8a`; production activation remains not authorized.

## Decision

- Environment A — CI ephemeral PostgreSQL: `APPROVED`.
- Environment B — isolated shared non-production PostgreSQL: `DEFERRED`.
- Environment C — production replica or production-derived environment: `BLOCKED`.

```text
RCA1B_EXECUTION_ENVIRONMENT=CI_EPHEMERAL_POSTGRESQL
PRODUCTION_NETWORK_ACCESS=FORBIDDEN
DATABASE_LIFETIME=ONE_CI_JOB
```

## Rationale

The existing Testcontainers matrix provides reproducible isolation and automatic teardown. Persistent shared databases introduce credential, contamination, retention and checkpoint-drift risks not required for the first DB reconciliation phase.

## Authority

Operations is accountable for isolation and credentials; SC approves environment class; Intelligence/Reliability approve lane semantics; Privacy/Security approves data boundaries.

## Dependencies

Docker-capable CI, canonical SQL `01..52`, deterministic synthetic seed and exact-head checkout.

## Execution Environment

One PostgreSQL container and database per version/job. No VPN, VPC peering, production DNS, external host allowlist or persistent volume. Timezone UTC and deterministic locale/collation settings are mandatory.

## DB Access Boundary

Bootstrap owner is limited to schema replay, test seed and ephemeral role setup. Reconciliation uses only the ephemeral read-only login.

## Query Boundary

Only committed allowlisted query IDs may run after bootstrap.

## Identity/Privacy

Synthetic identities only; no production-derived snapshot.

## Evidence

Record environment class and PostgreSQL version, never host/IP, connection string or credential.

## DB/SQL Impact

No canonical DB or SQL allocation. Test containers and their data are destroyed after execution.

## Production Impact

None.

## Verification

SC-4 verifies the decision and existing version-matrix capability. Actual environment isolation is `NOT_EXECUTED` until implementation.

## Risks

CI runner compromise and accidental network reachability must be addressed by implementation assertions and Operations review.

## Exit Criteria

Both PostgreSQL versions execute in isolated jobs with no production route and deterministic teardown.

## Handoff

Implement environment assertions in a separate RCA-1B PR; Environment B or C requires another SC decision.