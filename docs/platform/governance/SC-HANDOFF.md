# System Coordination Handoff

## 상태

`DP2_MAIN_INTEGRATED / DP3_ENTRY_DECISIONS_APPROVED`

## 기준

- official DP-1 Baseline SHA: `9d84f630e87d54f780e332eead0c1f8df6a51d0b`
- DP-1 merge commit: `bdce7de5ef6be31f8da6a8a349424be8f06a87a1`
- SC DP-2 decision merge commit: `c3f791c6c6eaa12b2aba3a1dbe686cb0b3d3cc80`
- DP-2 implementation HEAD: `f6c45a86ee21beb0d7a12e931c73ca887effdf18`
- DP-2 PR: `#8`
- DP-2 merge commit/current authority start: `0ff67aaf9a86b61be2b41c431a570a9f0d460f7c`
- DP-2 exact-head CI: Data PostgreSQL `29855410623`, Data Contract `29855410811`, Recommendation DB `29855410680`, Backend `29855410550`, SC `29855410537` — PASS
- DP-2 result: `DP2_IMPLEMENTATION_COMPLETE`

## 완료

- DP-1 and DP-2 are integrated into `main`
- Data contract, fingerprint, canonical event store, atomic idempotency and least-privilege Data roles are main authority
- PostgreSQL 15/18 concurrency and protected regression passed at the DP-2 exact HEAD
- SQL `01..31` are protected
- DP-3 retry/quarantine state, budget, role, lease and observability decisions are approved

## DP-3 approved decisions

### Scope

DP-3 implements retry scheduling, atomic work claiming, quarantine/review evidence and observability contracts. It does not activate production scheduling, execute replay, expose HTTP APIs, modify canonical source rows, map identities or cut over projections.

### SQL

- SQL `32`: retry schedule, processing-attempt and quarantine evidence
- SQL `33`: atomic claim/lease/complete/fail/quarantine procedures and grants
- SQL `34`: PostgreSQL 15/18 smoke, concurrency, lease and authority validation
- SQL `35+`: unallocated

### Retry policy

- policy ID: `data-projection-retry-v1`
- initial attempt: 1
- maximum automatic retries: 5
- maximum total executions: 6
- delays: `1m`, `5m`, `30m`, `2h`, `12h`
- deterministic scheduling jitter: `0..10%`
- retry exhaustion: terminal quarantine
- three consecutive identical normalized failure signatures may quarantine early
- unknown, validation, authorization, privacy, fingerprint, lineage and invariant failures fail closed without automatic retry

### Claim and roles

- processor: `jc_data_retry_processor`
- reviewer: `jc_data_quarantine_reviewer`
- replay executor: `jc_data_replay_executor`, with no execution grant in DP-3
- lease: 60 seconds
- heartbeat: 20 seconds
- default maximum batch: 100
- stale/foreign completion is rejected
- expired claims are reclaimed only through the approved procedure

### Observability

DP-3 must expose bounded metric/evidence contracts for scheduled, claimed, succeeded, failed, exhausted, quarantine, latency, queue depth, oldest age, lease expiry and stale-claim rejection. Raw identity, payload, idempotency key, token and unrestricted error text are prohibited.

Operations owns production alert routing and scheduler activation.

### Retention

Retry, quarantine and review evidence use 90-day technical retention metadata. Automatic purge and physical deletion remain disabled.

## DP-3 entry

```text
DP-2: MAIN INTEGRATED
DP-3 ENTRY: AUTHORIZED AFTER SC DP-3 DECISION PR MERGE
```

Exact rules: `SC-DP3-ENTRY-DECISIONS.md`.

## Remaining unresolved/outside DP-3

- identity mapping physical owner/deletion workflow
- replay execution procedure and grant
- production scheduler deployment and activation
- production alert routing
- legal/country-specific retention and erasure
- consumer projection cutover

## Protected state

```text
IP-12.5: HOLD_OPERATIONAL_INPUTS_PENDING
Production shadow: DISABLED
Kill switch: true
Effective sampling: 0 BPS
Actual cohort: empty / 0%
Search cutover: NOT STARTED
Production traffic: NOT APPROVED
Go/No-Go: NO_GO_FOR_TRAFFIC
```
