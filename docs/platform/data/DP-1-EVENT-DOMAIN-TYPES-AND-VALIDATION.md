# DP-1 Event Domain Types & Validation

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `DP-1` |
| 계약 ID | `dp-1-event-domain-types-validation-v1` |
| 상태 | `IMPLEMENTATION COMPLETE / FINGERPRINT ALGORITHM BLOCKED` |
| 소유 트랙 | Data Platform |
| 기준 브랜치 | `main` |
| 공식 DP-1 Baseline SHA | `9d84f630e87d54f780e332eead0c1f8df6a51d0b` |
| 작업 시작 HEAD | `e8cd8e434e0eac75de561417ae4eb5a4f73e448b` |
| 작업 브랜치 | `codex/dp-1-event-contracts` |
| Java | `21` |
| DB/SQL 영향 | 없음 |
| runtime/API 영향 | 없음 |

## 2. 목적

DP-0에서 확정된 이벤트, identity, version, validation, canonicalization, fingerprint 및 idempotency 경계를 production DB나 runtime에 연결하지 않는 독립 Java contract module로 구현한다.

이번 단계는 다음 신뢰 경계를 코드로 고정한다.

```text
ClientEventCommandV1 [untrusted intent]
        ↓ validation / authoritative resolution outside DP-1
PlatformEventEnvelopeV1 [canonical evidence contract]
        ↓ structural canonicalization boundary
Fingerprint boundary [SC-DP1-009 unresolved: fail closed]
        ↓
Idempotency pure comparison [no persistence]
```

## 3. Preflight

- 기본 브랜치: `main`
- 공식 baseline `9d84f630...`은 current main history에 포함됨
- 작업 시작 HEAD `e8cd8e4...`는 baseline보다 2 commits 앞섬
- baseline 이후 diff는 System Contract 및 SC handoff 문서 갱신만 존재
- PR #3 및 PR #4 merged 상태 확인
- current module registry에서 `jc-data-contracts` / `com.jc.data.contract` 예약 확인
- 기존 Java contract module은 Java 21, `java-library`, `-Xlint:all`, `-Werror`, dependency-free JavaExec contract runner를 사용
- `SC-DP1-009` fingerprint algorithm, encoding, wire ID, exact inclusion set은 미승인 상태 확인
- canonical DB 기준은 `journey-connect-db-v2.7/01..28`; DP-1은 SQL을 변경하지 않음

## 4. 변경 범위

### 4.1 신규 module

```text
jc-data-contracts
└─ com.jc.data.contract.v1
   ├─ command
   ├─ event
   ├─ identity
   ├─ version
   ├─ validation
   ├─ canonical
   ├─ fingerprint
   ├─ idempotency
   └─ compatibility
```

특성:

- Java 21 pure `java-library`
- Spring/JPA/JDBC/backend runtime 의존성 없음
- 외부 JSON dependency 없음
- immutable record/value object 중심
- `-Xlint:all -Werror`
- explicit wire values, enum ordinal serialization 없음

### 4.2 구현 계약

| 영역 | 구현 |
|---|---|
| command | `ClientEventCommandV1` |
| canonical event | `PlatformEventEnvelopeV1` |
| taxonomy | `EventFamily`, `EventType`, `EventTaxonomyRegistryV1` |
| identity | event/session/request/correlation/causation/entity/subject/actor/idempotency refs |
| versions | contract/schema/canonicalization/producer/consumer/build value objects |
| validation | stable error code, `ValidationError`, `ValidationResult`, command/envelope validator |
| compatibility | 6개 compatibility classification과 fail-closed evaluator |
| canonicalization | `platform-event-canonical-json-v1` structural normalizer |
| fingerprint | unresolved boundary와 fail-closed result만 구현 |
| idempotency | `NEW`, `DUPLICATE`, `CONFLICT` pure comparison |

## 5. 신뢰 경계

### 5.1 Client command

`ClientEventCommandV1`은 canonical authority가 아니다. 다음 값은 타입에 존재하지 않거나 canonical envelope에서만 생성된다.

- canonical event ID
- canonical actor/subject
- received time
- authoritative event family
- canonical fingerprint
- producer metadata
- lineage/replay metadata

Client command의 `requestedEventType`, entity candidate, occurred time, opaque session token, idempotency key와 bounded context는 untrusted validation 대상이다.

### 5.2 Canonical envelope

`PlatformEventEnvelopeV1`은 다음을 명시적으로 분리한다.

- `contractVersion`
- `schemaVersion`
- `canonicalizationVersion`
- `producerVersion`
- `producerBuildId`
- canonical IDs/refs
- `occurredAt` / `receivedAt`
- immutable payload

`subject:<opaque-id>`와 `user:<numeric-id>`는 별도 identity scheme이다. `ActorRef`는 canonical Data envelope에서 `platform_subject_v1`만 허용하며 자동 변환·fallback·join은 구현하지 않는다.

## 6. Event taxonomy

`user_behavior` registry에는 21개 canonical event type을 등록했다.

```text
post_impression
post_view
post_dwell
post_like
post_unlike
post_bookmark
post_unbookmark
post_share
post_hide
post_report
search_submit
search_result_impression
search_result_click
recommendation_impression
recommendation_click
profile_view
follow
unfollow
tag_click
crew_join
crew_leave
```

각 type은 required/optional payload allowlist와 entity requirement를 가진다. registry completeness, duplicate enum wire value, lowercase snake_case, family/type combination을 contract test에서 검증한다.

P0 wire value는 source에서 변경하지 않고 fixture 및 adapter candidate mapping으로만 보호한다.

## 7. Validation

Stable error taxonomy는 다음 범위를 포함한다.

- missing/blank/malformed ID
- identity namespace missing/unsupported/mismatch
- raw numeric identity 금지
- contract/schema/canonicalization/producer/consumer/build version 오류
- invalid/non-UTC timestamp
- invalid family/type 및 family/type 조합
- invalid/forbidden payload
- unknown required enum/schema fail closed
- duplicate contract/taxonomy registry 오류
- idempotency key/conflict
- canonicalization/fingerprint failure
- unresolved fingerprint contract

Payload validation은 bounded depth, field count, array size, string length, estimated size와 secret/token/raw identity/free text/precise location key denylist를 적용한다.

## 8. Canonicalization

`CanonicalJsonNormalizerV1`은 승인된 structural 규칙만 구현한다.

- UTF-8
- lexical object-key ordering
- array ordering 보존
- locale-independent finite number representation
- `Instant` UTC `Z`
- explicit null과 absent 구분
- empty array/object와 missing 구분
- whitespace/Map insertion order 독립
- forbidden field fail closed
- unpaired surrogate 차단

Canonicalization request는 호출자가 승인된 inclusion set을 제공하는 boundary다. DP-1은 미승인 field inclusion set을 임의로 선택하지 않는다.

## 9. Fingerprint

`SC-DP1-009`가 다음을 확정하지 않았다.

- algorithm
- output encoding
- fingerprint version wire ID
- exact inclusion set
- `occurredAt`, `receivedAt`, `producerBuildId` 포함 여부

따라서 concrete hash implementation은 존재하지 않는다.

구현된 범위:

- `EventFingerprintBoundaryV1`
- `FingerprintRequestV1`
- `FingerprintResultV1`
- `FingerprintStatus`
- `UnresolvedEventFingerprintBoundaryV1`

호출 결과는 항상 `UNRESOLVED_CONTRACT`와 `FINGERPRINT_CONTRACT_UNRESOLVED`를 반환하며 fingerprint value를 생성하지 않는다. 기존 P0 SHA/fingerprint는 복사하거나 호출하지 않는다.

## 10. Idempotency

순수 비교 계약:

```text
same key + same fingerprint
→ DUPLICATE + existing event reference

same key + different fingerprint
→ CONFLICT + IDEMPOTENCY_CONFLICT

different key
→ NEW
```

DB unique constraint, atomic compare, transaction, TTL, concurrent ingestion은 DP-2 책임이다.

## 11. Compatibility fixtures

| Fixture | 보호 내용 |
|---|---|
| `p0-recommendation-behavior-wires-v1.tsv` | P0 16개 behavior wire와 Data mapping candidate |
| `p1-identity-version-compatibility-v1.tsv` | opaque/legacy identity 분리, mapping 금지, shadow-only source |
| `p2-authority-compatibility-v1.tsv` | P2 exposure authority, dataset/metric 의미, shadow-only bridge |
| valid command/event JSON | valid ingress/canonical shape |
| forbidden payload JSON | secret/token field rejection |

P2 `engagement_rate` 포함값 `click,like,save,share`와 제외값 `view,impression,hide,report`, P2 experiment exposure authority, 기존 evaluation dataset authority를 fixture로 고정했다.

## 12. Verification

### 12.1 직접 실행

```text
javac --release 21 -Xlint:all -Werror [main sources]
javac --release 21 -Xlint:all -Werror [test sources]
java com.jc.data.contract.DataContractsContractTest
```

결과:

```text
DP-1 data contract checks passed: 569
```

### 12.2 검증 범위

- Java compile: PASS
- test compile: PASS
- `-Xlint:all -Werror`: PASS
- valid/invalid fixture: PASS
- taxonomy completeness/duplicate: PASS
- contract ID/version rules: PASS
- UTC/offsetless validation: PASS
- identity automatic conversion rejection: PASS
- unknown required enum/schema fail closed: PASS
- forbidden payload rejection: PASS
- deterministic repeated canonical output: PASS
- Map insertion order independence: PASS
- locale/timezone independence: PASS
- null/missing/empty distinction: PASS
- idempotency duplicate/conflict: PASS
- unresolved fingerprint fail closed: PASS
- Spring/JPA/JDBC/runtime dependency isolation: PASS
- guessed fingerprint algorithm/encoding absence: PASS

Gradle module check와 repository-wide protected regression은 PR CI에서 exact branch HEAD를 기준으로 수행한다. 실행 전에는 PASS로 기록하지 않는다.

## 13. 비회귀 경계

변경하지 않는다.

- canonical SQL `01..28`
- recommendation core/backend/P2 runtime
- Intelligence contract module
- Search modules/runtime
- IP-12/IP-12.5 controls/evidence
- production config
- `/api/v1/explore` authority
- production shadow, kill switch, sampling, cohort, Search cutover
- P1/P2 source authority 및 metric/exposure 의미

## 14. 발견 및 보완

1. 최초 compile에서 validator가 참조하는 `INVALID_ENTITY_REF`, `IDENTITY_NAMESPACE_MISMATCH`가 stable enum에 누락된 것을 발견했다.
2. 두 코드를 error registry에 추가하고 전체 warning gate를 재실행했다.
3. 자체 리뷰에서 Client command context에 canonical server-derived payload 필드를 요구하던 신뢰 경계 결합을 발견해 제거했다. Canonical payload shape는 envelope validator에서만 강제한다.
4. raw version/reference/event wire validator와 contract ID registry를 추가해 generic constructor exception 이전에 stable error code를 제공한다.
5. 최종 569 assertions를 재실행해 PASS했다.
6. fingerprint package에서 digest/encoding 상수와 `MessageDigest`, `Base64` 사용을 금지하는 static contract test를 추가했다.
7. canonicalization은 approved-field map을 정규화하지만 field inclusion authority를 갖지 않도록 분리했다.

## 15. 잔여 리스크와 다음 단계

- `SC-DP1-009` 승인 전 concrete fingerprint algorithm 구현 금지
- identity mapping physical owner/deletion policy는 미결정이며 DP-1에서 구현하지 않음
- Gradle/CI 및 repository-wide P0/P1/P2/IP-12.5 비회귀 결과는 exact PR HEAD evidence로 종결 필요
- DP-2는 SC가 SQL `29+` range와 DB target을 배정하기 전 시작 금지

최종 판정:

```text
DP1_IMPLEMENTATION_COMPLETE_WITH_BLOCKED_FINGERPRINT_ALGORITHM
```
