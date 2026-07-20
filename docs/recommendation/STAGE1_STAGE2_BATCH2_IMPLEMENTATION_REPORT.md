# Stage 1 완료·Stage 2 Wave 2 Scoring 배치 구현 보고서

## 완료 사항

### Stage 1 계약 기반 완료

- state-event resolution
- strict UTC 및 millisecond 정규화
- UTF-16 code-unit 문자열 비교
- canonical JSON
- scoring 정책 및 context 계약
- 정식 Wave 1 golden exporter/oracle

### Stage 2 Wave 2 scoring 이식

- interest match
- hard context eligibility
- soft context match
- freshness
- popularity
- score composition
- terminal 상태와 component provenance
- Java contract test
- TypeScript↔Java exact golden gate

## 핵심 검증 결과

| 검증 | 결과 |
|---|---|
| Java main/test `javac --release 21` | PASS |
| Java `-Xlint:all -Werror` | PASS |
| 순수 코어 framework dependency scan | PASS |
| Foundation contract/oracle | PASS / EXACT MATCH |
| Wave 1 contract/golden | PASS / EXACT MATCH |
| Wave 2 scoring contract/golden | PASS / EXACT MATCH |
| committed golden 회귀 | PASS |

## 발견 및 보완

### V8 ↔ Java `pow` 1 ULP 차이

Freshness 계산에서 `Math.pow`가 V8과 1 ULP 달랐다. `StrictMath.pow`로 교체하여 raw-bit exact match를 확보했다.

### 참조 구현 hard-interest 계약 불일치

`calculateInterestMatch`는 hard-excluded score를 `0`으로 반환하지만 `scoreCandidate`는 `null`을 요구한다. Java 포팅 오류가 아니므로 자동 보정하지 않고 재현 테스트와 별도 이슈 문서로 고정했다.

## 현재 단계

```text
Stage 0  완료
Stage 1  완료
Stage 2  진행 중 — scoring slice 완료
P0       미진입
P1/P2    미진입
```

## 다음 작업

1. base ranking 포팅
2. complete comparator 및 terminal partition 검증
3. diversity reranking 포팅
4. seeded exploration 포팅
5. ranking pipeline exact golden 및 property gate

## 제한사항

전체 Spring backend Gradle 테스트는 실행 환경의 Gradle wrapper 배포본 접근 가능 여부에 따라 별도 검증한다. 순수 Java core는 Gradle 없이 완전 컴파일·실행 검증했다.
