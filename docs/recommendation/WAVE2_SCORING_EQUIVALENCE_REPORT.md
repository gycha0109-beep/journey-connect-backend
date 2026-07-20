# Wave 2 Scoring 동등성 검증 보고서

## 1. 판정

**현재 Wave 2 scoring 이식 범위: EXACT MATCH**

TypeScript Phase 2.9b 참조 구현과 Java 21 구현을 동일한 입력으로 실행한 결과, 대표 시나리오의 상태, 정렬된 목록, breakdown, provenance, 정책 버전 및 모든 비교 대상 `double`의 IEEE-754 raw bits가 정확히 일치했다.

## 2. 이식 범위

- `calculateInterestMatch`
- `evaluateContextEligibility`
- `calculateContextMatch`
- `calculateFreshness`
- `calculatePopularity`
- `scoreCandidate`

## 3. fixture 범위

### Interest

- 선호·회피·미매칭 feature 혼합
- explicit source priority
- hard avoid threshold
- 사용자 signal 없음
- feature breakdown 및 denominator

### Freshness

- post 7일
- post 14일 half-life 경계
- journey 30일 half-life 경계
- unsupported entity
- missing timestamp

### Popularity

- trusted snapshot
- minimum exposure 미달
- rejected trust status
- missing snapshot
- unsupported entity

### Context

- hard required 충족
- hard excluded 미충족
- hard excluded 발동
- soft preferred / soft avoided
- hard clause가 없는 soft-only context
- hard/soft breakdown

### Score composition

- 모든 컴포넌트 scored
- popularity neutral-filled
- context hard gate
- component effective weight
- weighted contribution
- anchor / hard-gate provenance

## 4. 검증 방식

```bash
bash scripts/recommendation/verify_java_core_wave2.sh
```

검증기는 다음을 수행한다.

1. Spring/JPA/Hibernate 의존 금지 검사
2. Java 21 `-Xlint:all -Werror` main/test 컴파일
3. foundation, Wave 1, Wave 2 계약 테스트
4. TypeScript 실구현 기반 Wave 2 fixture 생성
5. Java 실구현 기반 Wave 2 fixture 생성
6. canonical byte diff
7. committed golden fixture diff
8. 이전 Wave 1 및 foundation 회귀 게이트

## 5. 실제 발견한 언어 차이

Freshness 감쇠 계산에서 Java `Math.pow`는 V8 결과와 1 ULP 차이가 발생했다.

대표 7일 post 결과:

```text
V8 / TypeScript:  0x3fe6a09e667f3bcc
Java Math.pow:    1 ULP difference
Java StrictMath:  0x3fe6a09e667f3bcc
```

Java 구현을 `StrictMath.pow`로 변경해 참조 구현과 raw-bit exact match를 확보했다. epsilon 비교로 숨기지 않고 연산 원인을 식별한 뒤 수정했다.

## 6. 참조 계약 불일치

동등성 검증 중 다음 TypeScript 내부 불일치를 확인했다.

```text
calculateInterestMatch hard exclusion
→ status = hard_excluded
→ score = 0

scoreCandidate validation
→ hard_excluded interest score는 null이어야 함
```

Java는 포팅 단계 원칙에 따라 두 동작을 각각 그대로 재현한다. 이 문제는 전체 orchestration 전에 별도의 버전된 계약 수정으로 해결해야 한다.

## 7. 미완료 범위

본 보고서는 전체 알고리즘 동등성 보고서가 아니다. 다음은 아직 미완료다.

- base ranking
- diversity
- exploration
- exposure trace
- attribution
- replay
- offline evaluation
- full-run orchestration

따라서 P0 진입 조건은 아직 충족하지 않았다.
