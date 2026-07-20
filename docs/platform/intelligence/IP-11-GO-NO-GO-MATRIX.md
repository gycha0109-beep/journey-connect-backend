# IP-11 Go/No-Go Matrix

Machine-readable matrix: `verification/ip11/IP11_GO_NO_GO_MATRIX.tsv`.

## 결정

`NO_GO` — governance decisions are materially closed, but technical/operational evidence remains open.

## 개선된 항목

- activation, rollback, kill-switch 역할 지정
- initial pilot resource budget 승인
- 0 BPS current / 10 BPS pilot ceiling 승인
- raw data 금지와 14/30일 retention 정책 승인
- Spring structured logs + Micrometer instrumentation target 승인
- internal-only cohort 정책 승인
- emergency disable 책임 지정

## 남은 blocker

- Gradle/Spring/PostgreSQL external attestation
- actual production account allowlist
- SQL 27·28 PostgreSQL 실행
- actual production switch path와 disable drill
- Micrometer binding/manual metric check verification
- persistence를 도입할 경우 retention implementation
- production activation implementation 자체는 IP-12 범위

## 최종 상태

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

## IP-12 current reassessment

`NO_GO_FOR_TRAFFIC`. Production properties, 10 BPS ceiling, kill-switch, allowlist schema, Micrometer adapter and structured logging are implemented. Actual account hashes, activation approval and new-source Gradle/Spring attestation remain open.
