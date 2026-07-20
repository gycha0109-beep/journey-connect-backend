# IP-11.75 Approved Owner RACI and Authority

## 역할 결정

- Activation owner: `Project Owner` (프로젝트 사용자 본인)
- Rollback owner: `Backend Owner`; 프로젝트 사용자가 Backend Owner를 겸할 수 있으나 역할 책임은 분리
- Kill-switch owner: `Project Owner`
- Backup owner: `팀장 영탁`
- Security/Privacy approval: `Project Owner + 팀장 영탁` 공동 승인
- 확인되지 않은 법적 실명, 계정 ID, 연락처, on-call 주소는 기록하지 않음

`Project Owner`는 사용자의 법적 실명을 추정하지 않는 역할명이다. Project Owner와 Backend Owner가 같은 사람일 수 있지만 activation 승인, 기술 실행, rollback 검증 책임은 문서상 분리한다.

## RACI

| 활동 | Project Owner | Backend Owner | 팀장 영탁 | Security/Privacy Approver | Mandatory co-approval |
|---|---|---|---|---|---|
| Production activation 승인 | A | C | C | C | external attestation + security conditions |
| Activation 실행 | A | R | I | I | 없음 |
| Rollback 판단/실행 | A | R | C | I | 없음 |
| Kill-switch 실행 | A | R | Backup | I | 없음 |
| Sampling 변경 | A | R | C | C | 0 초과는 Security/Privacy 조건 충족 |
| Cohort 변경 | A | R | C | C | Project Owner + 팀장 영탁 |
| Resource budget 변경 | A | R | C | I | 없음 |
| Privacy policy 변경 | A | C | C | R | Project Owner + 팀장 영탁 |
| Retention 변경 | A | R | C | C | Project Owner + 팀장 영탁 |
| Evidence 접근 | A/R | R | I | C | 없음 |
| Incident investigation | A | R | C | C | 없음 |
| 재활성 승인 | A | R | C | C | attestation + drill + allowlist |

각 활동의 `A`는 정확히 하나다. 공동 Security/Privacy 승인은 별도 mandatory co-approval 조건으로 유지한다.

## 승인 취소·재승인

source/build/schema, cohort, sampling ceiling, retention, persistence, security finding 또는 incident가 바뀌면 승인은 만료되고 effective sample은 0 BPS로 복귀한다.
