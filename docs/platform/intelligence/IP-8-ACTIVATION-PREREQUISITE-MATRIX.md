# IP-8 Activation Prerequisite Matrix

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `search-shadow-activation-prerequisite-matrix-v1` |
| 상태 | `ACTIVE / PRODUCTION_ACTIVATION_BLOCKED` |
| production activation | `HOLD_FOR_OWNER_DECISIONS` |

## 1. 판정 규칙

- `READY_FOR_CONTROLLED_HOOK_PROPOSAL`: proposal·disabled equivalence·regression task 준비 완료
- `HOLD_FOR_OWNER_DECISIONS`: 실제 activation에 필요한 owner/value 미확정
- `required_before_cutover`: shadow와 별개로 Search API cutover 전 필수

## 2. Matrix

| Prerequisite | 상태 | 필요 시점 | Owner | 근거/비고 |
|---|---|---|---|---|
| backend explore inventory | resolved | proposal | Intelligence | 실제 Controller/Service/Repository 확인 |
| controlled hook proposal | resolved | proposal | Intelligence | IP-8 proposal 문서 |
| disabled-mode equivalence | resolved | proposal | Intelligence | direct executable contract |
| production hook not inserted | resolved | proposal | Intelligence | protected source exact |
| unified regression task | resolved | proposal | Intelligence | `ip8SearchRegressionClosure` |
| actual retrieval/index strategy | unresolved | before activation | UNASSIGNED | legacy output 재투입 금지 |
| runtime input provider | unresolved | before activation | UNASSIGNED | production provider 없음 |
| Operations visibility owner | unresolved | before activation | UNASSIGNED | Operations port 미구현 |
| eligibility authority | unresolved | before activation | UNASSIGNED | 임의 eligibility 금지 |
| SearchRun writer | unresolved | before activation | UNASSIGNED | persistence 없음 |
| snapshot writer | unresolved | before activation | UNASSIGNED | ephemeral only |
| shadow evidence writer | unresolved | before activation | UNASSIGNED | current logging non-persistent |
| `search_exposure_v1` writer | unresolved | before activation | UNASSIGNED | exposure 미활성 |
| query retention owner | unresolved | before activation | UNASSIGNED | raw query 저장 금지 |
| evidence retention owner | unresolved | before activation | UNASSIGNED | retention 기간 미정 |
| access/deletion policy | unresolved | before activation | UNASSIGNED | privacy approval 필요 |
| executor concurrency budget | unresolved | before activation | UNASSIGNED | 숫자 확정 금지 |
| queue capacity budget | unresolved | before activation | UNASSIGNED | bounded requirement만 확정 |
| task timeout budget | unresolved | before activation | UNASSIGNED | test value와 production 분리 |
| latency budget | unresolved | before activation | UNASSIGNED | API SLO와 별개 |
| error budget | unresolved | before activation | UNASSIGNED | P2 metric과 별개 |
| circuit threshold | unresolved | before activation | UNASSIGNED | deterministic fixture only |
| kill-switch authority | unresolved | before activation | UNASSIGNED | audit/propagation 필요 |
| activation authority | unresolved | before activation | UNASSIGNED | production enable 금지 |
| rollback authority | unresolved | before activation | UNASSIGNED | owner 필요 |
| on-call owner | unresolved | before activation | UNASSIGNED | incident path 필요 |
| incident response path | unresolved | before activation | UNASSIGNED | escalation 미정 |
| Gradle Search closure | pending_external | before IP-9 | External verifier | current environment result 별도 로그 |
| backend Spring/Testcontainers | pending_external | before IP-9 | External verifier | Docker/PostgreSQL 필요 |
| production cursor key owner | unresolved | before cutover | UNASSIGNED | shadow disabled regression에는 불필요 |
| key rotation policy | unresolved | before cutover | UNASSIGNED | shadow disabled regression에는 불필요 |

## 3. Decision

```text
Controlled Hook Proposal: READY_FOR_CONTROLLED_HOOK_PROPOSAL
Production Activation: HOLD_FOR_OWNER_DECISIONS
IP-9 Entry: HOLD pending owner decisions + external Gradle/backend PASS
```

## 4. Authority

이 matrix는 release gate가 아니다. P2 metric, Search exposure metric, API SLO 또는 운영 승인으로 사용하지 않는다.
