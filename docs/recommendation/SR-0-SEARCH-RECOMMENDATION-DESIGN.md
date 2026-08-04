# SR-0~SR-2 Search Recommendation Design

## 목적

`GET /api/v1/explore`의 기존 검색 조건과 응답 형식을 보존하면서, 로그인 사용자의 검색 결과 후보 내부에서만 결정론적 추천 정렬을 적용한다.

범위:

- SR-0 현재 구조 분석 및 검색 추천 계약 고정
- SR-1 검색 후보 조회 및 검색 관련도 계산
- SR-2 탐색 추천 서비스 연결 및 legacy fallback

비범위:

- 크루 추천, 프론트엔드 개편, 운영 활성화, 배포, merge
- 검색 run/snapshot/exposure persistence와 cursor 전환
- 추천 P0/P1/P2 정책·저장 의미 변경
- DB, Flyway, canonical SQL 변경

## 현재 탐색 구조

```text
GET /api/v1/explore
→ PostController.explore
→ PostService.explore
→ JourneyPostRepository.explore
→ PageResponse<PostDtos.Summary>
→ ExploreSearchShadowBridge.afterExplore
→ legacy response
```

기존 탐색은 공개 발행, moderation visible, 활성 작성자를 강제하고 제목·본문·지역명을 검색한다. 탐색 기준 프론트는 기존 `keyword`, `region`, `page`, `size`와 `PageResponse<PostDtos.Summary>` 호환을 요구한다.

## 현재 추천 구조

```text
RecommendationFeedService
→ RecommendationCandidateSource
→ RecommendationCorePipeline
→ RecommendationOrchestrationService
→ RecommendationRunStore / RecommendationExposureStore
```

홈 피드의 후보 SQL, `HOME_FEED` surface, 점수 정책, exploration/diversity, exposure 의미는 검색 페이지에 재사용하지 않는다. 검색은 별도 Intelligence Search runtime으로 구현한다.

P1 개인화는 원시 행동 이벤트를 직접 점수화하지 않는다. 최신 `recommendation_p1_profile_snapshot.signals`를 우선 읽고, snapshot이 없으면 `recommendation_user_preference`를 cold-start 입력으로 사용한다.

## 변경 파일

```text
com.jc.backend.intelligence.search
├─ RecommendationSearchService
├─ RecommendationSearchCandidateSource
├─ RecommendationSearchCandidateRow
├─ RecommendationSearchCandidateMapper
├─ RecommendationSearchProfileSource
└─ SearchRankingPolicy
```

추가 변경:

- `PostController`: 선택 인증 사용자와 legacy response를 검색 서비스에 전달
- 검색 후보·점수·fallback·P1 profile 통합 테스트
- 기존 탐색 API/shadow hook 테스트 보강
- SR 전용 Java 21/PostgreSQL 15 CI

패키지는 `com.jc.backend.intelligence.search`를 사용한다. 기존 `com.jc.backend.search.shadow`는 응답 권한이 없는 shadow observer이므로 ranking authority로 재해석하지 않는다.

## 검색 후보 계약

입력:

```text
userId
keyword
region
candidateLimit
referenceTime
```

후보 최소 필드:

```text
postId, authorId, regionCode, regionSlug, regionNames, title, tagSlugs
keyword match flags, createdAt, publishedAt
viewCount, likeCount, bookmarkCount, recentExposureCount, totalCount
```

hard filter:

- `status = published`
- `visibility = public`
- `moderation_status = visible`
- `deleted_at is null`
- 작성자 `account_status = active`
- 활성 지역
- 입력 지역 exact match
- 검색어가 제목·본문·지역·활성 태그 중 하나와 일치
- `publishedAt <= referenceTime`

한 projection query에서 게시물, 지역, 태그, 좋아요·북마크 수를 읽는다. 후보별 repository 호출은 추가하지 않는다. 최종 DTO는 기존 bounded batch 조회를 사용한다.

활성 태그 검색은 로그인·기능 ON 검색 추천 경로에만 추가된다. 비로그인, 기능 OFF, 장애 시에는 기존 `PostService.explore` 후보 집합을 그대로 반환한다.

## 점수 정책과 policy version

정책 ID:

```text
search-ranking-policy-v1
```

정렬 우선순위:

```text
searchRelevance DESC
→ interestMatch DESC
→ auxiliaryScore DESC
→ createdAt DESC
→ postId DESC
```

검색 관련도는 제목 exact/prefix/contains, 태그 exact/contains, 지역 exact/contains, 본문 contains 순으로 계산한다. 별도 최상위 정렬 키이므로 개인화·인기 점수가 검색 관련도를 역전하지 못한다.

`interestMatch`는 P1 profile의 지역·태그 feature와 후보를 비교한다. 연결되지 않는 feature는 반영하지 않는다.

보조 점수:

```text
popularity 55%
freshness 35%
repeatExposurePenalty 5%
diversityAdjustment 5%
```

- popularity: 조회·좋아요·북마크 bounded score
- freshness: `referenceTime` 기준 30일 감쇠
- repeat exposure: 현재 authoritative search exposure 부재로 0
- diversity: 상위 window에서 반복 작성자·태그를 약하게 감점

같은 입력과 `referenceTime`은 같은 결과를 만든다. 최종 tie-break는 `createdAt DESC, postId DESC`다. `latest`, `current`, `default`를 정책 ID로 사용하지 않는다.

## fallback

```text
PostController.explore
→ PostService.explore legacy response 선계산
→ 기존 ExploreSearchShadowBridge 관측
→ RecommendationSearchService.explore
   ├─ 로그인 + ON + 검색 조건 + 지원 page contract: 검색 추천
   └─ 그 외: legacy response
```

legacy fallback 조건:

- 비로그인
- `app.recommendation.search.enabled=false`
- keyword와 region이 모두 없음
- 명시적 sort 요청
- 후보 전체를 candidate limit 안에 적재하지 못함
- profile·후보·정렬·DTO 변환 예외
- ranking 이후 visibility race

추천 처리 예외는 탐색 API 장애로 전파하지 않는다. 기본값은 OFF다.

```text
app.recommendation.search.enabled=false
app.recommendation.search.candidate-limit=1000
```

## API 호환성

변경 없음:

```text
GET /api/v1/explore
keyword, region, page, size
ApiResponse<PageResponse<PostDtos.Summary>>
```

인증 JWT는 선택적으로 해석한다. cursor는 이번 단계에 도입하지 않는다.

## DB 영향

없음.

- Flyway 및 canonical SQL 변경 없음
- 신규 table/index/view/function/role/grant 없음
- recommendation run/snapshot/exposure/P1/P2 의미 변경 없음
- `journey_db` 사용·변경 없음

## 노출 persistence 판정

read-only 구현을 선택했다.

- `search_exposure_v1`은 ID만 예약되어 있고 물리 모델은 미확정
- recommendation exposure를 검색 exposure로 위장하지 않음
- 일반 추천 exposure와 검색 노출을 합산하지 않음
- P2 experiment exposure 변경 없음
- `recentExposureCount`는 SR-3 계약 확정 전까지 0

## 테스트 결과

추가·보강 범위:

- 룩셈부르크 지역 hard filter
- 검색어 불일치, 태그 일치
- draft, moderation hidden, 비활성 작성자 제외
- 검색 관련도 우선, 관심 태그 보조
- 인기·최신성·다양성·결정론·tie-break
- 비로그인, 기능 OFF, 예외, explicit sort, incomplete pool fallback
- 최신 P1 snapshot과 explicit cold-start
- 기존 탐색 API 직렬화·pageable·shadow hook
- 기존 게시물 공개 범위와 좋아요 idempotency

검증 결과:

```text
SR Search Recommendation / Java 21 / PostgreSQL 15
focused search + explore regression: 25/25 PASS
recommendation core foundation/golden: PASS
P1 core 17 scenarios: PASS
P2 core 23 scenarios: PASS
backend P1 contract verification: PASS
backend P2 contract verification: PASS
```

저장소 공통 P0 gate는 SR 변경과 무관한 기존 `JavaOnlyVerificationBoundaryTest`에서 중단된다. 해당 테스트가 기존 관리자 UI용 Node workflow 2개를 전역 탐지한다. SR 범위에서 P0 보호 테스트나 관리자 UI workflow를 수정하지 않으므로 공통 전체 gate 교정 전에는 `VERIFIED`로 판정하지 않는다.

## 잔여 리스크

- 실제 `search_exposure_v1` persistence와 반복 노출 계산
- page-number 요청 사이의 고정 `referenceTime`/snapshot 결속
- candidate limit 초과 시 legacy fallback
- 프론트의 selected region query 전달 일관성
- 검색 run/explanation/evaluation evidence
- PostgreSQL 전문검색 index 미도입
- 저장소 공통 Java-only verification boundary와 관리자 UI workflow 범위 충돌

## 다음 작업

```text
SR-3 탐색 노출·행동 추적
SR-4 통합·성능·pagination 안정성 검증
```
