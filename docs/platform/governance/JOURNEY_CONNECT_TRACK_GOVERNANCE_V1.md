# Journey Connect Track Governance V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 개정 | `V1.3 / DATA PLATFORM TECHNICAL CLOSURE` |
| 상태 | `ACTIVE` |
| authoritative main | `c528f6fb0942389b70a348cb9aa672eb7819a392` |
| canonical DB | `journey-connect-db-v2.7/01..52` |
| historical baseline | `journey-connect-db-v2.7/01..28` |
| Data module/package | `jc-data-contracts` / `com.jc.data.contract.v1` |

## 책임

- Data: canonical event, idempotency, retry/quarantine evidence, checkpoint/projection/snapshot/lineage, quality/integration evidence, retention/privacy metadata.
- Recommendation: current P1 source/decision and protected Recommendation artifacts.
- Intelligence: model/runtime/result, feature semantics, confidence and activation.
- Search: document identity/mapping/index/freshness/reindex/runtime/cutover.
- Operations: workers, scheduler, deployment, secrets, DB access, monitoring, incident, retention/purge execution, kill switch/sampling/cohort operation.
- Reliability: SLI/SLO/error budget, release/rollback, replay/backfill approval, DR and promotion evidence.
- SC: registry, SQL sequence, authority/breaking-change and final conflict/go-no-go.

## Authoritative execution sequence

```text
IP 기술 기준선 종결
→ DP
→ OP
→ RP
→ 교차 트랙 통합 검증
```

과거 `DP-1/IP-1 병렬 진행`은 DB 비변경 범위의 **historical recommendation**이며 production dependency를 덮어쓰지 않는다. Data closure 후 작업은 DP-8이 아니라 별도 track task다.

## Closed Data boundary

DP-0~DP-7 technical roadmap은 complete다. SQL `01..52`는 immutable이고 `53+`는 unallocated다. Recommendation compatibility는 conditional, Intelligence/Search는 inconclusive다. Worker/scheduler/replay/backfill/rebuild/purge와 production activation은 closure 밖이다.

## Change proposal gate

Common ID/version/canonicalization/fingerprint, identity/privacy/retention, DB sequence/role/grant, cross-track authority/read/write, quality/integration semantics, source/consumer cutover, production control은 SC proposal이 필요하다.

## Integration refusal

Direct cross-track write, unversioned contract, authority/metric drift, unauthorized identity/cutover, unverified SQL, runtime을 문서 상태로 위장, unexecuted PASS, final head/workflow SHA mismatch를 거부한다.
