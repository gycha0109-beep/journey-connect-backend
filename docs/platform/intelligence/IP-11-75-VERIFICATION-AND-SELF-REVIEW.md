# IP-11.75 Verification and Self Review

## 검증 범위

- 사용자 승인 결정 → Decision Register/RACI/Go-No-Go 정합성
- retention 14일, error summary 30일, raw data prohibition
- resource 1/2/8/200/300/100, current 0 BPS, pilot ceiling 10 BPS
- actual cohort empty/0%
- production source/config/build/SQL no-change
- protected source와 SQL SHA
- document link/decision ID/package path 검증

## 자체 리뷰 1 — 승인 해석

발견·보완:

1. `Project Owner`를 법적 실명으로 확장하지 않음.
2. 정책 승인과 persistence/TTL implementation을 분리함.
3. 10 BPS pilot ceiling과 현재 0 BPS를 분리함.
4. instrumentation 승인과 dashboard/alert defer를 분리함.

## 자체 리뷰 2 — 계약 일관성

발견·보완:

1. RACI 활동당 Accountable을 하나로 제한하고 공동 Security approval는 mandatory co-approval로 표현함.
2. IP-11과 IP-11.5의 resource/retention/cohort 문구를 IP-11.75 amendment로 통일함.
3. current technical sample capability 100 BPS와 governance ceiling 10 BPS 차이를 IP-12 검증 조건으로 명시함.

## 자체 리뷰 3 — 독립 Go/No-Go

확인:

- external attestation 없이 GO 선언 없음
- production shadow disabled, effective sample 0 BPS
- SQL 01..28와 production source/config/build unchanged
- actual cohort empty
- remote switch/dashboard/persistence를 구현된 것으로 주장하지 않음

## 미실행

- Gradle/JUnit/Spring: `NOT EXECUTED — USER-DIRECTED SKIP`
- Docker/Testcontainers/PostgreSQL: `NOT EXECUTED — USER-DIRECTED SKIP`
- SQL 27·28 PostgreSQL: `EXTERNAL_ATTESTATION_PENDING`
