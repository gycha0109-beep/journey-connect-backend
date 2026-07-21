# DP-1 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `dp-1-handoff-v1` |
| 상태 | `COMPLETE_WITH_BLOCKED_FINGERPRINT_ALGORITHM` |
| 기준 브랜치 | `main` |
| 공식 baseline | `9d84f630e87d54f780e332eead0c1f8df6a51d0b` |
| 작업 시작 HEAD | `e8cd8e434e0eac75de561417ae4eb5a4f73e448b` |
| 구현 브랜치 | `codex/dp-1-event-contracts` |
| 다음 단계 후보 | `DP-2 PostgreSQL Event Store & Idempotency` |

## 1. 완료

- `jc-data-contracts` Java 21 pure contract module 생성
- Gradle module registry 등록
- `com.jc.data.contract.v1` package convention 적용
- Client command/canonical envelope 분리
- 9개 event family와 21개 `user_behavior` type compile-time 표현
- identity scheme 및 reference value object 구현
- 6종 version 의미 분리
- stable validation taxonomy와 explicit `ValidationResult` 구현
- structural canonicalization boundary 구현
- fingerprint fail-closed boundary 구현
- idempotency duplicate/conflict pure comparison 구현
- P0/P1/P2 compatibility fixture 구현
- 569 assertion contract runner 구현
- 전용 PR CI workflow와 machine-readable evidence 작성
- SC baseline reconciliation의 DP-1 경로 false-positive allowlist 최소 보정

## 2. 변경 계약

- `dp-1-event-domain-types-validation-v1`
- `client-event-command-v1`
- `platform-event-v1` Java representation
- `behavior-event-taxonomy-v1` Java registry
- `platform-event-canonical-json-v1` structural normalizer
- `data-event-consumer-v1` compatibility evaluator
- `dp-1-handoff-v1`

`event-idempotency-fingerprint-v1`의 idempotency boundary는 구현됐지만 fingerprint exact contract는 미승인 상태를 유지한다.

## 3. 보호 결과

- SQL/DB 변경: 없음
- production Java/Kotlin 변경: 없음
- Recommendation runtime 변경: 없음
- Search runtime 변경: 없음
- IP-12.5 production controls 변경: 없음
- P1/P2 source cutover: 없음
- identity mapping/join: 없음
- concrete fingerprint algorithm: 없음

## 4. 검증

직접 실행 결과:

```text
DP-1 data contract checks passed: 569
```

PASS:

- Java 21 compile
- `-Xlint:all -Werror`
- contract/fixture runner
- deterministic canonicalization
- locale/timezone/Map order independence
- identity namespace separation
- forbidden payload rejection
- unknown required enum/schema rejection
- duplicate/conflict classification
- fingerprint unresolved fail closed
- architecture dependency isolation

PR CI 최종 결과:

- Data Contract CI: PASS
- Recommendation Java Core CI: PASS
- Backend/IP-12.5 protected readiness gate: PASS
- SC Baseline Reconciliation: PASS
- protected branch diff: PASS

SQL, production runtime, Recommendation/Search/Intelligence 보호 소스의 변경은 없다.

## 5. Blocked item

`SC-DP1-009`:

- algorithm
- output encoding
- fingerprint wire ID
- exact inclusion set
- timestamp/build field inclusion

승인 전 `UnresolvedEventFingerprintBoundaryV1`만 사용한다. 임시 SHA-256, P0 fingerprint 복사, production-ready hash 선언은 금지한다.

## 6. DP-2 진입 조건

1. DP-1 PR merge 및 main authority 확정
2. `SC-DP1-009` 결정 또는 DP-2에서 fingerprint-dependent persistence를 분리하는 명시적 SC 승인
3. canonical DB target 및 SQL `29+` sequence 배정
4. event store physical writer/role/grant 승인
5. idempotency TTL/retention과 privacy 정책 승인
6. PostgreSQL atomic compare/concurrency 검증 계획 승인
7. 기존 P0/P1/P2 및 Search/IP-12.5 보호 gate 유지

## 7. 재개 지점

DP-1 merge 후 SC registry에서 `jc-data-contracts`를 ACTIVE로 갱신한다. DP-2는 DB allocation을 받은 뒤 event store와 atomic idempotency만 설계하며 retry/quarantine/replay executor는 DP-3으로 유지한다.
