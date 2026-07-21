# Behavior Event Taxonomy V1

## 문서 정보

| 항목 | 값 |
|---|---|
| 계약 ID | `behavior-event-taxonomy-v1` |
| 상태 | `RECOVERED / ACTIVE DESIGN` |
| event family | `user_behavior` |
| 소유 | Data Platform |

## 원칙

- impression, view, click과 state mutation을 구분한다.
- command와 실제 state transition event를 구분한다.
- no-op mutation은 새 canonical event를 만들지 않는다.
- 반대 동작은 source event를 삭제하지 않고 append한다.
- event type 의미 변경은 새 type/taxonomy version이 필요하다.
- recommendation weight/policy는 Intelligence, metric/attribution은 Reliability가 소유한다.

## Registry

| eventType | 의미 | producer | 발생/중복 기준 | required payload | recommendation | experiment outcome | reverse |
|---|---|---|---|---|---:|---:|---|
| `post_impression` | general post card exposure policy 충족 | frontend/backend | actor/session/entity/surface/episode | `surface`,`position`,`impressionPolicyVersion` | yes | yes | none |
| `post_view` | detail/equivalent full-content 진입 | application | view episode | `surface` | yes | yes | none |
| `post_dwell` | bounded view duration checkpoint | frontend/backend | viewRef+sequence | `viewEventRef`,`durationMillis` | yes | conditional | none |
| `post_like` | false→true actual transition | post application | mutation idempotency | `stateTransitionRef` | yes | yes | `post_unlike` |
| `post_unlike` | true→false actual transition | post application | mutation idempotency | `stateTransitionRef` | correction | yes | `post_like` |
| `post_bookmark` | false→true actual transition | post application | mutation idempotency | `stateTransitionRef` | yes | yes | `post_unbookmark` |
| `post_unbookmark` | true→false actual transition | post application | mutation idempotency | `stateTransitionRef` | correction | yes | `post_bookmark` |
| `post_share` | share handoff/action confirmed | application | command idempotency | `shareChannelClass` | yes | yes | none |
| `post_hide` | hide/not-interested state applied | application | mutation idempotency | `reasonCode` | negative | guardrail candidate | future restore contract |
| `post_report` | report record accepted | Operations ingress | report command idempotency | `reportReasonCode` | direct learning prohibited | safety guardrail | none |
| `search_submit` | validated search run created | Search | `searchRunRef` | `searchRunRef`,`queryRef` | yes | yes | none |
| `search_result_impression` | result exposure threshold met | Search/frontend | run+entity+position+episode | `searchRunRef`,`position`,`surface` | yes | yes | none |
| `search_result_click` | accepted result selection | Search/frontend | command idempotency | `searchRunRef`,`position`,`surface` | yes | yes | none |
| `recommendation_impression` | candidate exposure persisted | Recommendation | run+rank+episode | `runRef`,`absoluteRank`,`surface` | yes | yes | none |
| `recommendation_click` | bound candidate selected | Recommendation | command idempotency | `runRef`,`absoluteRank`,`surface` | yes | yes | none |
| `profile_view` | authorized profile view | application | view episode | `surface` | limited | conditional | none |
| `follow` | false→true transition | social application | mutation idempotency | `stateTransitionRef` | yes | yes | `unfollow` |
| `unfollow` | true→false transition | social application | mutation idempotency | `stateTransitionRef` | correction | yes | `follow` |
| `tag_click` | tag navigation/search accepted | application | command idempotency | `tagRef`,`surface` | yes | conditional | none |
| `crew_join` | membership false→true | crew application | mutation idempotency | `stateTransitionRef` | yes | yes | `crew_leave` |
| `crew_leave` | membership true→false | crew application | mutation idempotency | `stateTransitionRef` | correction | yes | `crew_join` |

## Entity and privacy

- post events: `post:<id>`
- recommendation/search result: candidate/result entityRef
- search_submit may have null entity; raw query prohibited, use approved `queryRef`
- profile/follow target uses existing `user:<id>` only in protected source context; new canonical actor remains `subject:<opaque-id>`
- tag: `tag:<stable-slug-or-id>`
- crew: `crew:<id>`
- report free text/share recipient/private profile/exact GPS are prohibited

## Impression authority

- general recommendation exposure authority remains recommendation exposure tables.
- behavior impression is not automatically a P2 denominator.
- P2 experiment exposure authority remains `recommendation_p2_experiment_exposure`.
- general post impression and recommendation impression must not be double-emitted for the same semantic exposure.

## P0 mapping boundary

Bare P0 values are not renamed in source. Mapping occurs only in `p0-recommendation-event-adapter-v1`:

| P0 | Data canonical candidate |
|---|---|
| `impression` | conditional `recommendation_impression` after authority/dedupe |
| `view` | `post_view` |
| `click` | `recommendation_click` |
| `like/unlike` | `post_like/post_unlike` |
| `save/unsave` | `post_bookmark/post_unbookmark` |
| `share/hide/report/search` | corresponding versioned Data type |
| `tag_click/follow/unfollow/crew_join/crew_leave` | same semantic versioned Data type after binding validation |
