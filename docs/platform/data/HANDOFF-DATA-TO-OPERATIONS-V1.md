# Handoff: Data to Operations V1

| Capability | Current | Required | Data dependency | Security | Observability | Rollback | Acceptance |
|---|---|---|---|---|---|---|---|
| ingestion worker | absent | bounded worker | event/idempotency | writer role | success/lag/conflict | disable/resume | crash/load tests |
| retry/quarantine | SQL only | worker/reviewer | retry evidence | role split | queue/age/rate | stop claims | lease/quarantine tests |
| projection/quality/integration workers | absent | isolated jobs | checkpoint/verdict | functions only | freshness/failure | stop/preserve | deterministic rerun |
| scheduler | disabled | audited orchestration | jobs | change audit | run status | disable | overlap/recovery |
| configuration | disabled defaults | environment config | versions | secrets | config/build refs | previous config | validation/approval |
| secrets/DB access | not operationalized | rotation/purpose access | roles | least privilege | access audit | revoke/rotate | rotation drill |
| deployment/environment | absent | isolated env | artifacts | network | deploy health | artifact rollback | runbook |
| dashboard/alerts | safe views | delivery/dashboard | safe metrics | no raw IDs | freshness/lag/error | HOLD | alert tests |
| incident/runbook | absent | owner/escalation/recovery | failure taxonomy | audit | timeline | rollback steps | tabletop |
| retention/purge | disabled | approved lifecycle executor | retention metadata | privacy/hold | deleted/held counts | policy-specific | dry-run/audit |
| kill/sampling/cohort | disabled/0/empty | approval-only operation | activation gates | approver split | exposure/cohort | kill | zero unauthorized traffic |

Operations must preserve idempotency, append-only, checkpoint, retry/quarantine, deterministic projection, verdict semantics, role boundaries, retention metadata and conflict handling.
