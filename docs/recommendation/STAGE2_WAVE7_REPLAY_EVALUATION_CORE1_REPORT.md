# Stage 2 Wave 7 — Replay·Offline Evaluation 및 Java Core 1.0 완료 보고서

## 1. 결론

Journey Connect TypeScript Phase 2.9b 추천 알고리즘의 Java 21 전수 포팅을 완료했다.

Foundation부터 replay, offline evaluation, policy comparison, evaluation decision, case orchestration까지 TypeScript와 Java가 canonical output 및 IEEE-754 raw bits 수준에서 일치한다.

```text
Stage 0  TypeScript 기준선 동결       완료
Stage 1  Java 계약·기반 구조          완료
Stage 2  Java 전수 포팅·동등성        완료
Java Core 1.0                         고정
P0                                   진입 가능, 미착수
```

## 2. 이번 배치 구현

### 2.1 Full ranking collection

- ranking-v3 cursor를 끝까지 순회
- collector 최대 페이지 수 정책 적용
- page/cursor 연속성 검증
- absolute rank 연속성 검증
- candidate identity 중복·synthetic identity 검출
- final/terminal partition 검증
- score 및 signed-zero mutation 검출
- exploration provenance 검증

### 2.2 Replay evaluator

- exposure duplicate resolver 재사용
- snapshot validation과 full result reconstruction
- historical requested/effective limit binding
- observed page별 기대 이벤트 재생성
- snapshot/policy/seed/status/count/summary/page/candidate/fingerprint/cursor 비교
- exact interval union 및 coverage rate 계산
- 상태 우선순위:

```text
invalid_trace
invalid_snapshot
mismatch
partial_observation
exact_match
```

### 2.3 Policy comparison

- supplied replay·attribution 결과 재계산 검증
- baseline과 treatment에 같은 snapshot 사용
- 정책·seed만 병렬 비교
- Top-5/10/20 intersection·union·Jaccard·rank shift
- personalized/exploration origin metric
- diversity window/cap violation metric
- observed common support 및 outcome association
- global attribution quality

### 2.4 Evaluation decision

판정 우선순위를 고정했다.

```text
block
> insufficient_evidence
> review
> pass
```

평가 항목:

- binding 및 replay usability
- case/run/behavior ID uniqueness
- minimum case/effect evidence
- exact replay rate
- observation coverage
- guardrail threshold
- forbidden causal/rollout claim
- invariant violation

### 2.5 Case orchestration

`RecommendationOfflineEvaluationEngine`이 다음 흐름을 단일 계약으로 수행한다.

```text
Replay
├─ exact_match / partial_observation
│  └─ exposure resolve
│     → behavior resolve
│     → outcome attribution
│     → policy comparison
└─ mismatch / invalid_*
   └─ downstream 미실행
```

fail-closed 경계 테스트를 추가했다.

## 3. Golden 시나리오

- scored 후보 32개
- terminal 후보 2개
- 페이지 2개: 30개 + 2개
- exposure 2건
- behavior: click·like·report
- baseline exact full replay
- treatment: 동일 정책 + 다른 seed
- decision 경로 4종:
  - insufficient evidence
  - pass
  - review
  - block

검증 결과:

```text
collector pages                  2
final candidates                32
terminal candidates              2
replay status          exact_match
observation coverage          full
Top-K cutoffs               5/10/20
TS ↔ Java canonical diff       none
```

## 4. Core 1.0 통합 게이트

신규 스크립트:

```bash
bash scripts/recommendation/verify_java_core_1_0.sh
```

수행 내용:

1. framework dependency scan
2. Java 21 main/test 단일 컴파일
3. Foundation~Wave 7 계약 테스트 9종
4. TS exporter 10종 병렬 실행
5. Java oracle 10종 실행
6. TS↔Java exact diff
7. committed golden 9종 검증
8. TypeScript 참조 SHA-256 418개 검증

최종 출력:

```text
TypeScript ↔ Java Foundation through Wave 7: EXACT MATCH
Java Core 1.0 exact-equivalence gate: PASS
Java recommendation core main sources: 207
Java recommendation core test sources: 20
```

## 5. 버전·CI 변경

- `jc-recommendation-core` 버전: `1.0.0`
- Gradle `coreWave7OfflineEvaluationContractTest` 추가
- `check`에 Wave 7 계약 포함
- GitHub Actions를 Core 1.0 단일 통합 게이트로 변경
- README와 진행 상태 문서 갱신

## 6. 검증 결과

```text
Reference SHA-256                    418/418 PASS
TypeScript typecheck                 PASS
TypeScript lint                      PASS
TypeScript tests                     65 files / 963 tests PASS
Java 21 -Xlint:all -Werror           PASS
Foundation contract                  PASS
Wave 1 contract                      PASS
Wave 2 scoring contract              PASS
Wave 3 ranking/diversity contract    PASS
Wave 3 exploration contract          PASS
Wave 4 integration contract          PASS
Wave 5 exposure contract             PASS
Wave 6 attribution contract          PASS
Wave 7 offline evaluation contract   PASS
Foundation~Wave 7 TS↔Java            EXACT MATCH
Committed golden fixtures            VERIFIED
```

## 7. 남은 외부 게이트

이 실행 환경에서는 `services.gradle.org` DNS 접근이 차단되어 Gradle 8.14.5 배포본을 받을 수 없었다. 이는 소스 테스트 실패가 아니라 wrapper bootstrap 단계의 환경 제한이다. 최종 적용본을 사용자 로컬에서 다음 명령으로 검증해야 한다.

```powershell
cd jc-backend
.\gradlew.bat clean test
.\gradlew.bat :jc-recommendation-core:check
```

두 명령이 성공하면 Stage 2 종료와 P0 진입 조건이 완전히 충족된다.

## 8. P0 진입 원칙

P0는 품질 개선이 아니라 저장·통합 단계다.

다음 순서를 유지한다.

```text
Flyway trusted storage
→ append-only/idempotency
→ candidate projection/mapper
→ Java Core 직접 호출
→ run 전체 순위 저장
→ exposure/behavior 저장
→ SHADOW
→ persistence replay
→ CANARY
```

Core 1.0 정책과 계산 의미는 P0에서 변경하지 않는다.
