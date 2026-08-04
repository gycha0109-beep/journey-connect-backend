# SR-6B Search Exposure Java Contracts

## 상태

```text
Stage: SR-6B
Contract: search-exposure-v1
Java contract implementation: COMPLETE
Runtime endpoint: NOT_IMPLEMENTED
Persistence writer: DISABLED_PENDING_APPROVAL
Database/Flyway/canonical SQL: UNCHANGED
Local contract verification: 9/9 PASS
GitHub Actions: PENDING
Overall: IMPLEMENTED_UNVERIFIED
```

## 목적

SR-6 설계에서 승인 전 구현 가능한 범위만 Java 계약으로 고정한다.

- request/domain type
- actor·result context·item binding validator
- visibility candidate policy
- deterministic canonical payload와 SHA-256 fingerprint
- golden fixture
- persistence port와 명시적 disabled adapter
- contract tests

실제 `/api/v1/search/exposures`, Spring bean wiring, identity mapping adapter, DB table·writer는 추가하지 않는다.

## 구현 파일

```text
jc-backend/src/main/java/com/jc/backend/intelligence/search/
├─ SearchExposureContract.java
├─ SearchExposureDtos.java
├─ SearchExposureActor.java
├─ SearchExposureCommand.java
├─ SearchExposureValidationPolicy.java
├─ SearchExposureValidator.java
├─ SearchExposureCanonicalizer.java
└─ SearchExposurePersistencePort.java
```

테스트:

```text
jc-backend/src/test/java/com/jc/backend/intelligence/search/
├─ SearchExposureValidatorTest.java
├─ SearchExposureCanonicalizerTest.java
└─ SearchExposurePersistencePortTest.java

jc-backend/src/test/resources/intelligence/search/
└─ search-exposure-canonical-v1.json
```

## 요청 계약

`SearchExposureDtos.BatchRequest`는 향후 batch endpoint가 사용할 transport contract다.

```text
pageOccurrenceId
resultContextToken
visibilityRuleVersion
producerBuildId
items[1..100]
```

item:

```text
exposureId
idempotencyKey
postId
absoluteRank
pagePosition
visibleRatioBasisPoints
dwellMilliseconds
exposedAt
```

사용자 numeric ID, subject, session은 client request에서 받지 않는다. `SearchExposureActor`는 향후 승인된 identity mapping과 JWT session derivation 결과를 server 내부에서 전달하기 위한 domain input이다.

## 검증 경계

`SearchExposureValidator`는 다음을 fail-closed로 검증한다.

- authenticated numeric user는 signed result context 검증에만 사용
- identity scheme은 `platform_subject_v1`만 허용
- `subject:<opaque-id>` 형식
- numeric identity 또는 `legacy_user_numeric_v1` fallback 거절
- result context 서명·사용자·TTL
- context의 `postId + absoluteRank` binding
- context page order와 `pagePosition` exact binding
- batch 내부 exposure ID·idempotency key·page position 중복 거절
- candidate visibility rule version
- viewport ratio 5,000 basis points 이상
- dwell 1,000ms 이상
- result context 발급·만료 시각과 노출 시각 결속
- 미래 시각 skew 제한

후보 rule:

```text
search-item-visible-v1
visible ratio >= 50%
continuous dwell >= 1,000ms
```

이 rule은 production ACTIVE가 아니라 SR-6 설계 후보다.

## Canonical payload

`SearchExposureCanonicalizer`는 batch item을 page position 기준으로 정렬한 뒤 기존 `RecommendationCanonicalPayload` canonical JSON encoder를 재사용한다.

생성 결과:

- batch canonical UTF-8 bytes
- batch SHA-256 fingerprint
- item별 canonical UTF-8 bytes
- item별 SHA-256 fingerprint
- deterministic JSON

privacy boundary:

- raw query 미포함
- numeric `userId` 미포함
- JWT 미포함
- precise location 미포함
- `queryFingerprint`, opaque `subjectRef`, server-derived session만 포함

golden batch fingerprint:

```text
14879159c3b4f671ac25de0f18af96f920c39cf99fd5e7e1ef66e21239d53293
```

입력 item 순서가 달라도 canonical batch는 동일하고, dwell·rank·position 등 evidence가 변경되면 fingerprint가 변경된다.

## Persistence port

`SearchExposurePersistencePort`는 향후 physical writer가 구현할 boundary다.

현재 제공되는 adapter:

```text
disabledPendingApproval()
→ DISABLED_PENDING_APPROVAL
→ storedCount=0
→ duplicateCount=0
```

승인 전 write 성공이나 duplicate 저장을 위장하지 않는다. Spring component로 등록되지 않으며 runtime에서 사용되지 않는다.

## DB·런타임 영향

없음.

- Flyway 변경 없음
- canonical SQL 변경 없음
- table/index/role/grant 없음
- JPA entity/repository 없음
- controller/service endpoint 없음
- Search behavior store 변경 없음
- recommendation general exposure 변경 없음
- P2 experiment exposure 변경 없음
- frontend `IMPRESSION` 활성화 없음

## 테스트

로컬 Java 21 독립 검증:

```text
main + test source compile: PASS
validator contract: 5 PASS
canonicalizer contract: 3 PASS
disabled persistence port: 1 PASS
total: 9/9 PASS
```

검증 시나리오:

- valid actor/context/item/page binding
- wrong user context 거절
- wrong rank/page position 거절
- unsupported rule·threshold 거절
- duplicate exposure occurrence identity 거절
- result context 이전 시각 거절
- numeric identity fallback 거절
- golden canonical payload·fingerprint
- input item ordering independence
- evidence 변경 fingerprint change
- disabled port zero-write result

GitHub Actions의 focused Search test와 full backend regression을 통과한 뒤 단계 상태를 `VERIFIED`로 전환할 수 있다.

## 다음 승인 게이트

SR-6C로 진행하려면 다음이 필요하다.

- System Coordination의 identity mapping owner 승인
- target DB version과 canonical SQL sequence 배정
- application writer role/grant 승인
- Data의 retention·deletion 결정
- Operations/Reliability의 visibility rule 활성 승인

그 전에는 executable DDL, runtime writer, endpoint, frontend impression을 추가하지 않는다.
