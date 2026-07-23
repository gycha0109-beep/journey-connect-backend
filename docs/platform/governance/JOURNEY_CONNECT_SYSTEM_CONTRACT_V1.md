# Journey Connect System Contract V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `jc-system-contract-v1` |
| 개정 | `V1.3 / DATA PLATFORM TECHNICAL CLOSURE` |
| 상태 | `ACTIVE / DP-0~DP-7 TECHNICAL BASELINE CLOSED` |
| authoritative repository | `gycha0109-beep/journey-connect-backend` |
| authoritative main | `c528f6fb0942389b70a348cb9aa672eb7819a392` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| historical DP-1 baseline | `journey-connect-db-v2.7/01..28` |
| Data module/package | `jc-data-contracts` / `com.jc.data.contract.v1` |
| 기준일 | `2026-07-24` |

이 문서는 Data, Recommendation, Intelligence, Search, Operations, Reliability, System Coordination의 source authority, version, DB sequence와 production activation 경계를 고정한다.

## 2. Authoritative technical state

```text
DP-0~DP-7 technical roadmap: COMPLETE
SQL 01..52: IMPLEMENTED / IMMUTABLE BASELINE
SQL 53+: UNALLOCATED
Production shadow: DISABLED
Kill switch: ENABLED
Sampling: 0 BPS
Cohort: EMPTY
Search cutover: NOT STARTED
Production traffic: NOT APPROVED
```

PR #20 head `affb561eeeb7b1eb9cabb44e5d29b9378194934d`는 PostgreSQL 15/18, Data, Recommendation, Intelligence, Search, Backend, DP6, DP7, SC exact-head CI를 통과했고 merge commit `c528f6fb0942389b70a348cb9aa672eb7819a392`으로 main에 반영됐다. Merge commit과 검증 head의 file tree에는 차이가 없다. Main push workflow는 존재하지 않으므로 `MAIN_CI_PASS`로 표현하지 않는다.

## 3. Track authority

| 영역 | semantic owner | Data 권한 |
|---|---|---|
| canonical event, idempotency, retry/quarantine evidence | Data | approved function boundary를 통한 write/read |
| checkpoint, projection, snapshot, lineage | Data | own |
| quality validation run/verdict | Data | quality evidence; release authority 없음 |
| cross-track integration run/verdict | Data | compatibility evidence; activation authority 없음 |
| Recommendation decision/profile/runtime/exposure | Recommendation/Intelligence | approved read-only comparison |
| P2 assignment/exposure/evaluation/metric/release | Reliability semantic authority; protected Recommendation physical path | approved read only |
| Intelligence model/runtime/result | Intelligence | direct write/activation 금지 |
| Search document/index/runtime/cutover | Search | direct write/index/cutover 금지 |
| production execution/scheduler/deployment/monitoring | Operations | Data contract consumer |
| release/SLO/replay/backfill/rollback | Reliability | Data evidence consumer |
| registry/DB sequence/breaking change | System Coordination | approval required |

타 트랙 table direct `INSERT/UPDATE/DELETE`는 금지한다.

## 4. Identity, time, version

- `subject:<opaque-id>`와 `user:<numeric-id>`는 자동 변환하지 않는다.
- Java/Kotlin은 `Instant`, DB는 `TIMESTAMPTZ`, JSON은 UTC `Z`를 사용한다.
- persisted version에 `latest`, `current`, `default`를 사용하지 않는다.
- identity mapping은 SC + privacy review와 단일 write owner 승인 전 구현하지 않는다.

## 5. Data contracts

`platform-event-v1`, `user-behavior-event-v1`, `platform-event-canonical-json-v1`, `platform-event-fingerprint-sha256-v1`, `event-idempotency-fingerprint-v1`, `event-retry-quarantine-replay-v1`, `recommendation-profile-input-v1`, `experiment-outcome-input-v1`, `data-projection-snapshot-v1`, `data-quality-policy-v1`, `data-cross-track-integration-policy-v1`.

Profile/outcome projection은 shadow-only다. Unknown required field/enum/schema는 fail closed한다.

## 6. Source authority protection

- P0/P1 behavior: `recommendation_behavior_event`
- general Recommendation exposure: Recommendation exposure family
- P2 experiment exposure: `recommendation_p2_experiment_exposure`
- P2 dataset: `recommendation-evaluation-dataset-v1`
- Data `VALIDATED`: quality만 의미하며 release/cutover 승인이 아님
- Data integration verdict: compatibility evidence이며 production activation 승인이 아님

## 7. DB/change contract

Historical baseline `journey-connect-db-v2.7/01..28`은 역사 증거로 보존한다. 현재 SQL `01..52`는 immutable baseline이며 SQL `53+`는 SC 배정 전 사용할 수 없다. Historical migration rewrite는 prohibited다. Object behavior, policy, fingerprint/canonical bytes 의미 변경은 새 migration/domain/version을 요구한다.

## 8. Production activation boundary

Technical closure는 production readiness 또는 approval이 아니다. Target contract, Operations runtime, observability/security, Reliability gate, shadow authorization, parity/drift, cohort, staged rollout, adoption, cutover는 별도 gate다.

## 9. 절대 금지

SQL `01..52` rewrite, 무배정 SQL, cross-track authority 획득, production shadow/routing 활성화, replay/backfill/rebuild/purge 실행, Data verdict를 release approval로 사용, `/api/v1/explore` 무승인 변경.
