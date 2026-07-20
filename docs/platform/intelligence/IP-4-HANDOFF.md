# IP-4 Handoff

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `ip-4-handoff-v1` |
| IP-4 | `COMPLETE` |
| Legacy Explore Compatibility Adapter | `IMPLEMENTED / READ_ONLY` |
| Search Runtime/Ranking/Persistence | `NOT IMPLEMENTED` |
| Search Exposure Persistence | `NOT IMPLEMENTED` |
| Search API Cutover | `NOT STARTED` |
| Legacy Explore Behavior | `UNCHANGED` |
| Protected Baseline | `MAINTAINED` |

## 완료

- 실제 `/api/v1/explore` Controller/Service/Repository/DTO/security/page inventory
- 순수 Java `jc-search-compatibility` module
- legacy request/page/item/author mirror read model
- request/result/page mapper와 orchestration adapter
- no-fake-cursor/no-fake-run/no-exposure-authority 불변조건
- typed invalid/unsupported/mapping failure
- memory-only compatibility evidence와 deterministic fingerprints
- 16 fixtures
- compatibility contract test 584 assertions
- IP-3 425, IP-1 739 assertion regression
- 2회 자체 리뷰와 보완

## 변경 범위

- 신규: `jc-search-compatibility/**`
- 수정: `jc-backend/settings.gradle.kts` — module 등록만
- 신규 문서: IP-4 본문/Handoff
- 수정 문서: Intelligence README
- 신규 verification: `verification/ip4/**`

기존 Controller/Service/Repository/DTO/JPQL, recommendation source, SQL은 수정하지 않았다.

## compatibility 핵심

- `SearchQueryV1`, `SearchFilterV1`, `SearchContextV1`, `SearchSortV1`은 의미가 확인되는 범위에서만 사용
- `SearchRequestV1`, `SearchRunV1`, `SearchCursorV1`, `RetrievalCandidateV1`, `search_exposure_v1` authority는 생성하지 않음
- page/item count와 source order 보존
- ID는 boundary에서만 `post:<id>`
- score/snapshot/final rank/retrievedAt 없음
- visibility/eligibility는 payload evidence 부재로 `unknown`

## 검증

| 항목 | 결과 |
|---|---|
| main/test compile | PASS (`-Xlint:all -Werror`) |
| compatibility | 584 PASS |
| IP-3 | 425 PASS |
| IP-1 | 739 PASS |
| fixture | 16/16 PASS |
| protected source | 320/320 exact |
| SQL 01..26 | 26/26 exact |
| production source | unchanged |
| Gradle | `services.gradle.org` DNS 실패; PASS 미선언 |

## 자체 리뷰

- 1차: 8 발견 / 8 수정 / 0 보류
- 2차: 10 발견 / 10 수정 / 0 보류
- 보완 후 관련 테스트 및 hash 재검증 PASS

## IP-5 진입 판정

`READY — IN-MEMORY RUNTIME FOUNDATION ONLY`

허용:

- Search runtime ports/interfaces
- in-memory orchestration
- retrieval/ranking interfaces
- immutable result snapshot builder
- deterministic ordering fixtures
- no production API wiring
- no DB persistence

HOLD:

- `/api/v1/explore` 변경/교체
- Controller/Service/Repository wiring
- DB/index/provider/model
- production cursor key
- SearchRun/result/exposure persistence
- Operations visibility authority 연결
