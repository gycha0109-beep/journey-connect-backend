# IP-11.75 Handoff

## 상태

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

## 완료

- 사용자 승인 owner/RACI 반영
- privacy/retention/access policy 반영
- resource/sampling/cohort approval 반영
- observability instrumentation target 반영
- IP-11/IP-11.5 Decision Register와 Go/No-Go 재분류
- production source/config/build/SQL no-change evidence
- static/package/re-extract verification

## 다음 필수 입력

1. actual production account allowlist
2. production property/restart 또는 remote switch operating procedure
3. production-equivalent disable drill
4. Micrometer binding/manual metric check verification
5. Gradle/Spring/PostgreSQL exact-SHA attestation

IP-12는 위 조건이 닫히기 전 시작하지 않는다.

## IP-12 current status

The approved pilot ceiling is enforced at 10 BPS in source, while the current effective value remains 0 BPS. IP-12 external attestation and account inputs remain required.
