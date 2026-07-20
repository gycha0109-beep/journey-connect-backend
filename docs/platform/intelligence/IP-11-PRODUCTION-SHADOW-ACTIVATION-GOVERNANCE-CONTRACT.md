# IP-11 Production Shadow Activation Governance Contract

## 상태

`ACTIVE / FAIL_CLOSED / GOVERNANCE_DECISIONS_APPROVED_WITH_CONDITIONS / PRODUCTION_DISABLED`

## Authority

- Activation owner: `Project Owner` (프로젝트 사용자 본인)
- Rollback owner: `Backend Owner`; 프로젝트 사용자가 Backend Owner를 겸할 수 있으나 역할 책임은 분리
- Kill-switch owner: `Project Owner`
- Backup owner: `팀장 영탁`
- Security/Privacy approval: `Project Owner + 팀장 영탁` 공동 승인
- 확인되지 않은 법적 실명, 계정 ID, 연락처, on-call 주소는 기록하지 않음

## Mandatory activation conditions

1. IP-12 implementation review
2. exact-SHA Gradle/Spring/PostgreSQL attestation PASS
3. actual internal account allowlist
4. production-equivalent disable drill
5. approved operating property/restart or remote switch procedure
6. Micrometer binding and manual metric-check procedure
7. explicit sampling approval not exceeding 10 BPS

Missing, blank, malformed or expired approval resolves to disabled/0 BPS. Owner assignment is governance authority, not code activation.

```text
IP-11: GOVERNANCE_DECISIONS_APPROVED_WITH_CONDITIONS
IP-11.5: TECHNICAL_CONTROLS_IMPLEMENTED / EXTERNAL_ATTESTATION_PENDING
IP-11.75: GOVERNANCE_APPROVAL_CLOSURE_COMPLETE / EXTERNAL_ATTESTATION_PENDING
Production shadow: DISABLED
Effective production sampling: 0 BPS
Search cutover: NOT STARTED
Go/No-Go: NO_GO
IP-12: HOLD_FOR_EXTERNAL_ATTESTATION_AND_OPERATIONAL_INPUTS
```
