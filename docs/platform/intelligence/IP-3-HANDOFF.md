# IP-3 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-3-handoff-v1` |
| IP-3 | `COMPLETE` |
| 다음 후보 | `IP-4 Existing Search Read Adapter / Compatibility` |
| Search Runtime | `NOT IMPLEMENTED` |
| Search Persistence | `NOT IMPLEMENTED` |
| Search API Cutover | `NOT STARTED` |
| Protected Baseline | `MAINTAINED` |

## 1. 완료

- Search domain immutable Java records/enums
- query/filter/sort/request/context/page validation
- raw/normalized query canonicalizer와 fingerprint
- snapshot-bound contract cursor, JSON/Base64URL codec, checksum/binding/expiry validation
- retrieval request/candidate contracts
- SearchRun → common IntelligenceRun mapping
- explanation/failure/fallback contracts
- JSON fixture 8개
- Search 전용 contract test task와 425 assertions
- 기존 IP-1 common contract 739 assertions exact regression
- 2회 자체 리뷰 및 보완
- protected source/SQL exact comparison

## 2. 생성·수정 범위

- 신규: `jc-search-contracts/src/main/java/com/jc/intelligence/contract/v1/search/**`
- 신규: `jc-search-contracts/src/test/java/com/jc/intelligence/contract/search/**`
- 신규: `jc-search-contracts/src/test/resources/search/**`
- 신규: `jc-search-contracts/build.gradle.kts` — common module dependency 및 `searchDomainContractTest` task
- 수정: `jc-backend/settings.gradle.kts` — Search contract module 등록
- 신규 문서: IP-3 본문, IP-3 Handoff
- 수정 문서: Intelligence README index/status

기존 backend, recommendation core, canonical SQL은 수정하지 않았다.

## 3. 검증

| 대상 | 결과 |
|---|---|
| Java 21 main compile | PASS, `-Xlint:all -Werror` |
| Java 21 test compile | PASS, `-Xlint:all -Werror` |
| Search contract test | 425 assertions PASS |
| IP-1 common contract regression | 739 assertions PASS |
| fixture load/round-trip/invalid | PASS |
| cursor serialize/deserialize/checksum | PASS |
| canonicalizer/fingerprint | PASS |
| forbidden dependency scan | PASS |
| protected source | 320/320 exact |
| canonical SQL | 26/26 exact |
| Gradle wrapper | BLOCKED — Gradle 8.14.5 distribution DNS 차단; PASS 미선언 |

## 4. 자체 리뷰

- 1차: 7 발견 / 7 수정 / 0 보류
- 2차: 5 발견 / 5 수정 / 0 보류
- 보완 후 재검증: PASS

## 5. 보호 결과

- Recommendation production source: unchanged
- Recommendation core: unchanged
- Recommendation candidate/comparator/exposure: not imported or reused
- DB migration/new SQL: none
- canonical SQL 01..26: unchanged
- P2 exposure authority/metric: unchanged
- Data shadow cutover: none
- identity join: none

## 6. IP-4 진입 판정

`READY — READ-ONLY COMPATIBILITY SCOPE`

허용:

- current `/api/v1/explore` 및 region lookup의 immutable read model inventory
- legacy request/result/page → Search contract read-only adapter
- compatibility classification fixture/test
- production 동작을 호출하지 않는 adapter contract test

HOLD:

- Controller/Service/Repository 수정
- Search runtime/ranking 실행
- DB/index/provider/persistence
- production cursor security/key
- Search exposure persistence
- API cutover
