# Test Plan and Results

| Field | Value |
|---|---|
| Official phase | `OP-1 RCA-2 Stage 1 Environment and Access Preparation` |
| Work-start / authoritative main | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| OP-0 exact final head | `e29a056d63c8c953851e4261bde9f71f3cd19441` |
| OP-0 merge commit | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| Artifact version | `op1-rca2-stage1-environment-access-v1` |
| Updated at | `2026-07-27T11:00:00Z` |

## Test inventory

- endpoint and production-route policy
- credential missing/expired/revoked/TTL/audience/scope
- allowlist allow/deny/expiry/revoke/duplicate/environment/purpose
- stable hash determinism, zero, one-percent ceiling, invalid input and distribution
- candidate unresolved/read-only/no-serving
- effective-zero integration and stub candidate isolation
- production endpoint, expired credential, empty allowlist and flag-off failure isolation
- existing RCA-2 orchestrator/static/side-effect regression

Exact-head results are written by the OP-1 workflow and verifier; no external endpoint is contacted.
