# IP-3 Search Domain Types & Validation

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 단계 ID | `IP-3` |
| 단계명 | `Search Domain Types & Validation` |
| 계약 ID | `jc-search-domain-types-validation-v1` |
| 상태 | `COMPLETE / CONTRACT_IMPLEMENTATION_ONLY` |
| 소유 | Intelligence Platform / Search Intelligence |
| 기준 계약 | IP-2 Search Contract Foundation |
| Search runtime | `NOT IMPLEMENTED` |
| Search persistence | `NOT IMPLEMENTED` |
| Search API cutover | `NOT STARTED` |
| DB/SQL | `UNCHANGED` |

## 2. 목적과 보호 기준선

IP-3는 IP-2에서 고정한 Search 계약을 dependency-free Java 타입, validator, canonicalizer, contract cursor 및 fixture/test로 구현한다. Controller, Service, Repository, ranking runtime, persistence 또는 API 동작은 구현하지 않는다.

보호 기준선:

- Recommendation P0/P1: `CLOSED`
- Recommendation P2 technical: `CLOSED`
- Recommendation P2 production: `HOLD`
- SC-1/DP-0/IP-0: `COMPLETE`
- IP-1/IP-1.10: `CLOSED`
- IP-2: `COMPLETE / CONTRACT_ONLY`
- protected source: 기존 320개 manifest 유지
- canonical SQL: `journey-connect-db-v2.7/01..26`

## 3. 확인한 현재 구조

- 기존 공통 모듈: `jc-intelligence-contracts`
- 신규 Search 모듈: `jc-search-contracts`
- 승인 package root: `com.jc.intelligence.contract`
- 신규 Search package: `com.jc.intelligence.contract.v1.search`
- 기존 IP-1 common contract와 dependency-free JSON wire/parser를 재사용
- `jc-intelligence-contracts`는 backend, recommendation core, Spring, JPA, HTTP, DB, system clock, environment에 의존하지 않음
- 기존 `/api/v1/explore` 및 backend 검색 유사 구현은 수정하지 않음

의존 방향:

```text
future backend/search runtime
        ↓
jc-search-contracts / v1.search
        ↓
jc-intelligence-contracts / IP-1 common types
        ↓
IP-1 common contract value objects

v1.search
  ✕ recommendation package/core
  ✕ Spring/JPA/DB/HTTP
  ✕ controller/service/repository
```

## 4. 단계별 작업 기록

### IP-3.1 Baseline & Dependency Inventory

- 목적: IP-2 문서, Gradle module, IP-1 common types, 보호 manifest 확인
- 변경 파일: 없음
- 확인 내용: Search 구현은 별도 `jc-search-contracts` 모듈에 격리하고 IP-1 common module에만 의존 가능
- 검증: IP-2 문서 8개 및 System/Track/Architecture 기준 대조
- 보완: Search 전용 package가 Recommendation 타입을 import하지 않도록 isolation gate 정의
- 잔여 리스크: Gradle 8.14.5 배포판은 실행 환경 DNS 차단으로 다운로드 불가

### IP-3.2 Domain Types & Enums

- 목적: IP-2 논리 객체를 immutable Java record/enum으로 고정
- 변경 파일: `jc-search-contracts/src/main/java/.../v1/search/**`
- 구현:
  - `SearchRequestV1`, `SearchQueryV1`, `SearchContextV1`, `SearchFilterV1`, `SearchSortV1`, `SearchPageRequestV1`
  - `SearchCursorV1`, `SearchOrderingTupleV1`
  - `SearchRunV1`
  - `RetrievalRequestV1`, `RetrievalCandidateV1`
  - `SearchExplanationV1`, `SearchFailureV1`, `SearchFallbackV1`
  - Search surface/scope/entity/failure/fallback/sort/visibility/eligibility/filter/retrieval wire enums
- 검증: Java 21 `-Xlint:all -Werror` compile PASS
- 보완: `ReplayClass`와 `ExplanationAudience`는 IP-1 common enum을 사용하여 중복 enum 생성 방지
- 잔여 리스크: runtime ranking/result snapshot type은 IP-5 이후 범위

### IP-3.3 Validation & Builder

- 목적: invalid state가 record/builder 경계를 통과하지 못하도록 고정
- 구현:
  - stable `SearchValidationErrorCode`
  - null/reference/fingerprint/rank/finite score/time/version checks
  - exact request/filter/query-normalization/cursor schema validation
  - text query blank/length/unsupported character validation
  - filter canonical ordering/dedupe/single-value conflict validation
  - sort/scope compatibility validation
  - run status/fallback/failure/replay consistency
  - `SearchRequestV1.Builder`
- 검증: invalid/boundary/null-safety contract test PASS
- 보완: request/session identifiers는 System Contract에 따라 namespaced ref뿐 아니라 UUID형 opaque ID도 허용
- 잔여 리스크: Operations visibility port 자체는 미구현

### IP-3.4 Query Canonicalizer & Fingerprints

- 목적: raw query와 normalized query를 분리하고 deterministic V1 fingerprint 생성
- pipeline:
  1. unpaired surrogate/forbidden code point 검증
  2. Unicode whitespace 통합
  3. trim/collapse
  4. Unicode NFKC
  5. `Locale.ROOT` lower-case
  6. code point/UTF-8 재검증
  7. domain-separated SHA-256 fingerprint
- 구현 contract:
  - normalization version: `search-query-normalization-v1`
  - query fingerprint domain: `journey-connect:search-query-fingerprint:v1`
  - filter fingerprint domain: `journey-connect:search-filter-fingerprint:v1`
- 불변조건: direct JSON construction도 original → normalized → fingerprint exact match를 요구
- 검증: canonical equivalent query fingerprint equality PASS
- 보완: browse는 blank string을 암묵적으로 변환하지 않고 query absence만 허용
- 잔여 리스크: 초성/형태소/오타/동의어 확장은 미구현

### IP-3.5 Contract Cursor

- 목적: offset wrapper가 아닌 snapshot-bound cursor payload와 codec 고정
- 필드:
  - cursor version
  - search run/result snapshot
  - query/filter fingerprint
  - sort/ranking policy version
  - reference time
  - next 1-based rank
  - complete ordering tuple
  - surface/scope/subject/session binding
  - issue/expiry time
  - structural checksum
- 구현:
  - canonical JSON + Base64URL codec
  - deterministic SHA-256 checksum
  - binding/expiry/checksum validator
- 제한:
  - checksum은 fixture/transport corruption 검출용 구조적 무결성이다.
  - HMAC/AEAD, production key, rotation, security owner는 구현하지 않았다.
- 검증: serialize/deserialize/binding/mismatch/expiry/checksum PASS
- 보완: referenceTime binding과 expiry validation을 분리하여 system clock 의존 제거
- 잔여 리스크: production cursor security는 후속 승인 필요

### IP-3.6 JSON Fixtures & Contract Test

- 생성 fixture 8개:
  - valid/invalid request
  - cursor
  - normalized query
  - failure
  - fallback
  - retrieval candidate
  - search run
- 구현 codec: camelCase JSON, lowercase_snake_case enum, UTC Instant, unknown optional field 허용, unknown required enum fail-closed
- 신규 test task: `searchDomainContractTest`
- 직접 실행 결과: Search 425 assertions PASS
- 기존 IP-1 contract test: 739 assertions PASS (exact baseline maintained)
- 보완: synthetic fixture에 token/key/password 등 민감 정보 부재 검사 추가
- 잔여 리스크: Gradle wrapper task는 distribution 다운로드 차단으로 직접 실행하지 못함

### IP-3.7 Protection & Final Review

- protected source manifest: 320/320 exact
- canonical SQL manifest: 26/26 exact
- backend/recommendation production source: 변경 없음
- 신규 DB/SQL: 없음
- Recommendation policy/candidate/exposure: 참조·수정 없음

## 5. 타입 및 의미 경계

| Search 타입 | 공통 계약 연결 | 금지된 결합 |
|---|---|---|
| `SearchRunV1` | `IntelligenceRunV1` read-only mapping | Recommendation run 의미 재사용 |
| `SearchQueryV1` | input evidence extension | raw query logging |
| `RetrievalCandidateV1` | future candidate snapshot extension | Recommendation candidate/score scale 재사용 |
| `SearchExplanationV1` | `IntelligenceExplanationV1` mapping | private score/debug 노출 |
| `SearchCursorV1` | Search domain only | feed cursor/offset wrapper 재사용 |
| Search failure/fallback | common terminal status에 mapping | fallback을 succeeded로 위장 |

`search_exposure_v1`은 reserved semantic ID만 참조하며 persistence writer/table/event는 구현하지 않았다.

## 6. 자체 리뷰

### 리뷰 1 — 계약·아키텍처

발견 7 / 수정 7 / 보류 0

1. Search를 common module에 직접 추가하면 IP-1 739 assertion baseline 변동 → `jc-search-contracts`로 격리
2. cursor referenceTime과 expiry validation 혼합 → binding/expiration 분리
3. checksum이 object 불변조건이 아님 → constructor exact checksum 검증
4. request/filter schema version이 임의 version 허용 → V1 exact schema 고정
5. fallback evidence/order reference가 nullable → 필수화
6. explanation reason/evidence/attribute 및 user-sensitive detail 검증 부족 → 강화
7. exposure helper 이름이 persistence 활성화를 암시 → `reservedExposureSourceId`로 수정

### 리뷰 2 — 구현 품질·호환성·보안

발견 5 / 수정 5 / 보류 0

1. request/session ID가 namespaced ref로 과도하게 제한 → UUID-compatible opaque ID 허용
2. filter value Unicode control/whitespace validation 부족 → scalar 검증과 code-point whitespace normalization 추가
3. explanation attribute collection order 불명확 → immutable insertion-order map 유지
4. RetrievalRequest/UUID/control-character 경계 test 부족 → contract test 추가
5. Search module 분리 후 test fixture/source fallback 경로가 기존 common module을 가리킴 → `jc-search-contracts` 경로로 수정

보완 후 main/test compile 및 Search/IP-1 contract test 재실행 PASS.

## 7. 검증 명령과 결과

실행:

```text
javac --release 21 -Xlint:all -Werror [all contract main sources]
javac --release 21 -Xlint:all -Werror -cp [main classes] [all contract tests]
java ... SearchDomainContractsContractTest
java ... IntelligenceContractsContractTest (common module source tree only)
sha256sum -c verification/ip2/IP2_PROTECTED_BASELINE_CURRENT_SHA256.txt
sha256sum -c verification/ip2/IP2_SQL_01_26_SHA256.txt
```

결과:

- Domain/contract compile: PASS
- Search contract test: 425 assertions PASS
- Existing common contract regression: 739 assertions PASS
- fixtures: 8/8 load/expected-invalid PASS
- protected source: 320/320 exact
- SQL 01..26: 26/26 exact
- forbidden runtime/recommendation dependency scan: PASS
- wire enum lowercase_snake_case: PASS
- Gradle wrapper: NOT RUN — `services.gradle.org` DNS unavailable

## 8. 완료 상태

```text
IP-3: COMPLETE
Search Runtime: NOT IMPLEMENTED
Search Persistence: NOT IMPLEMENTED
Search API Cutover: NOT STARTED
Protected Baseline: MAINTAINED
```

## 9. 잔여 리스크와 IP-4 gate

- production HMAC/AEAD cursor key owner/rotation 미정
- existing `/api/v1/explore` read adapter 입력·출력 mapping 미구현
- Operations visibility dependency 실제 연결 미구현
- ranking/retrieval runtime 및 result snapshot persistence 미구현
- search exposure persistence 미구현
- Gradle distribution 접근 가능한 환경에서 `searchDomainContractTest` task 실행 필요

IP-4는 기존 검색 유사 경로를 변경하지 않는 read-only compatibility inventory/adapter 범위로만 진입한다.
