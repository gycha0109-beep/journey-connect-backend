# IP-11 Production Shadow Owner Decision Packet

## 문서 정보

| 항목 | 값 |
|---|---|
| 상태 | `GOVERNANCE_DECISIONS_APPROVED_WITH_CONDITIONS` |
| current amendment | `IP-11.75` |
| Production shadow | `DISABLED` |
| Effective sampling | `0 BPS` |
| Go/No-Go | `NO_GO` |

## Owner decisions

- Activation owner: `Project Owner` (프로젝트 사용자 본인)
- Rollback owner: `Backend Owner`; 프로젝트 사용자가 Backend Owner를 겸할 수 있으나 역할 책임은 분리
- Kill-switch owner: `Project Owner`
- Backup owner: `팀장 영탁`
- Security/Privacy approval: `Project Owner + 팀장 영탁` 공동 승인
- 확인되지 않은 법적 실명, 계정 ID, 연락처, on-call 주소는 기록하지 않음

## Decision Register

Machine-readable authority: `verification/ip11/IP11_DECISION_REGISTER.tsv` and `verification/ip11-75/IP1175_DECISION_REGISTER.tsv`.

| Decision | Current status |
|---|---|
| Activation owner | `APPROVED_WITH_CONDITIONS` |
| Rollback owner | `APPROVED_WITH_CONDITIONS` |
| Kill-switch owner | `APPROVED_WITH_CONDITIONS` |
| Runtime input source | `OPEN_BLOCKER` — external DB/authority attestation |
| Resource budget | `APPROVED_FOR_INITIAL_PILOT` |
| Sampling ceiling | `APPROVED_WITH_CONDITIONS` — current 0, pilot max 10 BPS |
| Retention | `POLICY_APPROVED / STORAGE_IMPLEMENTATION_PENDING` |
| Security/Privacy | `APPROVED_WITH_CONDITIONS` |
| Observability | `INSTRUMENTATION_APPROVED / OPERATIONS_DESTINATION_DEFERRED` |
| Production cohort | `POLICY_APPROVED / ACCOUNT_ALLOWLIST_PENDING` |
| Emergency disable | `APPROVED_WITH_CONDITIONS` — production drill pending |
| External attestation | `DEFERRED` |

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

See [IP-11.75 Governance Decision Closure](IP-11-75-GOVERNANCE-DECISION-CLOSURE.md).
