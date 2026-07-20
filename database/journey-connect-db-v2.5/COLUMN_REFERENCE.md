# Journey Connect DB v1.8 컬럼 설명서

## app_users

| 컬럼 | 의미 |
|---|---|
| `id` | 사용자 식별자 |
| `email` | 로그인 이메일 |
| `password_hash` | 해시 처리된 비밀번호. `jc_auth`만 조회 |
| `username` | 중복되지 않는 계정명 |
| `display_name` | 화면 표시 이름 |
| `profile_image_url` | 프로필 이미지 경로 |
| `bio` | 개인 블로그 소개 |
| `role` | `user`, `moderator`, `admin` |
| `account_status` | `active`, `suspended`, `withdrawn` |
| `created_at` / `updated_at` | 생성·수정 시각 |

## regions

| 컬럼 | 의미 |
|---|---|
| `id` | 지역 식별자 |
| `parent_id` | 상위 지역 |
| `name_local` / `name_ko` / `name_en` | 현지어·한국어·영어 지역명 |
| `slug` | URL·검색용 고유 문자열 |
| `region_type` | 국가·주·도시·구역·동네 등 단계 |
| `country_code` | ISO 2자리 국가 코드 |
| `timezone` | IANA 시간대 |
| `sort_order` | 화면 정렬 순서 |
| `is_active` | 검색·노출 여부 |
| `created_at` / `updated_at` | 생성·수정 시각 |

## places

| 컬럼 | 의미 |
|---|---|
| `id` | 장소 식별자 |
| `region_id` | 장소가 직접 속한 지역 |
| `name_local` / `name_ko` / `name_en` | 장소명 |
| `normalized_name` | 공백·대소문자를 정리한 검색 보조 이름 |
| `address` | 주소 |
| `latitude` / `longitude` | 좌표 |
| `category` | 카페·식당·관광지 등 분류 |
| `created_by_user_id` | 장소 등록 사용자 |
| `is_active` | 사용·노출 여부 |
| `created_at` / `updated_at` | 생성·수정 시각 |

## posts

| 컬럼 | 의미 |
|---|---|
| `id` | 게시글 식별자 |
| `author_id` | 작성자 |
| `main_region_id` | 지역 피드 기준 대표 지역 |
| `title` / `content` | 제목·본문 |
| `view_count` | `increment_post_view()`로만 증가하는 누적 조회수 |
| `visibility` | `public`, `followers`, `private` |
| `status` | 작성자 생명주기: `draft`, `published`, `deleted` |
| `moderation_status` | 운영 노출 상태: `visible`, `hidden` |
| `published_at` | 최초 공개 시각 |
| `deleted_at` / `purge_after` | 사용자 논리 삭제·영구 삭제 예정 시각 |
| `moderated_at` | 운영 숨김 적용 시각 |
| `created_at` / `updated_at` | 생성·수정 시각 |

운영 숨김 상태에서는 대표 지역·제목·본문·공개 범위·작성 상태를 변경할 수 없습니다.

## post_images

| 컬럼 | 의미 |
|---|---|
| `id` | 이미지 식별자 |
| `post_id` | 소속 게시글. UPDATE 불가 |
| `image_url` | 이미지 저장 경로 |
| `alt_text` / `caption` | 접근성 설명·이미지 설명 |
| `sort_order` | 표시 순서 |
| `width` / `height` | 이미지 크기 |
| `created_at` | 생성 시각. UPDATE 불가 |

게시글이 운영 숨김 상태이면 이미지의 추가·수정·삭제가 모두 차단됩니다.

## post_places

| 컬럼 | 의미 |
|---|---|
| `id` | 연결 식별자 |
| `post_id` | 게시글. UPDATE 불가 |
| `place_id` | 포함 장소 |
| `sort_order` | 표시 순서 |
| `memo` | 장소별 짧은 후기·팁 |
| `created_at` | 연결 시각. UPDATE 불가 |

게시글이 운영 숨김 상태이면 장소 연결의 추가·수정·삭제가 모두 차단됩니다.

## tags / post_tags

| 테이블·컬럼 | 의미 |
|---|---|
| `tags.id` | 태그 식별자 |
| `tags.slug` | 내부 고유 문자열 |
| `tags.name_ko` / `name_en` | 표시명 |
| `tags.is_active` / `sort_order` | 선택 여부·정렬 순서 |
| `post_tags.post_id` / `tag_id` | 게시글과 태그 연결. 수정 대신 삭제 후 재삽입 |

게시글이 운영 숨김 상태이면 태그 연결의 추가·삭제가 차단됩니다.

## comments

| 컬럼 | 의미 |
|---|---|
| `id` | 댓글 식별자 |
| `post_id` | 소속 게시글 |
| `author_id` | 작성자 |
| `content` | 댓글 내용 |
| `deleted_at` | 작성자 측 논리 삭제 시각 |
| `moderation_deleted_at` | 관리자 운영 삭제 시각 |
| `created_at` / `updated_at` | 작성·수정 시각 |

운영 삭제 상태에서는 본문과 작성자 삭제 상태를 변경할 수 없습니다.

## post_likes / bookmarks / follows

| 테이블·컬럼 | 의미 |
|---|---|
| `post_likes.post_id` / `user_id` | 게시글 좋아요 |
| `bookmarks.post_id` / `user_id` | 게시글 저장 |
| `follows.follower_id` | 팔로우를 건 사용자 |
| `follows.following_id` | 팔로우 대상 |
| `created_at` | 생성 시각 |

세 테이블은 집합형 연결 데이터이므로 UPDATE하지 않고 INSERT·DELETE로 상태를 변경합니다.

## reports

| 컬럼 | 의미 |
|---|---|
| `id` | 신고 식별자 |
| `reporter_id` | 신고자. 계정 삭제 시 `NULL` 가능 |
| `target_type` | `user`, `post`, `comment` |
| `target_entity_id` | 원본이 삭제되어도 유지되는 대상 ID |
| `target_user_id` / `target_post_id` / `target_comment_id` | 원본이 존재하는 동안의 선택적 FK. 삭제 시 `NULL` |
| `target_snapshot` | 신고 당시 대상 정보 JSON. 접수 후 변경 불가 |
| `reason_category` / `reason_detail` | 신고 분류·상세 사유. 접수 후 변경 불가 |
| `status` | `pending`, `in_review`, `resolved`, `rejected` |
| `handled_by_user_id` / `handled_by_username` / `handled_by_role` | 처리 운영자 스냅샷 |
| `resolution_note` / `handled_at` | 처리 결과·완료 시각 |
| `created_at` / `updated_at` | 접수·상태 수정 시각 |

새 신고는 실제 대상 FK를 정확히 하나 가져야 합니다. 대상이 이후 삭제되면 FK만 `NULL`이 되고 `target_entity_id`와 `target_snapshot`은 유지됩니다.

## admin_actions

| 컬럼 | 의미 |
|---|---|
| `id` | 감사 로그 식별자 |
| `actor_user_id` / `actor_username` / `actor_role` | 조치 당시 운영자 정보 |
| `action_type` | 정지·복구·숨김·신고 처리 등 |
| `target_type` / `target_entity_id` | 대상 종류·ID 스냅샷 |
| `target_snapshot` | 대상 당시 정보 JSON 객체 |
| `reason` | 조치 사유 |
| `metadata` | 상태 전이 등 부가 JSON |
| `created_at` | 조치 시각 |

`admin_actions`에는 대상 FK가 없으며 UPDATE·DELETE를 금지합니다.

## 주요 제약과 인덱스

| 대상 | 규칙·목적 |
|---|---|
| 지역 계층 | 순환 참조와 부모·자식 국가 코드 불일치 차단 |
| 공개 게시글 | 대표 지역과 최소 1개 장소 필수 |
| 게시글 장소 | 대표 지역 또는 하위 지역만 허용 |
| 피드 인덱스 | `published + public + visible`만 대상 |
| 댓글 노출 | 사용자 삭제·운영 삭제가 모두 없는 행만 노출 |
| 진행 중 신고 | 같은 신고자·종류·대상 ID의 중복 신고 차단 |
| 신고 증거 | 대상 ID·스냅샷·사유 변경 차단 |
| 영구 삭제 | 열린 게시글·댓글 신고가 있으면 보류 |
| 조회수 | 공개 범위 접근 확인 후 전용 함수로 증가 |
| 연결 테이블 | 식별자·소유자·생성 시각 UPDATE 차단 |
| 숨김 게시글 자식 콘텐츠 | 이미지·장소·태그 INSERT·UPDATE·DELETE 차단. 부모 영구 삭제의 FK 연쇄 삭제는 허용 |
| DB 보안 역할 | NOLOGIN·비슈퍼유저·비복제·NOBYPASSRLS 및 다른 역할 상속 금지 |


# 추천 P0 테이블

## recommendation_snapshot

| 컬럼 | 의미 |
|---|---|
| `snapshot_id` | 코어 snapshot 문자열 식별자 |
| `snapshot_kind` | ranking input / diversity metadata / exploration metadata / ranking result / exposure event |
| `schema_version` | payload 계약 버전 |
| `canonicalization_version` | canonical bytes 생성 규칙 버전 |
| `hash_algorithm` / `content_hash` | kind·schema를 포함한 도메인 분리 SHA-256 |
| `canonical_payload` | replay 기준 UTF-8 canonical bytes |
| `payload_json` | 운영 조회용 선택적 JSONB |
| `payload_size_bytes` | bytes 길이 검증값 |

## recommendation_run

| 컬럼 | 의미 |
|---|---|
| `run_id` / `request_id` | 추천 실행과 요청 식별자 |
| `run_mode` / `run_status` | shadow·canary·live 및 성공·fallback·실패 |
| `user_id` / `session_id` / `context_id` / `surface` | 실행 소유권·화면·cursor 결속 기준 |
| `reference_time` | 점수 계산 기준 시각 |
| `*_snapshot_id` | ranking·diversity·exploration 입력 및 ranking result snapshot |
| `*_policy_version` | 정책 버전 벡터 |
| `exploration_seed` | 결정적 exploration seed |
| `ranking_status` / `ranking_empty_reason` | ranked 또는 empty 결과 |
| `*_candidate_count` | 입력·점수·최종·terminal partition |
| `result_fingerprint` | 전체 결과 canonical fingerprint |
| `core_build_id` / `duration_ms` | Java Core 빌드와 실행 시간 |

## recommendation_run_candidate

| 컬럼 | 의미 |
|---|---|
| `run_id` / `absolute_rank` | 실행별 고정 전체 순위 |
| `entity_key` | `post:<posts.id>` identity |
| `source_entity_id` | 삽입 당시 원본 게시글 ID snapshot |
| `origin` | personalized 또는 exploration |
| `score` / `score_is_negative_zero` | 점수와 signed-zero provenance |
| `base_absolute_rank` / `diversified_absolute_rank` | ranking·diversity 이전 순위 |
| exploration 관련 컬럼 | quality, exposure count, seed tie-break, pool/target rank |
| `provenance` | 추가 결정 근거 JSONB |

## recommendation_run_terminal_candidate

| 컬럼 | 의미 |
|---|---|
| `score_status` | not_applicable 또는 hard_excluded |
| `not_applicable_reason` | 적용 불가 이유 |
| `hard_exclusion_reason` | 강제 제외 이유 |
| `audit_payload` | 검증·eligibility 감사 정보 |

## recommendation_exposure_event / recommendation_exposure_candidate

| 컬럼 | 의미 |
|---|---|
| `event_id` / `idempotency_key` | 이벤트 식별과 재시도 식별 |
| `payload_fingerprint` / `canonical_payload` | 이벤트 payload 충돌 판정 원본 |
| `run_id` / user·session·context | 원 추천 실행 결속 |
| `replay_key` / `page_fingerprint` | exact replay 및 페이지 검증 |
| page rank 컬럼 | 실제 제공된 순위 구간과 후보 수 |
| 후보 `origin` / score / provenance | 실제 노출 후보의 코어 결과 증거 |

## recommendation_behavior_event

| 컬럼 | 의미 |
|---|---|
| `event_id` / `idempotency_key` | 행동 이벤트와 재시도 식별 |
| `user_id` / `session_id` / `run_id` | 사용자·세션·추천 실행 결속 |
| `event_type` | view, click, like, save, report 등 Core event vocabulary |
| `entity_type` / `entity_key` / `source_entity_id` | 대상 identity |
| `occurred_at` / `received_at` | 사용자 행동 시각과 서버 수신 시각 |
| `metadata` | surface, position, query, dwell time 등 제한된 메타데이터 |
