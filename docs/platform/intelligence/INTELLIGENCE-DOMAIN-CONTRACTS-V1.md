# Intelligence Domain Contracts V1

## 1. 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `intelligence-domain-contracts-v1` |
| 상태 | `ACTIVE DESIGN` |
| 소유 트랙 | Intelligence Platform |
| 공통 계약 | [INTELLIGENCE-COMMON-CONTRACTS-V1.md](INTELLIGENCE-COMMON-CONTRACTS-V1.md) |

## 2. 공통 분리 원칙

네 도메인은 공통 run/snapshot/provenance/version 계약을 사용하지만 다음을 공유하지 않는다.

- ranking policy
- score/confidence scale
- exposure denominator
- metric definition
- candidate domain extension
- fallback code
- privacy rule
- provider dependency

## 3. Recommendation Intelligence

### 3.1 책임

- candidate input 소비 또는 후보 생성
- eligibility, score, rank, diversity, exploration
- profile/context/policy 선택
- fallback
- 추천 근거
- run/snapshot/general exposure 연결

현재 P0/P1/P2가 reference implementation이다.

### 3.2 입력

- subject/session/surface/context
- `referenceTime`
- candidate facts snapshot
- P1 profile snapshot 또는 승인된 future input
- policy bundle/version
- visibility/eligibility projection
- experiment assignment reference가 있으면 resolved assignment

### 3.3 출력

- ordered recommendation result
- score breakdown/provenance
- terminal/excluded candidate evidence
- fallback result
- user/operator explanation refs
- run/result fingerprint

### 3.4 run type

```text
recommendation
```

도메인 extension 예:

- recommendation mode: shadow/canary/live
- baseline/treatment relation
- core build ID
- ranking/diversity/exploration policy vector

### 3.5 snapshot

현재 recommendation snapshot kind는 보호한다.

- `ranking_input_v1`
- `diversity_metadata_v1`
- `exploration_metadata_v1`
- `ranking_result_v1`
- `exposure_event_v1`

공통 snapshot으로 rename하지 않는다. adapter가 common input/candidate/output 역할을 매핑한다.

### 3.6 exposure

- 일반 추천 page exposure는 기존 `recommendation_exposure_event` 경로
- behavior `impression`은 행동 fact
- P2 experiment exposure는 `recommendation_p2_experiment_exposure`

P2 실험 분모에는 P2 experiment exposure만 사용한다.

### 3.7 failure/fallback

보호된 기존 예:

- `EMPTY_PROFILE`
- `NO_ELIGIBLE_CANDIDATES`
- `CORE_EXECUTION_FAILED`
- `SNAPSHOT_PERSISTENCE_FAILED`
- `RUN_STALE`
- `FEATURE_DISABLED`

실제 기존 wire value를 변경하지 않는다. 신규 code는 새 policy/schema version에서 추가한다.

### 3.8 metric connection

Recommendation은 run, exposure, fallback, outcome-compatible refs를 제공한다. metric numerator/denominator, attribution, release 결정은 Reliability가 소유한다.

현재 P2 metric:

- primary `engagement_rate`
- guardrail `fallback_rate`

의미는 Compatibility 문서에서 보호한다.

### 3.9 privacy

- raw user profile와 behavior 원문을 explanation에 노출하지 않는다.
- subject는 server-derived 또는 승인된 mapping을 사용한다.
- recommendation score 세부 정보의 외부 노출은 abuse/gaming 위험을 검토한다.

### 3.10 future phase

IP-1에서는 기존 recommendation 객체를 변경하지 않고 common contract adapter fixture와 compatibility test를 우선한다.

## 4. Search Intelligence

### 4.1 책임

- query normalization
- retrieval
- filtering
- ranking/reranking
- stable pagination/cursor
- no-result/fallback
- result explanation
- search result exposure

검색은 추천과 동일 엔진으로 가정하지 않는다.

### 4.2 `SearchRequest`

필수 후보:

| 필드 | 필수 | 의미 |
|---|---:|---|
| `requestId` | 예 | request identity |
| `subjectRef` | 조건부 | 개인화 검색 시 |
| `sessionId` | 예 | server-derived |
| `surface` | 예 | search surface |
| `queryTextRef` | 예 | 원문 직접 저장이 아니라 restricted/ref 또는 normalized snapshot |
| `filters` | 예 | stable ordered canonical filters |
| `locale` | 예 | normalization/display context |
| `referenceTime` | 예 | 계산 기준 |
| `pageSize` | 예 | bounded |
| `cursor` | 아니오 | 다음 페이지 |

raw query privacy는 Data/Privacy 정책을 따르며 일반 event payload에 복제하지 않는다.

### 4.3 `QuerySnapshot`

- raw query restricted reference 또는 irreversible queryRef
- normalized tokens/terms
- locale/language detection version
- spelling/synonym expansion version
- filter canonicalization
- query classification
- content hash

normalization output을 source fact로 오인하지 않는다.

### 4.4 `RetrievalRun`

- retrievalRunId
- querySnapshotId
- indexSnapshotId/provider snapshot
- retrievalPolicyVersion
- topK
- stable candidate order
- latency
- failure/fallback code
- candidate snapshot

### 4.5 `RankingRun`

- retrieval candidate snapshot
- filter policy version
- ranking/reranking policy version
- feature definition version
- model inference record refs
- deterministic tie-break
- final result snapshot

RetrievalRun과 RankingRun을 하나의 implementation method로 수행할 수 있지만 evidence 상 stage를 구분해야 한다.

### 4.6 `SearchResultSnapshot`

- ordered result refs
- final rank
- relevance score 또는 scoreRef
- match/highlight structured fields
- explanation refs
- index/content version refs
- result hash

### 4.7 `SearchExposure`

- searchRunId/resultSnapshotId
- subject/session/surface
- exposed result refs와 positions
- exposedAt
- exposure fingerprint
- pagination cursor context

Recommendation exposure와 별도 source authority를 가진다.

### 4.8 no-result/fallback code

| code | status | 의미 |
|---|---|---|
| `SEARCH_NO_RESULTS` | succeeded | 정상 0건 |
| `SEARCH_FILTERS_EXCLUDE_ALL` | succeeded | filter 결과 0건 |
| `SEARCH_INDEX_UNAVAILABLE` | fallback/failed | index 장애 |
| `SEARCH_RERANKER_FAILED` | fallback | retrieval order 사용 |
| `SEARCH_POLICY_UNAVAILABLE` | failed | resolved policy 없음 |
| `SEARCH_CURSOR_INVALID` | failed | cursor binding 실패 |
| `SEARCH_INDEX_SNAPSHOT_STALE` | fallback/failed | freshness policy에 따름 |

### 4.9 pagination/cursor stability

cursor는 최소 다음에 결속한다.

- search run/result snapshot
- next rank
- subject/session
- filter/query snapshot hash
- expiry
- signature/encryption version

다음 페이지에서 새 index 상태로 재검색해 순위를 섞지 않는다. live freshness를 선택한다면 새 search run으로 명시한다.

### 4.10 metric connection

후속 Reliability 계약 후보:

- result click-through
- zero-result rate
- reformulation rate
- latency guardrail

IP-0에서 metric 의미를 확정하지 않는다. search exposure가 분모 authority 후보이며 총괄/Reliability 승인이 필요하다.

### 4.11 privacy

- raw query는 restricted class
- 민감 query category는 최소 보존
- explanation에 raw profile/query를 반복 노출하지 않는다.

### 4.12 future phase

Search IP 단계는 query normalization + in-memory/static fixture 기반 retrieval/ranking contract test부터 시작한다. production engine 도입은 별도 단계다.

## 5. Content Analysis Intelligence

### 5.1 책임

- tag/theme/region/place extraction
- content classification
- quality signal
- risk/moderation signal reference
- structured feature generation
- model/prompt provenance
- stale/invalidated/human override linkage

Operations의 moderation 결정은 소유하지 않는다.

### 5.2 `ContentAnalysisRequest`

- analysisRequestId
- entityRef
- sourceContentVersion
- requestedAnalysisTypes
- locale
- policyVersion
- model/prompt version 또는 registry selector
- referenceTime
- idempotencyKey

### 5.3 `ContentInputSnapshot`

- source content version/ref
- allowlisted text/media references
- locale
- existing source facts
- privacy class
- content hash
- consent/license context가 필요한 경우 version

원문을 분석 output에 복제하지 않는다.

### 5.4 `ExtractedFeature`

Common `FeatureValue`를 따르며 featureClass는 일반적으로 `model_inference` 또는 `derived_aggregate`다.

예:

- `content.semantic.theme.healing`
- `content.semantic.place_candidate`
- `content.semantic.language`
- `content.semantic.quality_signal`

원천 장소 정보는 `place.fact.*`와 분리한다.

### 5.5 `ClassificationResult`

- class ID와 definitionVersion
- confidence nullable
- evidence span ref 또는 media region ref
- model inference record
- safety result
- abstain/unknown 지원

confidence가 threshold보다 낮은 결과를 확정 사실로 저장하지 않는다.

### 5.6 `ModerationSignalReference`

Content Analysis는 위험 신호를 생성할 수 있으나 hide/remove/penalty 결정을 하지 않는다.

- signalRef
- signalType/version
- severity/confidence
- evidenceRef
- Operations case/override ref nullable

### 5.7 `AnalysisOutputSnapshot`

- extracted features
- classifications
- quality/risk signal refs
- schema validation result
- stale/invalidated state
- human override refs
- model/prompt provenance
- output hash

### 5.8 상태

output semantic state:

```text
valid
stale
invalidated
superseded
```

실행 status와 output state를 혼동하지 않는다. 예를 들어 run은 succeeded였으나 source content가 수정되어 output이 stale일 수 있다.

### 5.9 failure/fallback

- `CONTENT_INPUT_UNSUPPORTED`
- `CONTENT_MODEL_FAILED`
- `CONTENT_SCHEMA_INVALID`
- `CONTENT_SAFETY_BLOCKED`
- `CONTENT_PARTIAL_RESULT`
- `CONTENT_SOURCE_VERSION_CHANGED`

partial result는 succeeded로 위장하지 않는다.

### 5.10 downstream effect

추천·검색이 분석 feature를 소비할 때 다음을 기록한다.

- analysis output snapshot ID
- feature definition/model/prompt version
- freshness/stale 상태
- source priority policy

분석 output이 자동 authority가 되지 않는다.

### 5.11 metric connection

Reliability가 schema validity, human agreement, coverage, latency, false-positive/negative evidence를 정의할 수 있다. moderation 최종 정확도와 운영 판정은 Operations/Reliability 협의다.

### 5.12 privacy

- 사용자 원문과 media는 restricted source ref로 관리
- raw content를 log/event/explanation에 복제 금지
- 얼굴·정밀 위치·민감 속성 inference는 별도 승인 없이는 feature registry 등록 금지

### 5.13 future phase

초기 구현은 deterministic schema validator와 stub/provider adapter contract test를 우선한다. production model 호출은 별도 승인 단계다.

## 6. Trip Planning Intelligence

### 6.1 책임

- traveler constraints 소비
- place candidate 조합
- itinerary ordering/scoring
- 시간·거리·영업시간·예산·선호 constraint check
- partial plan/fallback
- 생성 근거와 violation evidence

### 6.2 `TripPlanRequest`

- tripPlanRequestId
- subject/session
- locale/timezone
- trip start/end
- origin/area refs
- party/traveler preference refs
- hard/soft constraints
- budget/currency snapshot
- policy/model/prompt versions
- referenceTime
- idempotencyKey

### 6.3 `TravelerConstraintSnapshot`

각 constraint는 다음을 가진다.

- constraintId
- type
- hard/soft
- value/unit
- source/user-confirmed 여부
- validFrom/Until
- privacy class
- definitionVersion

예:

- availability window
- mobility/accessibility
- budget
- cuisine/theme preference
- indoor/outdoor
- reservation requirement

### 6.4 `PlaceCandidateSnapshot`

- placeRef
- place fact snapshot ID
- provider/source version
- coordinates precision policy
- opening hours as-of
- travel-time matrix snapshot
- estimated cost/currency rate snapshot
- eligibility
- freshness/staleness
- provenance refs

외부 사실은 조회 시각과 provider source가 필수다.

### 6.5 `ItineraryDraft`

- itineraryDraftId
- ordered days/stops
- start/end windows
- place refs
- travel legs
- estimated duration/cost
- score components
- unverified assumptions
- explanation refs
- draft hash

사용자 확정 itinerary와 AI draft를 분리한다.

### 6.6 `ConstraintCheckResult`

- constraintId
- status: `satisfied`, `violated`, `unknown`, `not_applicable`
- evidence refs
- severity
- measured/expected values
- checkerPolicyVersion

unknown을 satisfied로 처리하지 않는다.

### 6.7 `TripPlanOutputSnapshot`

- full 또는 partial itinerary
- satisfied/violated/unknown summary
- alternative refs
- fallback reason
- provider/model/prompt provenance
- output hash
- validity window

### 6.8 partial/fallback

| code | status | 의미 |
|---|---|---|
| `TRIP_PLAN_PARTIAL_CONSTRAINTS` | partial | 일부 constraint 미충족/unknown |
| `TRIP_PLAN_NO_FEASIBLE_ROUTE` | fallback/failed | hard constraint 만족 조합 없음 |
| `TRIP_PLAN_PLACE_DATA_STALE` | partial/fallback | place facts freshness 부족 |
| `TRIP_PLAN_TRAVEL_TIME_UNAVAILABLE` | partial/fallback | 이동시간 provider 실패 |
| `TRIP_PLAN_MODEL_FAILED` | fallback | deterministic heuristic 사용 가능 |
| `TRIP_PLAN_BUDGET_UNVERIFIED` | partial | 비용 사실 검증 불충분 |

### 6.9 replay

- deterministic constraint checker와 itinerary scoring은 exact replay 대상
- 외부 facts/model generation은 semantic/evidence replay 대상
- 당시 provider snapshot 없이 현재 provider를 다시 호출해 과거 plan을 재현했다고 선언하지 않는다.

### 6.10 downstream effect

- user accepted/rejected/edited draft
- published itinerary
- booking/outbound link action

이 행동의 canonical event ingestion은 Data가 소유한다. planner는 run/draft refs를 제공한다.

### 6.11 metric connection

Reliability 후보:

- hard constraint violation rate
- partial plan rate
- user acceptance/edit distance
- provider failure/fallback rate
- latency/cost guardrail

IP-0에서 출시 threshold를 정하지 않는다.

### 6.12 privacy

여행 일정은 위치·시간·동행 정보가 결합될 수 있어 기본 `restricted` 또는 `pseudonymous`로 분류한다. 정밀 개인 이동 계획을 일반 log/explanation에 기록하지 않는다.

### 6.13 future phase

place contract와 provider authority가 확정된 뒤 deterministic constraint engine부터 구현한다. 자유 텍스트 생성은 구조화 output validator 뒤에 둔다.

## 7. 도메인 비교표

| 항목 | Recommendation | Search | Content Analysis | Trip Planning |
|---|---|---|---|---|
| run type | recommendation | search | content_analysis | trip_planning |
| ordered candidates | 예 | 예 | 일반적으로 아니오 | 예 |
| score 의미 | preference/policy | relevance | confidence/quality | utility/constraint |
| exposure | general + P2 experiment 별도 | search exposure | downstream consumption | draft acceptance/effect |
| deterministic core | 현재 존재 | normalization/rank 가능 | validation/rules 가능 | constraint/scoring 가능 |
| model optional | 향후 | reranker/embedding | 주요 후보 | 생성/보조 |
| primary privacy | profile/behavior | query | raw content/media | location/time/party |
| semantic evaluation owner | Reliability | Reliability | Reliability + Operations | Reliability |
