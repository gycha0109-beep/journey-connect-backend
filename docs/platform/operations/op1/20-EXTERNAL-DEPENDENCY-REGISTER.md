# External Dependency Register

| Field | Value |
|---|---|
| Official phase | `OP-1 RCA-2 Stage 1 Environment and Access Preparation` |
| Work-start / authoritative main | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| OP-0 exact final head | `e29a056d63c8c953851e4261bde9f71f3cd19441` |
| OP-0 merge commit | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| Artifact version | `op1-rca2-stage1-environment-access-v1` |
| Updated at | `2026-07-27T11:00:00Z` |

## Blocking external dependencies

- infrastructure repository and deployment owner path
- isolated non-production namespace and DNS/service endpoint
- outbound network allowlist
- approved secret manager/workload identity provider
- persistent hashed allowlist store and audit path
- candidate source, protocol and API version

Each is `BLOCKED_EXTERNAL_DEPENDENCY`; no localhost, fake endpoint or static secret is represented as ready.
