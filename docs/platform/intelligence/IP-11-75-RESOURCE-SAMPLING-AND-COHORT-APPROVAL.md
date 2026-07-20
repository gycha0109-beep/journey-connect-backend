# IP-11.75 Resource, Sampling and Cohort Approval

## Initial pilot budget

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

## Sampling contract

- 현재 effective production sampling: `0 BPS`
- initial pilot ceiling: `10 BPS`
- `50 BPS`, `100 BPS`: 별도 승인·관찰 후에만 고려
- kill-switch killed, cohort empty, approval absent 또는 malformed configuration이면 effective 0 BPS
- 이 단계는 sampling을 활성화하지 않는다.

## Cohort contract

- 승인 정책: Phase A internal-only
- 허용 역할: Project Owner, 팀장 영탁, Backend Owner
- 실제 계정 ID/allowlist는 제공되지 않았으므로 actual cohort는 `empty / 0%`
- 일반·익명 사용자, admin-only flow, private/deleted/unpublished/moderation-blocked content, allowlist 외 계정은 제외
- 계정 ID를 임의 생성하지 않음

## Reclassification

Resource budget과 cohort 정책은 승인됐지만 actual allowlist, external attestation 및 production disable drill이 없으므로 `NO_GO`다.
