# IP-10 Verification and Self Review

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-10-verification-self-review-v1` |
| 상태 | `COMPLETE` |

## 자체 리뷰 1 — 구조·권한

발견 및 보완:

1. default disabled configuration과 stage active configuration의 처리 순서에 따라 bridge bean 중복 가능성이 있었다.
   - `DisabledSearchShadowActivationCondition`을 추가해 valid test/stage activation일 때 default bean 자체를 제외했다.
2. malformed property binding이 condition 평가 중 context 실패로 이어질 가능성이 있었다.
   - activation probe가 일반 `RuntimeException`을 fail-closed disabled로 처리하도록 보완했다.
3. stage profile 지원을 위해 기존 IP-7 gate와 receipt에 additive 표현이 필요했다.
   - `search-shadow-stage` allowlist와 asynchronous `submitted` receipt 불변조건만 추가했으며 기존 wire value는 변경하지 않았다.

## 자체 리뷰 2 — 실패·동시성·테스트

확인:

- outer dispatch와 runtime timeout executor 모두 bounded
- common ForkJoinPool/unbounded queue 없음
- request thread는 future completion을 기다리지 않음
- provider exception, queue full, executor close, runtime timeout/cancellation 직접 검증
- sample 0에서 executor/runtime/log 호출 0
- legacy response identity·item list·pagination 유지
- IP-7 1,700 assertions를 포함한 기존 계약 회귀 유지

보완:

- invalid boolean/property도 disabled로 귀결되는 Spring context test 추가
- test/stage active graph의 단일 bridge bean 검증 강화

## 자체 리뷰 3 — 독립 최종 검토

판정:

- fixture-only 이름뿐인 activation이 아니라 `DefaultSearchRuntime` retrieval/ranking이 실제 호출됨
- production/default/prod+stage context에서 active graph 생성 금지
- Search output은 HTTP body에 연결되지 않음
- evidence에서 raw query/raw correlation/session 식별자 제외
- IP-9 외부 Gradle/backend attestation은 미실행으로 유지
- protected manifest는 IP-8 expected 320개를 실제 current source와 비교
- 최종 ZIP은 재추출 후 직접 compile/runner/hash 검증 대상으로 구성

## 잔여 검증

- Gradle/JUnit/Spring/PostgreSQL은 사용자 지시로 생략되어 external attestation pending
- production owner·budget·retention 결정 미해결
