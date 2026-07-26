# Endpoint Implementation Requirements

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


OP-1 completion candidate requirements:

```text
ENVIRONMENT=ISOLATED_NON_PRODUCTION_RUNTIME
PRODUCTION_DNS=FORBIDDEN
PRODUCTION_NAMESPACE=FORBIDDEN
PRODUCTION_DATABASE_ROUTE=FORBIDDEN
TLS=REQUIRED
AUTHENTICATION=REQUIRED
DENY_BY_DEFAULT=REQUIRED
OWNER=OPERATIONS
```

## Verification procedure

1. Record infrastructure repository, path, environment ID and immutable config digest.
2. Prove DNS/namespace/database routes are non-production and deny by default.
3. Prove TLS and workload authentication with positive and negative tests.
4. Prove production DNS, namespace and database destinations are unreachable.
5. Bind evidence to exact implementation head and endpoint config digest.

OP-0 creates no endpoint. The external implementation repository/path is an OP-1 blocker.
