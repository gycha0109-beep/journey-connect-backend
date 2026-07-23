# Handoff: Data to Reliability V1

| Capability | Current | Required |
|---|---|---|
| SLI/SLO/error budget | `TO_BE_DECIDED` | versioned definitions/owner/window/source |
| release gate | not approved | technical/contract/runtime/obs/security/shadow evidence |
| freshness/lag thresholds | `TO_BE_DECIDED` | target-specific values |
| quality/duplicate/conflict thresholds | `TO_BE_DECIDED` | segment/window/response |
| rollback threshold | `TO_BE_DECIDED` | authority and kill-switch rule |
| replay/backfill | unauthorized | scope/dry-run/resource/audit/rollback |
| incident/postmortem | undefined | severity/owner/timeline/evidence |
| disaster recovery | absent | RPO/RTO `TO_BE_DECIDED`, restore drill |
| promotion evidence | absent | build/config/data/cohort/metric/approval refs |

Candidate SLIs: ingestion success/lag, projection freshness, snapshot completion, quality VALIDATED/REJECTED, integration COMPATIBLE/INCONCLUSIVE, duplicate/conflict/quarantine, rebuild determinism. Data closure sets no numerical target.
