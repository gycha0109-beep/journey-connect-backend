# Risk Register

| Field | Value |
|---|---|
| Official phase | `OP-1 RCA-2 Stage 1 Environment and Access Preparation` |
| Work-start / authoritative main | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| OP-0 exact final head | `e29a056d63c8c953851e4261bde9f71f3cd19441` |
| OP-0 merge commit | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| Artifact version | `op1-rca2-stage1-environment-access-v1` |
| Updated at | `2026-07-27T11:00:00Z` |

## Residual risks

- a later endpoint could resolve to a production route despite a safe alias; Operations must attest DNS/network policy
- a workload provider could issue excessive scope or TTL; live evidence is required
- allowlist lifecycle and deletion audit are not externally implemented
- cohort salt governance is unresolved
- actual candidate schema/side effects cannot be assessed before source selection
- OP-2 metrics, dashboards, alerts and drills are incomplete

All risks retain traffic at zero.
