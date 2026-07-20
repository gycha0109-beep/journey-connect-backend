# Stage 2 Wave 4~6 구현·검증 보고서

## 1. 보고 범위

이번 배치는 Java 전수 포팅 Stage 2의 다음 범위를 구현한다.

```text
Wave 4  ranking-v2/v3 통합 및 cursor-v2/v3
Wave 5  recommendation exposure trace와 duplicate resolver
Wave 6  behavior resolver와 outcome attribution
```

본 배치에서는 추천 품질 정책을 변경하지 않았다. TypeScript 참조 구현의 계약과 결과를 Java 21에서 그대로 재현하는 데만 집중했다.

## 2. 구현 결과

### 2.1 Wave 4 — ranking-v2/v3 통합

기존 base ranking 내부 로직을 `BaseRankingSnapshotBuilder`로 추출했다. ranking-v1/v2/v3가 같은 base snapshot과 terminal partition을 사용하므로 동일 로직의 복제와 버전 간 drift를 방지한다.

통합 경로:

```text
validated scored candidates
→ base ranking snapshot
→ diversity reranking
→ seeded exploration
→ final partition
→ versioned cursor
→ page slice
```

구현 항목:

- ranking-v2 diversity integration
- ranking-v3 exploration integration
- cursor-v2 canonical encode/decode
- cursor-v3 canonical encode/decode
- Base64URL byte contract
- page boundary 검증
- terminal candidate migration
- personalized/exploration identity invariant
- 입력 순서 독립성

### 2.2 Wave 5 — exposure trace

ranking-v3 결과를 replay와 attribution에 필요한 최소 노출 이벤트로 투영한다.

구현 항목:

- exposure candidate projection
- replay key fingerprint
- page fingerprint
- event signature projection
- SHA-256 canonical hashing
- signed zero 보존
- delivery-only metadata fingerprint 비영향
- event ID conflict
- idempotency key conflict
- run invariant conflict
- transitive alias ambiguity
- deterministic duplicate audit

raw cursor, terminal 내부 상세, 불필요한 사용자 데이터는 exposure payload에 포함하지 않는다.

### 2.3 Wave 6 — behavior resolution 및 attribution

행동 이벤트를 먼저 결정론적으로 정규화·중복 해소한 뒤, 유효한 노출에 귀속한다.

구현 항목:

- behavior canonical signature
- event ID와 idempotency key 기반 duplicate/conflict
- alias ambiguity 검출
- run/replay case scope 검증
- 사용자·세션 일치 검증
- 최신 선행 exposure 선택
- event별 attribution window
- cutoff 검증
- supported event 분류
- positive·negative·severe-report 수치 outcome
- attribution audit category 11종

기준 window:

```text
click / view / tag 계열  30분
그 외 지원 행동         7일
```

## 3. TypeScript ↔ Java exact 검증

각 Wave는 TypeScript exporter와 Java oracle을 독립 실행한다. 양쪽 결과를 canonical JSON으로 직렬화해 exact diff하며, 부동소수점 값은 raw IEEE-754 bits도 비교한다.

```text
Wave 4 ranking integration       EXACT MATCH
Wave 5 exposure trace            EXACT MATCH
Wave 6 outcome attribution       EXACT MATCH
```

Wave 4 검증 항목:

- ranking-v2/v3 순서
- origin
- diversity summary
- exploration decision
- terminal partition
- cursor-v2/v3 bytes
- page boundary

Wave 5 검증 항목:

- replay key
- page fingerprint
- candidate projection
- signed zero
- duplicate audit
- conflict error code

Wave 6 검증 항목:

- behavior duplicate resolution
- exposure 선택
- window 경계
- cutoff
- audit category
- outcome 값
- 정렬 순서

## 4. Java 검증 구성

```text
Main Java sources                 201
Test/oracle Java sources           17
Compiler                           Java 21
Compiler flags                     -Xlint:all -Werror
Framework dependency in core       금지
```

주요 명령:

```bash
bash scripts/recommendation/verify_java_core_wave3.sh
bash scripts/recommendation/verify_java_core_wave6.sh
```

Wave 6 통합 스크립트는 다음을 수행한다.

1. 순수성 검사
2. main/test Java 1회 컴파일
3. Wave 4~6 계약 테스트
4. TypeScript exporter 실행
5. Java oracle 실행
6. exact canonical diff
7. committed golden 회귀 확인

## 5. CI 변경

`.github/workflows/recommendation-java-port-ci.yml`을 Wave 6까지 승격했다.

CI 순서:

1. TypeScript baseline SHA-256 검증
2. lockfile 기반 `npm ci`
3. TypeScript typecheck
4. TypeScript lint
5. TypeScript 963 tests
6. Java Foundation~Wave 3 검증
7. Java Wave 4~6 exact 검증

## 6. 회귀 방지 기준

다음 변경은 실패로 처리한다.

- cursor byte가 달라짐
- ranking identity/rank/origin이 달라짐
- `-0.0`이 `+0.0`으로 무단 정규화됨
- exposure fingerprint가 delivery metadata에 영향받음
- 중복 이벤트가 다른 payload인데 정상 dedupe됨
- 최신 선행 exposure가 아닌 이벤트에 귀속됨
- attribution window 경계가 달라짐
- audit event가 누락되거나 순서가 비결정적임
- 기존 golden을 설명 없이 갱신함

## 7. 현재 판정

```text
Stage 0                          완료
Stage 1                          완료
Stage 2 scoring                  완료
Stage 2 ranking primitive        완료
Stage 2 ranking integration      완료
Stage 2 exposure trace           완료
Stage 2 outcome attribution      완료
Stage 2 replay/evaluation        미완료
P0                               진입 금지
```

이번 배치로 실제 노출과 행동 결과를 결정론적으로 연결할 수 있는 Java 계산 계층이 확보됐다. 그러나 replay와 offline evaluation이 아직 남아 있으므로 Java Core 1.0 또는 P0 완료로 판정하지 않는다.

## 8. 다음 작업

```text
replay collection
→ exact replay
→ offline evaluation
→ policy comparison
→ evaluation decision
→ 전체 orchestration golden
→ Java Core 1.0 동등성 게이트
```

## 9. 최종 재검증 결과

2026-07-18 기준 최종 재검증 결과는 다음과 같다.

```text
GitHub Actions YAML syntax                     PASS
Reference SHA-256 manifest                     418/418 PASS
TypeScript typecheck                           PASS
TypeScript lint                                PASS
TypeScript tests                               65 files / 963 tests PASS
Foundation~Wave 3 Java contracts              PASS
Foundation~Wave 3 TS↔Java golden              EXACT MATCH
Wave 4 Java contract                           PASS
Wave 4 TS↔Java golden                          EXACT MATCH
Wave 5 Java contract                           PASS
Wave 5 TS↔Java golden                          EXACT MATCH
Wave 6 Java contract                           PASS
Wave 6 TS↔Java golden                          EXACT MATCH
Java compiler warnings                         0
```

이 실행 환경에서는 전체 Vitest 명령의 worker가 테스트 완료 후 종료되지 않는 현상이 발생했다. 동일한 65개 테스트 파일을 7개 독립 배치로 실행해 결과를 합산했으며, 최종 집계는 `963/963 PASS`다. 테스트 실패나 기대값 변경은 없었다.

전체 Spring Boot Gradle 테스트는 wrapper가 `services.gradle.org`에서 Gradle 8.14.5를 내려받는 단계에서 DNS가 차단돼 이 컨테이너에서는 재실행하지 못했다.

```text
java.net.UnknownHostException: services.gradle.org
```

이전 Wave 3 기준 소스는 사용자 로컬 환경에서 `gradlew.bat clean test` 통과가 확인됐다. 이번 Wave 4~6 배치는 독립 Java 컴파일과 exact 검증을 통과했으며, 최종 적용 후 사용자 로컬 또는 CI의 네트워크 가능한 환경에서 백엔드 전체 `clean test`를 다시 실행해야 한다.
