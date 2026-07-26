# Credential Implementation Requirements

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


```text
TYPE=SHORT_LIVED_NONPRODUCTION_WORKLOAD_IDENTITY
MAX_TTL_SECONDS=3600
STORAGE=PLATFORM_SECRET_MANAGER
ROTATION=REQUIRED
REVOCATION=REQUIRED
WRITE_SCOPE=FORBIDDEN
PRODUCTION_SCOPE=FORBIDDEN
```

Evidence must include issuer/audience/scope policy, TTL proof, secret-manager reference without secret material, rotation test, Level 6 revoke test and post-revocation authentication failure. Credentials, tokens and secret values must never appear in Git, CI artifacts, logs or screenshots.
