# OP-1 Handoff Prompt

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


## Task

Implement `OP-1 RCA-2 Environment and Access Preparation` in a separate Draft PR starting from the then-current authoritative `main`.

## Required scope

- resolve and record external operations/security implementation repositories and paths
- prepare isolated non-production endpoint and network boundary
- issue/validate short-lived read-only non-production workload credential
- implement test-account-only allowlist with expiry and hashed audit
- implement deterministic stable-hash cohort with hard 1% ceiling, default OFF and fail-closed behavior
- resolve and prepare the actual candidate source/protocol/adapter with no-serving and no-side-effect boundaries
- verify environment, route and selection controls while effective traffic remains 0

## Mandatory exclusions

No 1% enablement, runtime observation, production route/identity/credential/config, serving, authority transfer, DB or SQL. Do not modify historical RCA/SC evidence.

## Entry check

Do not start as an approved OP-1 phase until `op1-entry-gate.json` is all true, including explicit OP-0 user approval and resolved implementation paths/source. Produce exact-head evidence and an OP-2 handoff; keep Draft/Open until user approval.
