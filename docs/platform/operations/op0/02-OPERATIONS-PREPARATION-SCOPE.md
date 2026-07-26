# Operations Preparation Scope

| Field | Value |
|---|---|
| Official phase | `OP-0 RCA-2 Stage 1 Operations Preparation Baseline` |
| Work-start / authoritative main | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| RCA-2 exact final head | `511b19f80cdd42bb2fafde0563c7388b4f5b5f48` |
| RCA-2 merge commit | `b57c344c9b4e332966fe9f6d36a5da66a5faae71` |
| SC-6 exact final head | `20da93e932c50b5bebd549a56db40edb00ca1eea` |
| SC-6 merge commit | `40ff229e2401e7d5d9c5323d469bcd012530e882` |
| Artifact version | `op0-rca2-stage1-operations-preparation-v1` |
| Updated at | `2026-07-26T14:15:55Z` |


## In scope

Workstream definitions, owners, dependencies, sequencing, acceptance criteria, evidence requirements, rollback responsibility, readiness gates, blocker/risk registers and OP-1/OP-2 handoff contracts.

## Out of scope

- infrastructure creation or deployment
- endpoint, credential, secret, route or identity creation
- actual stable-hash selection implementation
- actual candidate adapter connection
- dashboard deployment or alert route connection
- rollback drill execution
- role approval or manual 1% enablement
- runtime observation
- Java/Kotlin production changes, traffic config, DB or SQL

## Decision rule

Planning completion does not imply implementation readiness. Every unresolved implementation dependency is owned and recorded. Any false Stage 1 gate condition forces `TRAFFIC_ENABLEMENT=BLOCKED` and `CURRENT_TRAFFIC_PERCENT=0`.
