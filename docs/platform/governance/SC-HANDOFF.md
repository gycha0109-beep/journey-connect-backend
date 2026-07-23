# System Coordination Handoff

## Status

`DATA PLATFORM TECHNICAL CLOSURE CANDIDATE / PRODUCTION NOT AUTHORIZED`

## Authoritative baseline

- main: `c528f6fb0942389b70a348cb9aa672eb7819a392`
- DP-7 PR #20 head: `affb561eeeb7b1eb9cabb44e5d29b9378194934d`
- PR #20: merged
- SQL `01..52`: implemented and immutable
- SQL `53+`: unallocated
- DP-0~DP-7: technical roadmap complete

## Protected state

```text
Production shadow: DISABLED
Kill switch: true
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
/api/v1/explore authority: LEGACY
Production traffic: NOT APPROVED
Recommendation production write: DISABLED
Intelligence runtime activation: DISABLED
Worker/scheduler: NOT IMPLEMENTED / DISABLED
Replay/backfill/rebuild/purge execution: UNAUTHORIZED
```

## Cross-track findings

- Recommendation profile/outcome: `CONDITIONALLY_COMPATIBLE`; P1/P2 authority retained.
- Intelligence: `INCONCLUSIVE`; Data-specific mapping absent.
- Search: `INCONCLUSIVE`; Data-to-Search contract absent and subject/exposure is not a Search document.

## Handoff

| Work | Owner | Entry condition |
|---|---|---|
| Recommendation shadow/parity/adoption | Recommendation | contract + rollback |
| Data-specific Intelligence contract | Intelligence | domain/feature/identity/privacy decision |
| Data-to-Search contract/index/cutover | Search | document/freshness/deletion/reindex decision |
| workers/scheduler/deploy/monitoring/purge | Operations | runtime/security/runbook |
| SLI/SLO/release/replay/backfill/rollback | Reliability | thresholds/evidence approval |
| SQL53+/authority/identity changes | SC | explicit decision |

## Verification truth

Main merge commit has no push workflow runs and is not labeled `MAIN_CI_PASS`. DP-7 verified head and merge commit have identical trees. Closure PR must start from main and rerun PostgreSQL 15/18, Data, Recommendation, Intelligence, Search, Backend, DP6, DP7, SC and closure gates on one exact head. Any SQL, production source/config or authority mutation fails protected diff.

## Gate

`DATA_PLATFORM_TECHNICAL_CLOSURE_CANDIDATE`

User approval is required before closure PR merge. Production activation remains separate.
