# Data Platform Production Readiness Gaps V1

`TECHNICAL BASELINE COMPLETE / PRODUCTION NOT READY`

| gap_id | domain | description | current_state | required_state | severity | blocking | owner | prerequisite | acceptance_criteria |
|---|---|---|---|---|---|---|---|---|---|
| `DPG-EXEC-01` | execution | ingestion worker | absent | idempotent deployable worker | BLOCKER | yes | Operations | event contract | role/lease/error/load/runbook |
| `DPG-EXEC-02` | execution | retry/quarantine worker | SQL capability | controlled worker/review | HIGH | yes | Operations | retry policy | claim/heartbeat/quarantine tests |
| `DPG-EXEC-03` | execution | projection/quality/integration workers | absent | isolated jobs | BLOCKER | yes | Operations | schedules | deterministic execution/failure isolation |
| `DPG-EXEC-04` | execution | scheduler/orchestration/lock | disabled | audited orchestration | HIGH | yes | Operations | worker design | overlap/recovery/rollback |
| `DPG-REC-01` | recovery | replay | unauthorized/no execute | bounded approved replay | BLOCKER | yes | Reliability/Ops | privacy/identity | scope/dry-run/audit/rollback |
| `DPG-REC-02` | recovery | backfill | unauthorized | bounded versioned backfill | BLOCKER | yes | Reliability/Ops | source authority | dedupe/resource/audit |
| `DPG-REC-03` | recovery | checkpoint/partial failure | contract only | tested recovery | HIGH | yes | Operations | workers | crash/restart tests |
| `DPG-REC-04` | recovery | rebuild/invalidation | comparison only | approved rebuild/supersession | HIGH | yes | Reliability/Ops | quality policy | mismatch/no-rewrite handling |
| `DPG-LIFE-01` | lifecycle | retention/purge | metadata only | approved executor | HIGH | yes | Operations/Privacy | legal policy | dry-run/hold/audit/tombstone |
| `DPG-LIFE-02` | lifecycle | deletion/legal hold/archive | undefined | explicit policy | BLOCKER | yes | Privacy/Ops/SC | identity owner | end-to-end evidence |
| `DPG-OBS-01` | observability | metrics/logs/traces/dashboard | safe views only | telemetry/dashboard | BLOCKER | yes | Operations | SLI | freshness/lag/error/alerts |
| `DPG-OBS-02` | observability | thresholds | TO_BE_DECIDED | approved thresholds | HIGH | yes | Reliability | sample | version/owner/escalation |
| `DPG-OBS-03` | observability | anomaly delivery | absent | routed notification | HIGH | yes | Operations | alert channel | dedupe/ack/escalation |
| `DPG-SEC-01` | security | secrets/runtime roles | not operationalized | managed rotation | BLOCKER | yes | Ops/Security | environments | least privilege/rotation/audit |
| `DPG-SEC-02` | security | network/environment separation | not evidenced | isolation | HIGH | yes | Ops/Security | deployment | access test |
| `DPG-SEC-03` | security | privileged monitoring/break-glass | DB hardening only | monitored emergency access | HIGH | yes | Ops/Security | audit | reason/expiry/review |
| `DPG-REL-01` | release | shadow authorization | disabled | explicit approval | BLOCKER | yes | Reliability/Ops | gates2-6 | scope/window/cohort/rollback |
| `DPG-REL-02` | release | parity/drift | not executed | accepted comparison | BLOCKER | yes | Target/Reliability | shadow | reproducible report |
| `DPG-REL-03` | release | rollout/rollback threshold | TO_BE_DECIDED | versioned plan | BLOCKER | yes | Reliability/Ops | SLO | stages/kill/threshold |
| `DPG-REL-04` | release | promotion evidence | absent | exact evidence pack | HIGH | yes | Reliability | rollout | build/config/data/approval |

No numerical SLO or release threshold is fixed here.
