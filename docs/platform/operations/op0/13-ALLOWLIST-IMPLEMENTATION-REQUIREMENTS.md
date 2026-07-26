# Allowlist Implementation Requirements

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
IDENTITY_MODE=SYNTHETIC_OR_TEST_ACCOUNT_ONLY
MAX_ENTRY_DURATION_DAYS=30
RAW_ID_LOGGING=FORBIDDEN
HASHED_AUDIT=REQUIRED
PURPOSE_BINDING=REQUIRED
ENVIRONMENT_BINDING=REQUIRED
```

The allowlist must fail closed, expire entries automatically, support immediate emptying and reject production identities. Audit evidence may contain only restricted hashes and bounded reason codes. OP-0 registers no identity.
