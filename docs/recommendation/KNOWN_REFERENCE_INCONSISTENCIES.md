# TypeScript 참조 구현 확인된 계약 불일치

## REF-CONTRACT-001 — hard-excluded interest score 불일치

### 상태

- 분류: 참조 구현 내부 계약 불일치
- 심각도: High
- 포팅 영향: full score orchestration blocker
- 최초 확인: 2026-07-17
- Java silent fix: 금지

## 1. 재현

TypeScript 참조 구현의 `calculate-interest-match.ts`는 hard avoid가 발동하면 다음 결과를 만든다.

```text
status = hard_excluded
score = 0
hardExclusionFeatureIds = non-empty
```

반면 `score-candidate.ts`의 validation은 hard-excluded interest에 대해 다음을 요구한다.

```text
status = hard_excluded
score = null
notApplicableReason = null
hardExclusionFeatureIds = non-empty
```

따라서 실제 interest calculator의 hard-excluded 출력을 score composer에 직접 전달하면 `interest hard_excluded contract invalid` 오류가 발생한다.

## 2. 근거 위치

```text
reference/recommendation-ts-2.9b/
  src/domain/personalization/primitives/calculate-interest-match.ts
  src/domain/personalization/primitives/score-candidate.ts
```

Java에서도 다음 두 행위를 각각 exact port로 보존했다.

- `InterestMatchCalculator`: hard exclusion score `0.0`
- `CandidateScorer`: hard exclusion score non-null 거부

`CoreWave2ScoringContractTest`가 이 불일치를 재현하고 고정한다.

## 3. 영향

영향 있음:

- hard interest exclusion이 실제로 발생하는 full-run orchestration
- multiple hard exclusion 판정
- interest hard gate provenance

영향 없음:

- scored interest
- no-signal interest
- context-only hard exclusion
- freshness
- popularity
- 독립 컴포넌트 golden 비교

## 4. 금지 해결책

다음 방식은 금지한다.

- Java에서만 `0 → null` 자동 변환
- score composer validation을 Java에서만 완화
- golden fixture 기대값만 변경
- 기존 `interest-match-v1` 의미를 기록 없이 변경
- application adapter에서 무버전 normalization

이 방식들은 Java와 TypeScript 간 동등성 및 과거 replay 의미를 깨뜨린다.

## 5. 승인 가능한 해결책

### 선택지 A — 버전된 결함 수정

새 계약 버전을 추가한다.

```text
interest-match-v2
score-composition-v2
```

두 정책이 hard-excluded score를 `null`로 일치시키고, 기존 v1 replay는 계속 실행 가능하게 한다.

### 선택지 B — v1 defect-fix 승인

기존 v1이 아직 외부 저장·운영에 사용되지 않았다는 증거가 있고, 명시적인 breaking/defect-fix 승인을 받는 경우에만 양 언어 구현을 함께 수정한다.

필수 산출물:

- 변경 사유
- 영향 분석
- 이전 fixture 보관
- 신규 fixture
- replay 영향 보고서
- 계약서 변경 기록

## 6. 현재 결정

현 단계에서는 수정하지 않는다.

```text
독립 컴포넌트 exact port 계속 진행
→ ranking/diversity/exploration 이식
→ full orchestration 전 REF-CONTRACT-001 해결안 별도 승인
```
