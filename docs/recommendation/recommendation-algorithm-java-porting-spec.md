# Journey Connect 추천 알고리즘 Java 전수 포팅 및 P0·P1·P2 적용 설계 명세

## 1. 문서 정보

| 항목 | 내용 |
|---|---|
| 문서명 | Journey Connect 추천 알고리즘 Java 전수 포팅 및 P0·P1·P2 적용 설계 명세 |
| 파일명 | `설계 명세.md` |
| 대상 프로젝트 | Journey Connect |
| 대상 백엔드 | Java 21 / Spring Boot 3.5 계열 |
| 기준 알고리즘 | TypeScript Phase 2.9b 구현 |
| 문서 성격 | 구현 기준, 구조 계약, 회귀 방지 기준, 병합 게이트 |
| 상태 | 설계 확정안 |
| 적용 원칙 | 기존 Phase 3~4b와 독립적인 알고리즘 보강 트랙 |

---

## 2. 목적

현재 추천 알고리즘은 TypeScript로 구현되어 있으나, Journey Connect의 실제 백엔드는 Java/Spring Boot 기반이다.

본 작업의 목적은 다음과 같다.

1. TypeScript 추천 알고리즘의 동작을 Java 21로 정확히 재구현한다.
2. Java 구현이 기존 TypeScript 구현과 기능적으로 동등함을 증명한다.
3. 검증된 Java 추천 코어를 Journey Connect 백엔드에 직접 통합한다.
4. P0, P1, P2 개선을 Java 기반으로 순차 진행한다.
5. 모든 전환과 개선 과정에서 과거 정책, replay, snapshot, 평가 결과의 재현 가능성을 보존한다.
6. 최종적으로 별도 Node/TypeScript 런타임 의존성을 제거한다.

본 작업은 단순 소스 번역이 아니다.

> TypeScript 구현을 실행 가능한 기준 명세와 참조 오라클로 고정하고, 동일 계약을 만족하는 Java 도메인 모듈을 독립적으로 재구현하는 작업이다.

---

## 3. 버전 및 단계 해석

### 3.1 기존 프로젝트 단계와의 관계

Journey Connect 플랫폼 본체는 이미 Phase 3~4b 수준까지 별도 개념으로 진행되었다.

이후 추가되는 `2.9.x`는 과거 단계로 회귀하는 의미가 아니다.

```text
Journey Connect 플랫폼 개발 계열
├─ Phase 3
├─ Phase 4
└─ Phase 4b

추천 알고리즘 보강 계열
├─ TypeScript Phase 2.9b 기준 구현
├─ Java Port
├─ P0
├─ P1
└─ P2
```

두 계열은 목적과 버전 의미가 다르다.

### 3.2 최종 진행 순서

```text
Stage 0  TypeScript 기준선 동결
Stage 1  Java 추천 코어 골격 구축
Stage 2  Java 전수 포팅 및 기능 동등성 검증
Stage 3  P0: 신뢰 저장·이벤트·프로젝트 통합
Stage 4  P1: 추천 품질·정책 개선
Stage 5  P2: 통계 검증·출시 판정
Stage 6  TypeScript 참조 구현 제거 또는 보관 전환
```

다음 두 방식은 금지한다.

```text
금지 1
TypeScript 선삭제 → Java 재작성 → 이후 검증

금지 2
Java 포팅 + P0 + P1 + P2 동시 변경
```

포팅 오류와 알고리즘 개선 효과를 분리할 수 없기 때문이다.

---

## 4. 최상위 개발 원칙

### 4.1 기존 TypeScript는 먼저 삭제하지 않는다

TypeScript 구현은 Java 동등성이 입증될 때까지 참조 구현으로 유지한다.

### 4.2 Java 포팅 중 알고리즘 의미를 개선하지 않는다

포팅 단계의 목표는 개선이 아니라 동등성이다.

다음 변경은 Java 동등성 확보 전 금지한다.

- 가중치 조정
- 새로운 점수 요소 추가
- 정렬 tie-break 변경
- diversity 정책 변경
- exploration 비율 변경
- attribution window 변경
- 평가 임계값 변경
- 기존 validation 완화 또는 강화
- fingerprint 계산 변경

### 4.3 기존 정책은 덮어쓰지 않는다

```java
// 금지
RankingPolicyV1의 값 직접 변경

// 허용
RankingPolicyV1 유지
RankingPolicyV2 신규 추가
```

### 4.4 알고리즘 코어는 순수 계산 모듈로 유지한다

코어는 다음에 직접 의존하지 않는다.

- Spring
- JPA
- Hibernate
- HTTP
- Redis
- PostgreSQL
- 시스템 현재 시각
- 환경변수
- 난수 전역 상태
- 인증 컨텍스트

코어 계약은 다음 형태를 유지한다.

```text
명시적 입력
→ 결정론적 계산
→ 명시적 출력
```

### 4.5 DB 행을 코어 타입으로 직접 전달하지 않는다

```text
DB Projection
→ Application Mapper
→ Recommendation Core Input
```

### 4.6 과거 결과는 수정하지 않는다

snapshot, recommendation run, exposure, evaluation 결과는 기본적으로 append-only로 관리한다.

### 4.7 테스트 실패를 기대값 갱신으로 덮지 않는다

기존 결과가 달라진 경우 다음 중 하나가 명시되어야 한다.

1. 신규 정책 버전에서 의도적으로 변경됨
2. 기존 구현의 명확한 버그가 수정됨
3. 승인된 breaking change임

그 외는 회귀로 판정한다.

---

## 5. 목표 아키텍처

## 5.1 권장 Gradle 멀티모듈 구조

```text
Journey-Connect/
├─ settings.gradle
├─ build.gradle
├─ jc-backend/
│  └─ Spring Boot 애플리케이션
├─ jc-recommendation-core/
│  └─ 순수 Java 추천 알고리즘
├─ jc-recommendation-contract-test/
│  └─ TypeScript ↔ Java 동등성 및 golden fixture 검증
└─ reference/
   └─ recommendation-ts-2.9b/
      └─ 동결된 TypeScript 참조 구현
```

프로젝트 구조 변경 비용이 너무 크면 초기에는 다음 구조도 허용한다.

```text
jc-backend/
└─ src/main/java/com/jc/backend/recommendation/core/
```

다만 최종 권장 구조는 독립 Gradle 모듈이다.

## 5.2 의존 방향

```text
jc-backend
    ↓
jc-recommendation-core

jc-recommendation-core
    ✕ Spring
    ✕ JPA
    ✕ jc-backend
```

순환 의존은 금지한다.

---

## 6. `jc-recommendation-core` 패키지 구조

```text
com.jc.recommendation
├─ model
│  ├─ candidate
│  ├─ context
│  ├─ feature
│  ├─ profile
│  ├─ result
│  └─ snapshot
├─ policy
│  ├─ ranking
│  ├─ diversity
│  ├─ exploration
│  ├─ attribution
│  └─ evaluation
├─ validation
├─ scoring
│  ├─ interest
│  ├─ context
│  ├─ freshness
│  ├─ popularity
│  └─ composition
├─ ranking
├─ diversity
├─ exploration
├─ exposure
├─ attribution
├─ replay
├─ evaluation
├─ canonical
├─ fingerprint
└─ support
```

---

## 7. Java 타입 설계 기준

| TypeScript 개념 | Java 기준 |
|---|---|
| readonly object | `record` |
| string union | `enum` |
| discriminated union | `sealed interface` |
| readonly array | `List.copyOf()` |
| optional field | `Optional` 남용 금지, nullable 여부 명시 |
| number | 기본 `double` |
| 정수 count/rank | `int` 또는 `long` |
| UTC timestamp | `Instant` |
| deterministic map | `LinkedHashMap` 또는 key 정렬 |
| validation error | 오류 코드 enum + 도메인 예외 |
| opaque identifier | 전용 value object 또는 검증된 `String` |

### 7.1 점수 타입

TypeScript의 `number`는 IEEE-754 double이므로 Java 포팅 시 점수 계산은 `double`을 기본으로 한다.

`BigDecimal`로 변경하지 않는다.

```text
double
- score
- weight
- decay
- similarity
- probability-like value

int / long
- rank
- count
- epoch millis
- sequence

BigDecimal
- 정확한 금액·예산 계산이 실제로 필요한 경우만 사용
```

### 7.2 불변성

모든 입력과 결과 객체는 생성 후 변경되지 않아야 한다.

- `record` 사용
- mutable collection 외부 노출 금지
- 생성 시 `List.copyOf`, `Map.copyOf`
- 정렬이 필요하면 새로운 리스트 생성
- 입력 리스트를 제자리 정렬하지 않음

---

## 8. TypeScript 기준선 동결

## 8.1 동결 대상

- 소스 전체
- 정책 버전
- 963개 기존 테스트
- validation 순서
- 오류 코드
- ranking 결과
- diversity 결과
- exploration 결과
- replay 결과
- snapshot 구조
- fingerprint
- signed zero 처리
- seed 기반 결과
- canonicalization 규칙

## 8.2 참조 구현 식별자

예시:

```text
reference-implementation: ts-phase-2.9b
reference-build-id: Git commit SHA
```

모든 golden fixture에는 참조 build ID를 기록한다.

---

## 9. 포팅 작업 순서

## Wave 1. 계약 및 기반 타입

- identifier
- enum
- record
- policy model
- validation error
- result union
- immutable collection utilities
- comparator utilities
- UTC parser
- numeric utilities

완료 조건:

- 모든 Java 타입 컴파일
- JSON fixture 역직렬화
- validation 기본 테스트 통과
- null/누락 필드 계약 고정

## Wave 2. 독립 점수 계산

- hard context eligibility
- interest match
- soft context match
- freshness
- popularity
- score component composition
- terminal candidate 분리

완료 조건:

- scoring golden vector exact match
- invalid input 오류 코드 일치
- 입력 객체 불변

## Wave 3. ranking·diversity·exploration

- base ranking
- complete tie-break
- diversity reranking
- exploration insertion
- provenance
- final rank assignment
- terminal result merge

완료 조건:

- 최종 entity 순서 exact match
- origin exact match
- seed 결과 exact match
- signed zero 보존
- 중복 후보 없음

## Wave 4. exposure·attribution

- page exposure
- event fingerprint
- idempotency
- attribution window
- outcome attribution
- deduplication

완료 조건:

- 동일 이벤트 중복 처리 일치
- 경계 시간 처리 일치
- exposure fingerprint 일치

## Wave 5. replay·evaluation

- replay input
- policy binding
- invariant verification
- offline evaluation
- evaluation decision
- policy comparison

완료 조건:

- replay exact match
- policy version vector 일치
- 평가 결과 일치
- canonical fingerprint 일치

## Wave 6. 전체 오케스트레이션

```text
validated input
→ score
→ rank
→ diversity
→ exploration
→ snapshot
→ result
```

완료 조건:

- 전체 실행 golden fixture exact match
- 대량 differential test 통과
- Java core 1.0 기준선 고정

---

## 10. 언어 간 호환성 위험

## 10.1 정렬

모든 comparator는 완전한 순서를 정의한다.

예시:

```text
score DESC
→ freshness DESC
→ entityType ASC
→ entityId ASC
```

tie 상태에서 단순히 `0`을 반환해 입력 순서에 의존하지 않는다.

## 10.2 signed zero

`0.0`과 `-0.0`은 별도로 취급해야 할 수 있다.

Java에서는 다음 방식으로 확인한다.

```java
Double.doubleToRawLongBits(value)
```

snapshot 및 persistence에서 필요하면 다음을 분리 저장한다.

```text
score
scoreIsNegativeZero
```

## 10.3 NaN 및 Infinity

기존 TypeScript 계약에 따라 허용 여부를 고정한다.

기본 원칙:

- 입력에서 거부
- 계산 결과 발생 시 오류
- JSON snapshot에 직접 저장 금지

## 10.4 JSON canonicalization

Jackson 기본 직렬화 결과를 snapshot hash 원본으로 사용하지 않는다.

canonicalization 규칙을 별도로 고정한다.

- UTF-8
- key 정렬
- null 처리
- 누락 필드 처리
- 배열 순서 유지
- 숫자 문자열 규칙
- `-0` 처리
- UTC timestamp 형식
- escape 규칙
- schema version 포함
- snapshot kind domain separation 포함

## 10.5 날짜

코어는 `Instant` 또는 strict UTC ISO-8601만 허용한다.

허용 예:

```text
2026-07-17T12:00:00Z
```

offset 없는 local datetime은 코어에서 허용하지 않는다.

## 10.6 의사난수

Java 표준 `Random`, `SplittableRandom`, `SecureRandom`으로 교체하지 않는다.

현재 TypeScript의 PRNG 알고리즘, 비트 연산, overflow 의미를 그대로 재구현한다.

JavaScript bitwise 연산은 signed 32-bit 기반이므로 `int` overflow를 명시적으로 검증한다.

## 10.7 문자열 비교

locale 종속 비교를 사용하지 않는다.

기본적으로 Unicode code point 또는 기존 TypeScript 비교 의미를 명시적으로 복제한다.

---

## 11. 테스트 전략

## 11.1 기존 TypeScript 테스트

기존 테스트는 참조 구현의 행위를 고정하는 역할을 한다.

Java 테스트로 전환되었다고 해서 기존 테스트를 즉시 삭제하지 않는다.

## 11.2 Golden Vector Test

TypeScript가 입력과 기대 출력을 JSON fixture로 생성한다.

```text
src/test/resources/golden/
├─ validation/
├─ scoring/
├─ ranking/
├─ diversity/
├─ exploration/
├─ exposure/
├─ attribution/
├─ replay/
└─ evaluation/
```

fixture 메타데이터:

```json
{
  "fixtureVersion": "golden-v1",
  "referenceBuildId": "git-sha",
  "policyVersions": {},
  "input": {},
  "expected": {}
}
```

## 11.3 Differential Test

동일 입력을 TypeScript와 Java에 공급하고 결과를 비교한다.

```text
input generator
├─ 정상 입력
├─ 경계 입력
├─ 잘못된 입력
├─ tie-heavy 입력
├─ signed-zero 입력
├─ empty profile
├─ all-excluded candidates
└─ duplicate identity cases
```

비교 대상:

- 결과 candidate identity
- absolute rank
- origin
- terminal reason
- component score
- total score
- provenance
- snapshot
- fingerprint
- error code

## 11.4 Property Test

검증할 invariant:

- 같은 입력과 seed는 항상 같은 결과
- 입력 후보 수보다 결과 후보 수가 많아질 수 없음
- 결과 identity 중복 없음
- terminal 후보와 ranked 후보의 교집합 없음
- 모든 입력 후보는 최종 partition 중 하나에 포함
- hard-excluded 후보는 ranked 결과에 포함되지 않음
- rank는 1부터 연속
- 정책 버전 누락 없음
- 입력 객체 변경 없음

## 11.5 Java 단위 테스트

JUnit 5와 AssertJ를 기본으로 한다.

필요 시 jqwik 같은 property-based test 라이브러리를 별도 검토한다.

## 11.6 PostgreSQL 통합 테스트

P0 이후 다음 항목은 실제 PostgreSQL 환경에서 검증한다.

- `TIMESTAMPTZ`
- `BYTEA`
- append-only trigger
- unique constraint
- idempotency conflict
- Flyway migration
- snapshot bytes round-trip
- cursor pagination
- concurrent event insertion

---

## 12. Java 동등성 완료 게이트

다음 조건을 모두 만족해야 P0로 진입할 수 있다.

```text
[ ] TypeScript 기준선 동결 완료
[ ] Java core 전체 모듈 구현 완료
[ ] 기존 핵심 golden vector exact match
[ ] 모든 identity/rank/origin exact match
[ ] seed 기반 exploration exact match
[ ] replay exact match
[ ] fingerprint exact match
[ ] validation 오류 코드 일치
[ ] validation 순서 일치
[ ] signed zero 처리 일치
[ ] canonical serialization 일치
[ ] differential test 통과
[ ] property test 통과
[ ] 입력 불변성 검증
[ ] Java 전체 테스트 통과
[ ] Java core 버전 고정
```

내부 score의 부동소수점 차이를 단순 epsilon으로 숨기지 않는다.

차이가 발생한 연산 위치를 먼저 식별해야 한다.

---

# 13. P0 설계

## 13.1 P0 목표

P0는 추천 품질 개선 단계가 아니다.

P0의 목표는 다음과 같다.

> 검증된 Java 추천 코어를 Journey Connect의 실제 데이터와 연결하고, 모든 입력·출력·노출·행동 이벤트를 재현 가능한 형태로 저장하며, shadow 및 canary 실행이 가능한 상태를 만든다.

## 13.2 P0 범위

- Spring Boot와 Java core 연결
- candidate retrieval
- DB row → core input mapping
- trusted snapshot
- recommendation run 저장
- exposure 저장
- behavior event 저장
- idempotency
- replay 가능한 저장 구조
- run 기반 pagination
- shadow mode
- canary mode
- 장애 fallback
- PostgreSQL 통합 테스트

## 13.3 P0에서 하지 않는 것

- 가중치 학습
- ML 모델 도입
- 정책 자동 최적화
- 대규모 벡터 검색
- 고급 cold-start
- confidence interval 기반 출시 판단
- 기존 피드 즉시 전면 교체

---

## 14. P0 백엔드 패키지 구조

```text
com.jc.backend.recommendation
├─ api
│  ├─ RecommendationFeedController
│  ├─ RecommendationEventController
│  └─ RecommendationPreferenceController
├─ application
│  ├─ RecommendationFeedService
│  ├─ RecommendationShadowService
│  ├─ RecommendationBehaviorService
│  ├─ RecommendationReplayService
│  └─ RecommendationModeDecider
├─ mapping
│  ├─ RecommendationCandidateMapper
│  ├─ RecommendationProfileMapper
│  ├─ RegionFeatureMapper
│  └─ RecommendationTimeMapper
├─ query
│  └─ RecommendationCandidateQueryRepository
├─ persistence
│  ├─ RecommendationSnapshotStore
│  ├─ RecommendationRunStore
│  ├─ RecommendationExposureStore
│  └─ RecommendationBehaviorStore
├─ cursor
│  └─ RecommendationCursorCodec
├─ config
│  └─ RecommendationProperties
└─ support
```

---

## 15. P0 실행 흐름

## 15.1 첫 페이지

```text
1. 인증 사용자와 session 확인
2. requestId, runId, referenceTime 생성
3. candidate retrieval 실행
4. 사용자 명시적 선호와 행동 signal 조회
5. DB 데이터를 core input으로 변환
6. Java recommendation core 직접 호출
7. trusted snapshot 생성
8. 전체 최종 순위 저장
9. 반환 대상 게시물의 공개 상태 재검증
10. exposure event 저장
11. 첫 페이지 반환
```

## 15.2 다음 페이지

다음 페이지에서는 알고리즘을 다시 실행하지 않는다.

```text
recommendation_run_candidate
WHERE run_id = ?
  AND absolute_rank BETWEEN ? AND ?
```

이유:

- 페이지 간 순위 drift 방지
- 신규 게시물 삽입 영향 차단
- 좋아요 수 변화 영향 차단
- replay 가능성 보존
- 실제 노출 결과 추적 가능

---

## 16. P0 추천 실행 모드

| 모드 | 동작 |
|---|---|
| `OFF` | 기존 최신순 피드만 사용 |
| `SHADOW` | 기존 응답 유지, 추천 계산 및 저장만 수행 |
| `CANARY` | 일부 사용자에게 추천 응답 |
| `LIVE` | 전체 대상 적용 |

전환 순서:

```text
OFF
→ SHADOW
→ CANARY
→ LIVE
```

각 단계는 명시적인 승인과 게이트를 통과해야 한다.

---

## 17. P0 snapshot 설계

## 17.1 snapshot 종류

| 종류 | 내용 |
|---|---|
| `RANKING_INPUT_V1` | 후보 점수 입력, 사용자, 컨텍스트, 정책 |
| `DIVERSITY_METADATA_V1` | 지역, 작성자, 테마, 중복 그룹 |
| `EXPLORATION_METADATA_V1` | 최근 노출 수, 탐색 메타데이터 |
| `RANKING_RESULT_V1` | 전체 최종 순위와 terminal 결과 |
| `EXPOSURE_EVENT_V1` | 실제 페이지 노출 결과 |

## 17.2 hash 입력

```text
journey-connect:snapshot:v1
+ snapshotKind
+ schemaVersion
+ canonicalPayloadBytes
```

hash algorithm:

```text
SHA-256
```

P0 DB v1.9의 정확한 snapshot hash 입력은 다음 byte sequence로 고정한다.

```text
UTF8("journey-connect:snapshot:v1")
+ 0x00
+ UTF8(snapshotKind)
+ 0x00
+ UTF8(schemaVersion)
+ 0x00
+ canonicalPayloadBytes
```

구분자는 문자열 `\0`이 아니라 단일 NUL byte `0x00`이다. 일반 exposure·behavior payload fingerprint는 해당 canonical payload bytes 자체의 SHA-256을 사용한다.

## 17.3 저장 형식

canonical hash 원본은 JSONB가 아니라 정확한 UTF-8 bytes로 저장한다.

```text
canonical_payload BYTEA
content_hash CHAR(64)
payload_size_bytes INTEGER
```

JSONB는 검색 및 운영 조회용 보조 필드로만 선택적으로 둔다.

P0 저장 상한은 snapshot 16 MiB, exposure event 2 MiB, behavior event 256 KiB로 고정한다. 상한 변경은 새 저장 계약 버전으로만 수행한다.

---

## 18. P0 DB 테이블

## 18.1 `recommendation_snapshot`

주요 컬럼:

- `snapshot_id`
- `snapshot_kind`
- `schema_version`
- `canonicalization_version`
- `hash_algorithm`
- `content_hash`
- `canonical_payload`
- `payload_size_bytes`
- `created_at`

규칙:

- append-only
- update 금지
- delete 금지
- 동일 kind/version/hash 중복 금지

## 18.2 `recommendation_run`

주요 컬럼:

- `run_id`
- `request_id`
- `run_mode`
- `user_id`
- `session_id`
- `surface`
- `reference_time`
- snapshot FK
- policy version vector
- exploration seed
- candidate count
- result fingerprint
- core build ID
- duration
- created_at

## 18.3 `recommendation_run_candidate`

주요 컬럼:

- `run_id`
- `absolute_rank`
- `entity_type`
- `entity_key`
- `source_entity_id`
- `origin`
- `score`
- `score_is_negative_zero`
- base/diversified rank
- exploration provenance

제약:

```text
PRIMARY KEY(run_id, absolute_rank)
UNIQUE(run_id, entity_key)
```

## 18.4 `recommendation_run_terminal_candidate`

- run ID
- entity key
- terminal reason
- validation/eligibility provenance

## 18.5 `recommendation_exposure_event`

- event ID
- idempotency key
- payload fingerprint
- run ID
- user ID
- session ID
- surface
- served at
- page rank range
- page fingerprint
- canonical payload

## 18.6 `recommendation_exposure_candidate`

- exposure event ID
- entity key
- absolute rank
- page position
- origin
- score
- provenance

## 18.7 `recommendation_behavior_event`

- event ID
- idempotency key
- payload fingerprint
- user ID
- session ID
- run ID
- event type
- entity key
- occurred at
- received at
- metadata
- created at

---

## 19. P0 identity 규칙

DB의 숫자 ID를 코어에 그대로 전달하지 않는다.

```text
post:1
crew:1
place:1
user:10
```

entity type별 namespace를 강제한다.

검증 규칙:

- prefix 필수
- source ID 필수
- 공백 금지
- 대소문자 규칙 고정
- 동일 run 내 중복 금지

---

## 20. P0 시간 규칙

기존 `LocalDateTime` 데이터는 application mapper에서 business timezone을 명시적으로 적용해 `Instant`로 변환한다.

```text
기존 DB LocalDateTime
→ Asia/Seoul 해석
→ Instant
→ UTC ISO-8601
```

신규 추천 관련 테이블은 모두 `TIMESTAMPTZ`와 Java `Instant`를 사용한다.

---

## 21. P0 candidate retrieval

초기 retrieval:

```text
published = true
entityType = POST
created_at DESC
id DESC
최대 100개
```

초기 feature:

- region
- author
- createdAt
- popularity snapshot
- recent exposure count
- 지원 가능한 명시적 theme

후보마다 별도 count query를 수행하지 않는다.

전용 projection query에서 필요한 데이터를 일괄 조회한다.

---

## 22. P0 cursor

기존 단순 Base64 cursor를 추천 피드에 재사용하지 않는다.

추천 cursor payload:

```json
{
  "version": "recommendation-feed-cursor-v1",
  "runId": "UUID",
  "nextRank": 21,
  "userRef": "opaque",
  "sessionId": "UUID",
  "expiresAt": "UTC"
}
```

보호 방식:

- HMAC 서명 또는 AES-GCM
- 사용자 바인딩
- session 바인딩
- 만료 검증
- rank 범위 검증
- run 존재 검증

---

## 23. P0 이벤트 idempotency

동일 idempotency key 처리:

```text
같은 key + 같은 payload fingerprint
→ 기존 결과 반환 또는 정상 dedupe

같은 key + 다른 payload fingerprint
→ 409 IDEMPOTENCY_CONFLICT
```

좋아요와 북마크는 기존 상태 변경 트랜잭션과 행동 이벤트 저장을 같은 트랜잭션에서 처리한다.

실제 상태가 변경되지 않은 중복 요청에는 새 행동 이벤트를 생성하지 않는다.

---

## 24. P0 fallback

추천 실행 실패 시 기존 피드로 fallback한다.

fallback 사유는 제한된 enum으로 관리한다.

예시:

- `EMPTY_PROFILE`
- `NO_ELIGIBLE_CANDIDATES`
- `CORE_EXECUTION_FAILED`
- `SNAPSHOT_PERSISTENCE_FAILED`
- `RUN_STALE`
- `FEATURE_DISABLED`

내부 예외 메시지나 stack trace는 API에 노출하지 않는다.

---

## 25. P0 완료 게이트

```text
[ ] Java core 동등성 완료
[ ] Spring에서 core 직접 호출
[ ] 후보 조회 projection 구현
[ ] snapshot canonical bytes 저장
[ ] snapshot hash 재검증
[ ] run 전체 순위 저장
[ ] 후속 페이지 재계산 없음
[ ] exposure 저장
[ ] behavior event 저장
[ ] idempotency 검증
[ ] cursor 위변조 검증
[ ] shadow 실행
[ ] canary 실행
[ ] fallback 정상
[ ] PostgreSQL 통합 테스트
[ ] persistence → replay exact match
[ ] 기존 feed 비회귀
```

---

# 26. P1 설계

## 26.1 P1 목표

P1은 추천 품질 개선 단계다.

Java 포팅의 동등성 제약에서 벗어나되, 기존 정책은 그대로 보존한다.

## 26.2 P1 주요 범위

- 행동 기반 사용자 프로필
- cold-start 개선
- 세그먼트별 정책
- surface별 diversity
- popularity bias 보정
- low-exposure candidate 보정
- 정책 선택기
- 정책 버전 병렬화
- feature vocabulary 확장
- 후보 retrieval 개선

## 26.3 정책 선택기

```java
RankingPolicy policy = policySelector.select(
    userSegment,
    surface,
    sessionContext,
    experimentAssignment
);
```

기존 계산 엔진은 정책을 입력받아 실행한다.

## 26.4 정책 버전

```text
ranking-policy-v1
ranking-policy-v2
diversity-policy-v1
diversity-policy-home-v2
exploration-policy-v1
exploration-policy-v2
```

기존 정책은 절대 수정하지 않는다.

## 26.5 행동 프로필

행동 이벤트를 바로 점수로 사용하지 않는다.

```text
raw behavior events
→ validation
→ deduplication
→ attribution
→ signal aggregation
→ decay
→ bounded user interest profile
```

프로필 생성 결과도 버전과 기준 시각을 가진 snapshot으로 관리한다.

## 26.6 P1 완료 게이트

```text
[ ] 기존 정책 replay 불변
[ ] 신규 정책 별도 버전
[ ] 행동 프로필 빌더 결정론 보장
[ ] 정책 선택 이유 추적 가능
[ ] popularity bias 지표 확인
[ ] diversity 지표 확인
[ ] segment별 회귀 확인
[ ] shadow 비교
[ ] canary 비교
[ ] rollback 가능
```

---

# 27. P2 설계

## 27.1 P2 목표

P2는 추천 개선을 통계적으로 검증하고 출시 여부를 판정하는 단계다.

## 27.2 주요 범위

- confidence interval
- bootstrap
- effect size
- segment comparison
- common support
- minimum sample threshold
- multiple comparison 보정 검토
- offline evaluation
- online experiment result
- release evidence report
- 정책 승격 및 rollback 기준

## 27.3 게이트 분리

```text
Gate A: 계산·구조 무결성
Gate B: 데이터 품질
Gate C: 증거량 충분성
Gate D: 성과 개선
Gate E: 운영 배포 승인
```

구조 검증과 성과 검증을 하나의 점수로 합치지 않는다.

## 27.4 평가 결과

평가 결과는 기존 row를 update하지 않는다.

```text
evaluation_run_001
evaluation_run_002
evaluation_run_003
```

각 실행은 다음을 기록한다.

- 평가 대상 정책
- baseline 정책
- dataset snapshot
- 기간
- segment
- metric definition version
- sample count
- effect size
- confidence interval
- gate result
- final decision
- evaluator build ID

## 27.5 P2 완료 게이트

```text
[ ] metric definition 버전 고정
[ ] dataset snapshot 고정
[ ] 재현 가능한 평가
[ ] sample threshold 충족
[ ] confidence interval 계산
[ ] effect size 계산
[ ] segment별 결과
[ ] guardrail 지표 확인
[ ] 출시/보류/rollback 판정
[ ] 보고서 생성
```

---

# 28. TypeScript 제거 조건

TypeScript는 다음 조건을 모두 만족한 후 제거하거나 archive/reference로 전환한다.

```text
[ ] Java core exact equivalence 완료
[ ] P0 shadow 결과 정상
[ ] Java replay 정상
[ ] Spring 통합 테스트 정상
[ ] PostgreSQL 통합 테스트 정상
[ ] golden fixture가 Java 기준으로 재생성 가능
[ ] Java 정책 버전 고정
[ ] TypeScript oracle 의존 제거
[ ] CI에서 TypeScript 비교가 더 이상 필수 아님
[ ] 삭제 승인 기록
```

TypeScript 제거는 Java 포팅 완료 직후가 아니라, P0 실제 통합 검증 이후에 수행한다.

P1과 P2 기간에도 참조 구현을 보관할 수 있다.

---

# 29. 권장 PR 순서

## PR 1 — 기준선 동결

- TypeScript reference 디렉터리 확정
- Git commit 고정
- 기존 테스트 실행
- golden fixture 생성기 추가
- 변경 금지 문서화

## PR 2 — Java core 골격

- Gradle 모듈
- package 구조
- 공통 record/enum
- validation
- canonical utilities
- 테스트 기반

## PR 3 — scoring 포팅

- eligibility
- interest
- context
- freshness
- popularity
- composition
- golden test

## PR 4 — ranking 포팅

- comparator
- ranking
- diversity
- exploration
- provenance
- differential test

## PR 5 — exposure·replay·evaluation 포팅

- exposure
- attribution
- replay
- offline evaluator
- fingerprint
- exact equivalence gate

## PR 6 — Java Core 1.0 고정

- 전체 테스트
- property test
- mutation test 검토
- Java core build ID
- 동등성 보고서

## PR 7 — P0 DB

- Flyway migration
- snapshot
- run
- candidate
- exposure
- behavior
- append-only trigger

## PR 8 — P0 Spring 통합

- candidate query
- mapper
- application service
- core 호출
- snapshot persistence
- shadow mode

## PR 9 — P0 API와 이벤트

- recommended feed
- cursor
- exposure
- click/view
- like/bookmark 연결
- fallback

## PR 10 — P0 canary

- 대상 사용자 설정
- 운영 진단
- replay 검증
- canary release gate

## PR 11 이후 — P1

- 프로필 빌더
- 신규 정책
- 정책 선택기
- retrieval 개선
- diversity/popularity 개선

## 후속 PR — P2

- 통계 평가
- 증거 보고서
- 출시 판정
- 정책 승격

---

# 30. CI 파이프라인

```text
1. Java compile
2. Java unit test
3. TypeScript reference test
4. Golden fixture validation
5. Differential test
6. Property test
7. Spring unit test
8. PostgreSQL integration test
9. Flyway migration test
10. Replay exact-match test
11. Security and dependency scan
12. Build artifact
```

포팅 완료 전에는 TypeScript reference test와 differential test를 필수로 둔다.

TypeScript 제거 후에는 golden fixture 및 Java replay test를 기준 게이트로 승격한다.

---

# 31. 관측성

기록할 주요 metric:

- recommendation core latency
- candidate retrieval latency
- snapshot persistence latency
- run success/failure
- fallback count
- stale run count
- exposure count
- duplicate event count
- idempotency conflict count
- no-candidate count
- policy version distribution
- personalized/exploration ratio
- replay mismatch count

로그에 기록하지 않을 것:

- raw 인증 토큰
- 민감한 사용자 입력
- 자유 텍스트 전체
- canonical payload 전체
- 내부 stack trace의 외부 응답 노출

---

# 32. 보안 원칙

- 추천 API는 인증 사용자와 run 소유권을 검증한다.
- cursor는 서명 또는 암호화한다.
- event의 user ID를 클라이언트 값만 신뢰하지 않는다.
- run에 포함되지 않은 entity 행동 이벤트는 거부한다.
- snapshot payload는 크기 제한을 둔다.
- 운영 모드 변경은 관리자 권한과 감사 로그를 요구한다.
- 내부 정책·score 세부 정보의 외부 노출 범위를 제한한다.
- replay 및 평가 데이터 접근 권한을 분리한다.

---

# 33. 완료 정의

최종 완료 상태는 다음과 같다.

```text
Java 추천 코어
├─ TypeScript 2.9b와 동등성 증명
├─ 결정론 보장
├─ replay 가능
├─ 정책 버전 보존
└─ Spring 의존성 없음

Journey Connect 백엔드
├─ 실제 후보 조회
├─ Java core 직접 호출
├─ snapshot/run/event 저장
├─ shadow/canary/live 전환
├─ 안정적인 pagination
├─ 장애 fallback
└─ PostgreSQL 통합 검증

추천 고도화
├─ P1 정책 개선
├─ P2 통계 검증
├─ 출시 판정
└─ rollback 가능
```

---

# 34. 최종 의사결정

1. 추천 알고리즘은 Java 21로 전수 재구현한다.
2. TypeScript 구현은 Java 동등성 검증용 참조 오라클로 동결한다.
3. Java 동등성 확보 전 P0·P1·P2 기능을 섞지 않는다.
4. Java core 동등성 완료 후 P0를 진행한다.
5. P0 완료 후 P1을 진행한다.
6. P1의 실제 정책과 데이터가 준비된 후 P2를 진행한다.
7. TypeScript 삭제는 P0 실제 통합 검증 이후에만 허용한다.
8. 기존 Phase 3~4b와 알고리즘 보강 버전은 별도 개념으로 유지한다.
9. 기존 정책, snapshot, replay, 평가 결과는 덮어쓰지 않는다.
10. 모든 변경은 회귀 테스트와 재현 증거를 통해 승인한다.

---

## 부록 A. 전체 순서 요약

```text
TypeScript 2.9b 동결
        ↓
Java Core 골격
        ↓
Java 전수 포팅
        ↓
Golden + Differential + Replay 동등성
        ↓
Java Core 1.0 고정
        ↓
P0: DB·이벤트·실제 프로젝트 통합
        ↓
Shadow
        ↓
Canary
        ↓
P1: 프로필·정책·품질 개선
        ↓
P2: 통계 검증·출시 판정
        ↓
Java 단독 운영
        ↓
TypeScript 제거 또는 archive
```

## 부록 B. 절대 금지 목록

- TypeScript 기준 구현 선삭제
- 포팅 중 가중치 변경
- 기존 정책 상수 덮어쓰기
- DB/JPA 객체를 core에 직접 전달
- core 내부 DB/HTTP 호출
- 현재 시각 직접 조회
- 비결정적 난수 사용
- 페이지마다 재랭킹
- 기존 snapshot 수정
- 과거 evaluation row 수정
- 테스트 실패 후 기대값 일괄 갱신
- Java/TypeScript 차이를 무조건 epsilon으로 은폐
- run 소유권 검증 없는 cursor 사용
- 자유 텍스트를 추천 trace에 무제한 저장
