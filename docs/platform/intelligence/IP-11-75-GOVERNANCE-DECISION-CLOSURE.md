# IP-11.75 Governance Decision Closure

## 문서 정보

| 항목 | 값 |
|---|---|
| 단계 | `IP-11.75` |
| 상태 | `GOVERNANCE_APPROVAL_CLOSURE_COMPLETE / EXTERNAL_ATTESTATION_PENDING` |
| 기준 ZIP | `JC-IP-11-5-Tech-Final.zip` |
| 기준 SHA-256 | `1caa01e929a7f762214290b62c1fdde88c7fdfd37d788685bd6d38801a7fc534` |
| Production shadow | `DISABLED` |
| Effective sampling | `0 BPS` |
| Search cutover | `NOT STARTED` |
| Go/No-Go | `NO_GO` |

## 목적

사용자가 승인한 운영 책임, 개인정보, 보존, 관측, 자원, sampling 및 internal cohort 결정을 IP-11/IP-11.5 governance evidence에 반영한다. 이 단계는 production activation, allowlist 생성, evidence persistence, SQL/production source 변경을 수행하지 않는다.

## 승인 반영

- Activation owner: `Project Owner` (프로젝트 사용자 본인)
- Rollback owner: `Backend Owner`; 프로젝트 사용자가 Backend Owner를 겸할 수 있으나 역할 책임은 분리
- Kill-switch owner: `Project Owner`
- Backup owner: `팀장 영탁`
- Security/Privacy approval: `Project Owner + 팀장 영탁` 공동 승인
- 확인되지 않은 법적 실명, 계정 ID, 연락처, on-call 주소는 기록하지 않음

- Raw query, raw/normalized query 원문, raw identity, JWT subject, session ID, full request/response payload: `PROHIBITED`
- Aggregate/bucket evidence: 최대 `14일`; persistence 구현 시 생성 시각 기반 자동 삭제 필수
- Error summary: 최대 `30일`; enum category, bucket, version만 허용
- 접근: `Project Owner`, `Backend Owner`
- 개인정보·raw data·오기록·정책 위반·잘못된 결속 발견 시 즉시 삭제
- 현재 production sink는 no-op이고 persistent storage/cleanup job은 없음
- 따라서 `POLICY_APPROVED / STORAGE_IMPLEMENTATION_PENDING`; production persistence는 계속 `DISABLED`

- 승인된 instrumentation target: Spring Boot structured logs + Micrometer metrics
- 현재 확인된 구현: privacy-safe metric abstraction, no-op/default sink, in-memory test sink, Spring Boot SLF4J 기반
- Micrometer production binding과 metric 확인 절차는 IP-12 또는 별도 integration 단계에서 검증 필요
- Prometheus, Grafana, 외부 APM, 중앙 로그 플랫폼: `DEFERRED`
- automated paging/on-call channel: `OPEN_OPERATIONAL_DETAIL`
- external dashboard 부재는 instrumentation policy 승인을 취소하지 않지만 controlled production activation 전 수동 확인 절차와 책임자 지정이 필수

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

- 승인 정책: Phase A internal-only
- 허용 역할: Project Owner, 팀장 영탁, Backend Owner
- 실제 계정 ID/allowlist는 제공되지 않았으므로 actual cohort는 `empty / 0%`
- 일반·익명 사용자, admin-only flow, private/deleted/unpublished/moderation-blocked content, allowlist 외 계정은 제외
- 계정 ID를 임의 생성하지 않음

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

## 남은 직접 blocker

1. Gradle 8.14.5, JUnit, Spring default/test/stage/prod context attestation
2. PostgreSQL 15에서 SQL 27·28와 projection integration attestation
3. actual production account allowlist
4. actual production property/restart 또는 remote kill-switch path 검증
5. production-equivalent disable drill
6. Micrometer binding과 수동 metric 확인 절차 검증
7. persistence를 도입하는 경우 TTL/ACL/deletion implementation
