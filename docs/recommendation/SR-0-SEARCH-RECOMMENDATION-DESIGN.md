# SR-0~SR-2 Search Recommendation Design

## 목적

`GET /api/v1/explore`의 기존 검색 후보 의미와 응답 형식을 보존하면서, 로그인 사용자의 검색 결과 내부 정렬에만 설명 가능한 개인화 신호를 보조 적용한다.

범위:

- SR-0 현재 구조 분석과 계약 고정
- SR-1 검색 후보 조회와 검색 관련도 계산
- SR-2 탐색 추천 서비스 연결과 legacy fallback

비범위:

- 크루 추천
- 프론트엔드 개편
- 검색 run/snapshot/exposure persistence
- cursor 전환
- 운영 활성화, 배포, merge
- 추천 P0/P1/P2 정책 또는 저장 의미 변경
- DB/Flyway/canonical SQL 변경

## 현재 탐색 구조

구현 전 호출 경로:

```text
GET /api/v1/explore
→ PostController.explore
→ PostService.explore
→ JourneyPostRepository.explore
→ PageResponse<PostDtos.Summary>
→ ExploreSearchShadowBridge.afterExplore
→ legacy response
```

기존 구현 저장소의 `JourneyPostRepository.explore`는 다음을 hard filter한다.

- `status = published`
- `visibility = public`
- `moderation_status = visible`
- 작성자 `account_status = active`
- keyword: 제목, 본문, 지역명
- region: slug 또는 지역명 exact match
- `published_at DESC, id DESC`

탐색 기준 브랜치의 프론트는 기존 `keyword`, `region`, `page`, `size` query parameter와 `items/content/data` 목록 호환을 기대한다. `SearchPage.jsx`는 일부 지역 필터를 클라이언트에서도 수행하지만, 백엔드 후보 계약은 지역을 hard filter로 강제한다.

## 현재 추천 구조

홈 피드는 다음 구조를 사용한다.

```text
RecommendationFeedService
→ RecommendationCandidateSource
→ RecommendationCorePipeline
→ RecommendationOrchestrationService
→ RecommendationRunStore / RecommendationExposureStore
```

홈 파이프라인은 `HOME_FEED` surface, 홈 후보 SQL, 홈 점수 정책, diversity/exploration, recommendation exposure 의미를 가진다. 검색 페이지와 목적이 다르므로 해당 파이프라인·가중치·surface·exposure를 재사용하지 않는다.

P1 프로필은 원시 행동 이벤트를 직접 점수화하지 않는다. 기존 `recommendation_p1_profile_snapshot.signals`를 우선 읽고, snapshot이 없을 때만 `recommendation_user_preference`를 cold-start 입력으로 사용한다.

## 패키지와 변경 파일

신규 runtime은 `com.jc.backend.intelligence.search`에 둔다.

근거:

- 기존 `com.jc.backend.search.shadow`는 응답 권한이 없는 IP-12 shadow observer다.
- 검색 retrieval/ranking은 Intelligence Search 의미다.
- 홈 추천 application/persistence와 의존 방향을 분리한다.
- search shadow를 ranking authority로 재해석하지 않는다.

구성:

```text
com.jc.backend.intelligence.search
├─ RecommendationSearchService
├─ RecommendationSearchCandidateSource
├─ RecommendationSearchCandidateRow
├─ RecommendationSearchCandidateMapper
├─ RecommendationSearchProfileSource
└─ SearchRankingPolicy
```

## 검색 후보 계약

입력:

```text
userId
keyword
region
candidateLimit
referenceTime
```

후보 필드:

```text
postId
authorId
regionCode
regionSlug
regionNames
title
normalized tag slugs
keyword match flags
createdAt
publishedAt
viewCount
likeCount
bookmarkCount
recentExposureCount
totalCount
```

후보 hard filter:

- 공개 발행 게시물
- moderation visible
- 삭제되지 않은 게시물
- 활성 작성자
- 활성 지역
- 입력 지역 exact match
- 검색어가 제목·본문·지역·활성 태그 중 하나와 일치
- `publishedAt <= referenceTime`

기존 legacy 검색에 활성 태그 검색을 추가한다. 이 확장은 추천 기능이 활성화된 로그인 요청에만 적용되고, 비로그인·OFF·장애 경로는 기존 `PostService.explore` 후보 집합을 그대로 사용한다.

한 번의 후보 projection query로 게시물, 지역, 태그, 좋아요·북마크 수를 읽는다. 후보별 repository 호출은 추가하지 않는다. 최종 DTO 변환은 기존 `PostService.summariesByOrderedIds`의 bounded batch 조회를 재사용한다.

## 점수 정책과 policy version

정책 ID:

```text
search-ranking-policy-v1
```

영속 `latest`, `current`, `default` 식별자는 사용하지 않는다.

정렬 계층:

```text
searchRelevance DESC
→ interestMatch DESC
→ auxiliaryScore DESC
→ createdAt DESC
→ postId DESC
```

`searchRelevance`는 제목 exact/prefix/contains, 태그 exact/contains, 지역 exact/contains, 본문 contains 순으로 계산한다. 검색 관련도는 합산 total score가 아니라 최상위 정렬 키이므로 개인화·인기 점수가 검색 관련도를 역전할 수 없다.

`interestMatch`는 최신 P1 profile snapshot의 feature signal을 사용한다. snapshot이 없으면 명시적 선호를 사용한다. 후보 지역·태그와 연결되지 않는 signal은 점수에 반영하지 않는다.

`auxiliaryScore`:

```text
popularity 55%
freshness 35%
repeatExposurePenalty 5%
diversityAdjustment 5%
```

- popularity: 조회·좋아요·북마크의 bounded logarithmic score
- freshness: `referenceTime` 기준 30일 감쇠
- repeatExposurePenalty: 향후 authoritative search exposure가 제공될 때 사용
- diversityAdjustment: 상위 window 안에서 반복 작성자·태그를 약하게 감점

동일 입력과 동일 `referenceTime`은 동일 결과를 만든다. 최종 tie-break는 `createdAt DESC, postId DESC`다.

## fallback

```text
PostController.explore
→ PostService.explore로 legacy response 선계산
→ 기존 ExploreSearchShadowBridge 관측
→ RecommendationSearchService.explore
   ├─ 로그인 + 기능 ON + 검색 조건 + 지원 page contract: 검색 추천
   └─ 그 외: legacy response
```

다음은 모두 legacy fallback이다.

- 비로그인
- `app.recommendation.search.enabled=false`
- keyword와 region이 모두 없음
- 명시적 sort 요청
- candidate pool이 `candidateLimit`을 초과해 전체 정렬할 수 없음
- P1 profile 조회 실패
- 후보 조회·정렬·DTO 변환 예외
- ranking 이후 visibility race로 DTO 수가 달라짐

첫 페이지를 포함한 추천 처리 예외는 탐색 API 장애로 전파하지 않는다.

기본 설정:

```text
app.recommendation.search.enabled=false
app.recommendation.search.candidate-limit=1000
```

운영 활성화는 이번 단계에 포함하지 않는다.

## API 호환성

변경하지 않는 계약:

```text
GET /api/v1/explore
keyword
region
page
size
PageResponse<PostDtos.Summary>
ApiResponse<T>
```

인증 JWT는 선택적으로 해석한다. 비로그인 요청 동작은 기존과 같다. cursor는 이번 단계에서 도입하지 않는다.

## DB 영향

없음.

- Flyway 및 canonical SQL 수정 없음
- 신규 table/index/view/function/role/grant 없음
- 기존 recommendation run/snapshot/exposure/P1/P2 table 의미 변경 없음
- `journey_db` 직접 변경 없음

## 노출 persistence 판정

SR-0~SR-2는 read-only 검색 추천이다.

- `search_exposure_v1`은 ID만 예약되어 있고 물리 모델이 확정되지 않았다.
- `recommendation_exposure_event`를 검색 exposure로 사용하지 않는다.
- 일반 추천 exposure와 검색 노출을 합산하지 않는다.
- P2 experiment exposure를 변경하지 않는다.
- `recentExposureCount`는 현재 0이며, SR-3에서 authoritative search exposure 계약이 확정된 뒤 연결한다.

## 테스트 결과

구현 단계에서 다음 테스트를 추가한다.

- 후보 hard filter: 지역, keyword, tag, draft/moderation, inactive author
- 순위: relevance 우선, 관심사 보조, popularity/freshness, diversity, 결정론, tie-break
- fallback: anonymous, OFF, 예외, explicit sort, incomplete pool
- P1 profile: 최신 snapshot, explicit cold-start

최종 상태는 branch exact HEAD에서 전체 Gradle test와 관련 CI가 통과한 뒤 갱신한다.

## 잔여 리스크

- 실제 `search_exposure_v1` persistence와 반복 노출 계산 미구현
- page-number 요청 사이의 고정 `referenceTime`/snapshot 결속 없음
- 대규모 후보에서 candidate-limit 초과 시 legacy fallback
- 프론트가 selected region을 항상 `region` query parameter로 전달하지 않는 현재 화면 경로
- 검색 run/explanation/evaluation evidence 미구현
- PostgreSQL text search index 또는 전문검색 엔진 미도입

## 다음 작업

```text
SR-3 탐색 노출·행동 추적 계약 및 구현
SR-4 통합·성능·pagination 안정성 검증
```
