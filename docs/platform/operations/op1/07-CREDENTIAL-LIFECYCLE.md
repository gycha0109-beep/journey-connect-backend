# Credential Lifecycle

| Field | Value |
|---|---|
| Official phase | `OP-1 RCA-2 Stage 1 Environment and Access Preparation` |
| Work-start / authoritative main | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| OP-0 exact final head | `e29a056d63c8c953851e4261bde9f71f3cd19441` |
| OP-0 merge commit | `0d9cdc7bf9821d5bb8811bfdd08286f3e4804e8d` |
| Artifact version | `op1-rca2-stage1-environment-access-v1` |
| Updated at | `2026-07-27T11:00:00Z` |

## Lifecycle

```text
MISSING -> REFRESH -> READY -> STALE -> REFRESH
READY|STALE -> REVOKED
READY -> EXPIRED
```

Refresh begins inside a 60-second margin. Missing, expired, revoked, over-TTL, production-audience, production-scope and write-scope leases fail closed. Actual issuance and rotation evidence remains external.
