# SR-3~SR-4 Search Verification

## 상태

```text
SR-3 search behavior tracking: VERIFIED
SR-3 search_exposure_v1 persistence: HOLD_DB_CONTRACT_REQUIRED
SR-4 integration, query-shape and pagination stability: VERIFIED
Overall: IMPLEMENTED_UNVERIFIED
```

전체 상태를 `VERIFIED`로 판정하지 않는 이유는 `search_exposure_v1`의 physical schema와 writer authority가 승인되지 않았기 때문이다. 행동 사실 저장과 authoritative search exposure는 동일한 의미가 아니다.

## 검증 기준

코드 exact head:

```text
5f9d748e876278d5bffa406d9ccb9616bcd8ccb4
```

환경:

```text
Java 21
Gradle 8.14.5
Testcontainers PostgreSQL 15
```

실행 명령:

```text
./gradlew :clean :test --no-build-cache --no-daemon
```

Focused search 검증:

```text
./gradlew :clean :test --no-build-cache --no-daemon \
  --tests 'com.jc.backend.intelligence.search.*' \
  --tests 'com.jc.backend.post.PostControllerSearchShadowHookTest' \
  --tests 'com.jc.backend.post.PostApiIntegrationTest'
```

보호 추천 계약:

```text
:jc-recommendation-core:coreFoundationContractTest
:jc-recommendation-core:javaCoreGoldenFixtureContractTest
:jc-recommendation-core:p1CoreContractTest
:jc-recommendation-core:p2CoreContractTest
:p1ContractVerification
:p2ContractVerification
```

## 결과

JUnit XML 88개를 집계한 결과:

```text
tests:    312
failures:   0
errors:     0
skipped:    0
```

Search 관련 주요 테스트:

```text
RecommendationSearchCandidateSourceIntegrationTest  5 PASS
RecommendationSearchPerformanceContractTest         2 PASS
RecommendationSearchProfileSourceIntegrationTest    2 PASS
RecommendationSearchServiceTest                     9 PASS
SearchBehaviorServiceIntegrationTest                 3 PASS
SearchContextCodecTest                               4 PASS
SearchRankingPolicyTest                              6 PASS
PostControllerSearchShadowHookTest                   5 PASS
```

추천 보호 결과:

```text
Recommendation Core foundation/golden: PASS
P1 Core: 17/17 scenarios PASS
P2 Core: 23/23 scenarios PASS
Backend P1/P2 contract verification: PASS
IP-8 search readiness: 2590 assertions PASS
IP-5 search runtime: 850 assertions PASS
IP-7 search shadow wiring: 1700 assertions PASS
IP-11.5 production shadow: 147 assertions PASS
IP-12.5 Gradle readiness: PASS
```

## SR-3 검증

확인된 항목:

- `POST /api/v1/search/events` 인증 경계
- IMPRESSION, VIEW, CLICK event type 제한
- HMAC 서명된 `search-result-context-v1`
- 사용자, post ID, absolute rank exact binding
- token 변조·만료·타 사용자 거부
- event ID 및 idempotency key 중복 처리
- `search-behavior-event-v1` canonical payload
- raw query 미저장, query fingerprint만 저장
- 일반 추천 `run_id` 미결속
- `recommendation_exposure_event` row 미생성
- P2 experiment exposure 미변경

판정:

```text
search behavior facts: VERIFIED
search exposure persistence: HOLD_DB_CONTRACT_REQUIRED
```

## SR-4 검증

확인된 항목:

- 첫 페이지 `referenceTime` 고정
- user, query, page size, policy, ranking fingerprint 결속
- 동일 snapshot의 page partition 결정론
- 새 게시물의 기존 snapshot 중간 삽입 방지
- profile·후보·점수 변경 시 continuation 409
- token 변조·만료·타 사용자·다른 검색 조건 거부
- 첫 페이지 장애 legacy fail-open
- 후속 페이지 snapshot 오류 fail-closed
- snapshot 없는 기존 page-number 요청 legacy 호환
- 기존 JSON body와 query parameter 유지
- search context response header 및 CORS 노출

후보 SQL 검증:

```text
input
eligible
tag_data
like_counts
bookmark_counts
matching
```

- hard filter를 집계 전 적용
- 태그·좋아요·북마크를 eligible set 단위로 집계
- 게시물별 상관 count subquery 제거
- candidate query 1회
- ordered summary batch 1회
- N+1 repository 호출 없음
- candidate limit 초과 시 안전 fallback

## DB 및 비회귀

DB 변경:

```text
없음
```

확인:

- Flyway 변경 없음
- canonical SQL 변경 없음
- 신규 table, index, view, function, role, grant 없음
- `journey_db` 접근·변경 없음
- `/api/v1/feed` 변경 없음
- 추천 P0/P1/P2 계산·저장 의미 변경 없음
- recommendation exposure를 search exposure로 재해석하지 않음
- 기존 shadow observer는 결과 authority를 갖지 않음

## 공통 CI 상태

SR Search Recommendation workflow는 focused 검증과 전체 backend 회귀가 모두 성공했다.

저장소 공통 Backend/P0 workflow는 Gradle readiness 자체는 성공했지만, 기존 ADM-3/Data baseline verifier가 임시 detached worktree에서 다음 명령을 실행할 때 exit 128로 종료될 수 있다.

```text
git diff --name-only origin/main...HEAD
```

이 verifier 결함은 SR-3~SR-4 변경과 무관하며 이번 범위에서 수정하지 않는다.

## 잔여 작업

- `search_exposure_v1` System Contract 및 DB 계약 승인
- anonymous search exposure identity 결정
- impression threshold와 client acknowledgement 결정
- retention, deletion, privacy, attribution owner 결정
- 프론트에서 search response header 보존
- 다음 페이지 `X-Search-Snapshot` 전달
- visible impression 및 click/view event 전송
- 대규모 후보에 대한 실제 EXPLAIN ANALYZE와 index 계약
