# IP-11 Runtime Input, Resource and Sampling Budget

## 상태

`SOURCE_EXTERNAL_ATTESTATION_PENDING / INITIAL_PILOT_BUDGET_APPROVED`

The versioned Search read projection and projection-only provider are implemented by IP-11.5. SQL 27/28 PostgreSQL replay and Operations eligibility authority remain external blockers.

| 항목 | 승인된 initial pilot 상한 | 현재 상태 |
|---|---:|---|
| core concurrency | 1 | capability present; activation disconnected |
| maximum concurrency | 2 | capability present; activation disconnected |
| queue capacity | 8 | capability present; activation disconnected |
| runtime timeout | 200ms | capability present |
| hard cancellation timeout | 300ms 이하 | current provisional implementation 250ms; 승인 상한 이내 |
| maximum candidate count | 100 | capability present |
| effective sample | 0 BPS | 강제 유지 |
| initial pilot ceiling | 10 BPS | 승인된 상한; 활성화 값 아님 |

`50 BPS`, `100 BPS`는 향후 제안값이며 각각 별도 승인과 관찰 증거가 필요하다. 현재 production controls의 provisional technical policy가 100 BPS를 표현할 수 있으므로 IP-12에서는 승인 ceiling 10 BPS와의 `min()`/validation 결속을 외부 attestation 전에 검증해야 한다.

## Sampling

- effective production sampling: `0 BPS`
- initial pilot ceiling: `10 BPS`
- 50/100 BPS: separate approvals only
- actual cohort: empty/0%

Production activation remains prohibited.
