# Evidence Retention Plan

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


| Evidence | Minimum retention | Location | Restrictions |
|---|---:|---|---|
| OP-0 contracts and decisions | repository history | Git | no secrets/raw identity |
| OP-0 CI verifier artifact | 90 days | GitHub Actions artifact | exact tested SHA and JSON result |
| OP-1 endpoint/access evidence | 180 days | approved operations evidence store | immutable digest references only |
| OP-2 dashboard/alert/drill evidence | 180 days | approved operations evidence store | redacted, no tokens/raw IDs |
| OP-3 observation evidence | 365 days or governing policy, whichever is stricter | approved evidence store | pseudonymous/aggregated metrics |
| approval records | lifecycle + 365 days | auditable approval system | explicit actor/time/scope/expiry |

Credential values, raw IDs, unrestricted hashes, payloads and secrets are never retained in governance artifacts. Deletion/expiry must not rewrite historical aggregate decision evidence.
