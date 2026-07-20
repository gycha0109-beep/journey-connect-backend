# Journey Connect P0-2 백엔드 스키마 수렴 구현·독립 리뷰 보고서

## 1. 결론

Journey Connect 백엔드를 기존 레거시 테이블(`user_account`, `journey_post`, `region`)에서 검토 완료된 canonical PostgreSQL 모델(`app_users`, `posts`, `regions`)로 수렴시켰다.

동시에 Java Core 1.0을 변경하지 않고 다음 adapter 경계를 추가했다.

- canonical 게시글 → 추천 후보 projection
- projection → Java Core 입력 mapper
- snapshot/run/ranked-terminal candidate 저장
- exposure/behavior append-only 저장
- replay bundle 조회

구현 후 독립 리뷰를 수행해 공개 범위, 계정 상태, 크루 제약, DB 역할 권한, 멱등성 충돌 검증, CI 기준선의 문제를 추가 보완했다.

## 2. 적용 기준선

- Java: 21
- Spring Boot: 3.5.16
- 추천 Core: `jc-recommendation-core` 1.0.0
- DB 기준선: Journey Connect DB v1.9 (`01~09`)
- 신규 DB 패키지: Journey Connect DB v2.0 (`01~12`)
- 테스트 DB: PostgreSQL 15 Testcontainers
- DB 실행 CI: PostgreSQL 15 / 18

## 3. DB v2.0 증분 구조

기존 `01~09`는 바이트 단위로 변경하지 않았다.

신규 파일:

- `10_backend_runtime.sql`
- `11_backend_runtime_security_roles.sql`
- `12_backend_runtime_smoke_test.sql`

### 3.1 런타임 테이블

- `refresh_tokens`
- `crews`
- `crew_members`

### 3.2 추가 컬럼·인덱스

- `regions.center_latitude`
- `regions.center_longitude`
- `app_users.lower(display_name)` unique index
- 지역 좌표·크루 피드·크루 멤버·refresh token 인덱스

### 3.3 크루 무결성

- OWNER 멤버는 `crews.owner_id`와 일치
- 승인·거절 reviewer는 크루 owner와 일치
- reviewer만 단독 수정해도 검증 trigger 실행
- 정확히 한 명의 OWNER 강제
- OWNER+APPROVED 수가 capacity를 초과하지 않음
- aggregate 검사는 deferred constraint trigger로 트랜잭션 종료 시 검증

## 4. JPA canonical 수렴

| 기존 백엔드 | canonical DB |
|---|---|
| `user_account` | `app_users` |
| `journey_post` | `posts` |
| `region` | `regions` |
| `post_image` | `post_images` |
| `post_comment` | `comments` |
| `post_like` | `post_likes` |
| 기존 bookmark 모델 | `bookmarks` 복합 PK |

추가 매핑:

- `places`
- `post_places`
- `refresh_tokens`
- `crews`
- `crew_members`

게시글 상태는 boolean 한 개가 아니라 다음 canonical 상태를 사용한다.

- `status`: draft / published / deleted
- `visibility`: public / followers / private
- `moderation_status`: visible / hidden
- `published_at`, `deleted_at`, `purge_after`

## 5. 공개 정책 수렴

피드·검색·공개 프로필은 다음 조건을 적용한다.

- published
- public
- moderation visible
- active author

상세·댓글·북마크·추천 후보는 canonical 함수 `public.can_user_view_post(viewer_id, post_id)`를 재사용한다.

추가 보완:

- followers 게시글은 실제 follow 관계가 있어야 조회 가능
- follow가 해제되면 기존 bookmark 목록에서도 즉시 제외
- 숨김·삭제·비활성 작성자 콘텐츠 제외
- 추천 후보는 활성 장소가 한 개 이상 연결된 게시글만 허용

## 6. 비활성 계정 fail-closed

`suspended` 또는 `withdrawn` 사용자는 다음 인증·mutation 경로에서 차단된다.

- login / refresh / current user
- 프로필 조회·수정
- 내 게시글·내 북마크
- 게시글 수정·삭제
- like/unlike
- bookmark/unbookmark
- 댓글 작성·삭제
- 크루 생성·가입·취소·신청 목록·승인·거절

오류 코드는 `USER_INACTIVE`로 통일했다.

## 7. 추천 read adapter

추가 패키지:

`com.jc.backend.recommendation`

구성:

- `RecommendationCandidateSource`
- `RecommendationCandidateRow`
- `RecommendationCoreInputMapper`
- `RecommendationCoreCandidate`

후보 projection은 다음을 한 번에 조회한다.

- post/author/region
- view/like/bookmark count
- tag 목록
- 사용자별 최근 30일 exposure count
- canonical 공개 가능 여부

DB bigint 식별자는 Core 경계에서 String으로 변환한다. Core 내부 타입과 정책은 변경하지 않았다.

## 8. 추천 write/replay adapter

추가 패키지:

`com.jc.backend.recommendation.persistence`

구성:

- `RecommendationSnapshotStore`
- `RecommendationRunStore`
- `RecommendationExposureStore`
- `RecommendationBehaviorStore`
- `RecommendationReplayStore`
- `RecommendationHashing`
- `RecommendationStorageTypes`

### 8.1 검증 보완

- snapshot ID·버전·16 MiB 크기 검증
- run count/status/reason partition 검증
- ranked/terminal source ID 중복 방지
- score finite 및 0~1 범위
- exploration provenance 양수·unsigned 32-bit 범위
- exposure rank·page bounds·candidate uniqueness 검증
- exposure retry 시 event 필드와 candidate 전체 비교
- score signed-zero boolean 별도 비교
- behavior retry 시 user/run/entity/time/metadata 전체 비교
- behavior entity type·positive ID·256 KiB 크기 검증

snapshot의 identity는 canonical bytes와 domain-separated hash다. `payload_json`은 조회 편의를 위한 projection이며 identity로 사용하지 않는다.

## 9. PostgreSQL 통합 테스트

기존 H2 `create-drop` 테스트를 제거하고 canonical PostgreSQL Testcontainers로 전환했다.

Testcontainers bootstrap:

1. PostgreSQL 15-alpine 시작
2. canonical SQL `01~12`를 `psql -v ON_ERROR_STOP=1`로 순차 실행
3. Hibernate `ddl-auto=validate`
4. 기존 API 및 신규 추천 integration test 실행

추가 검증:

- canonical table 존재 및 Hibernate validate
- role function privilege
- followers 공개 정책
- follow 해제 후 bookmark 차단
- 크루 자동승인 제약
- 비활성 계정 mutation 차단
- 추천 후보 mapping
- snapshot/run/exposure/behavior/replay round trip
- exposure·behavior idempotency conflict
- duplicate run candidate 사전 차단
- nonpositive cursor ID 차단

## 10. Flyway 처리

레거시 자동 migration은 다음 위치로 격리했다.

- `db/migration-legacy/`
- `db/migration-v1_8/`

기본 `db/migration/`에는 SQL을 두지 않는다.

현재 canonical DB는 검토된 `database/journey-connect-db-v2.0/01~12` 패키지로 적용하며, canonical history를 Flyway baseline으로 재구성하기 전까지 Flyway 기본값은 disabled다.

Hibernate는 `ddl-auto=validate`만 사용한다.

## 11. 독립 리뷰에서 발견·보완한 항목

| 발견 | 보완 |
|---|---|
| `can_user_view_post` 실행 권한 누락 | `jc_app`, `jc_recommendation`에 함수 EXECUTE만 부여 |
| reviewer만 변경하면 크루 trigger 우회 | trigger UPDATE 열에 `reviewed_by` 추가 |
| 비활성 사용자의 일부 mutation 가능 | user/profile/post/crew 전체 authenticated 경로 차단 |
| bookmark가 followers/private 정책을 우회 | canonical visibility 함수 기반 native query 적용 |
| cursor가 0/음수 ID 수용 | positive ID 검증 추가 |
| run adapter 입력 검증 부족 | count/partition/range/duplicate 검증 추가 |
| exposure retry가 candidate 내용을 충분히 비교하지 않음 | rank/source/origin/score/provenance 전체 비교 |
| behavior retry가 metadata·binding을 충분히 비교하지 않음 | 모든 persisted binding 비교 |
| 기존 CI가 H2/PostGIS 레거시 경로 실행 | Testcontainers 및 PostgreSQL 15·18 canonical 01~12로 교체 |
| SQL 테스트 copy drift 가능 | static exact-copy gate 추가 |

## 12. 권한 아키텍처 주의사항

현재 Spring Boot는 하나의 datasource 설정을 사용하지만 DB 역할은 다음과 같이 분리돼 있다.

- `jc_app`
- `jc_auth`
- `jc_recommendation`
- `jc_admin`

테스트는 schema owner인 `postgres`로 실행한다. 운영에서 최소 권한을 실제로 유지하려면 P0-3에서 auth/app/recommendation용 별도 datasource 또는 명시적 role-routing transaction 경계를 추가해야 한다.

하나의 로그인 역할에 세 그룹 역할을 모두 부여하는 방식은 동작하지만 DB 내부 권한 분리 효과가 약해지므로 임시 로컬 구성으로만 취급한다.

## 13. 검증 결과

완료:

- PostgreSQL SQL AST parse `01~12`: PASS
- DB v1.9 → v2.0 `01~09`: EXACT MATCH
- DB v2.0 → Testcontainers `01~12`: EXACT MATCH
- P0-1 static storage gate: PASS
- P0-2 static convergence gate: PASS
- GitHub Actions YAML parse: PASS
- Java source syntax parse: 90 files PASS
- persistence adapter standalone Java 21 `-Xlint:all -Werror`: PASS
- Java Core Foundation~Wave 7 contracts: PASS
- TypeScript ↔ Java Core 1.0: EXACT MATCH
- reference SHA-256: 418/418 PASS

현재 실행 환경 제한:

- Maven/Gradle 의존성 repository 응답 정지 및 불완전 cache 때문에 백엔드 전체 `testClasses`를 끝까지 실행하지 못함
- 실패 위치는 소스 compile error가 아니라 dependency resolution
- 사용자 로컬 Docker/Gradle에서 최종 `clean test` 필요

## 14. 로컬 최종 게이트

Docker Desktop을 실행한 상태에서:

```powershell
cd C:\Users\hun\Documents\Journey-Connect\jc-backend

.\gradlew.bat clean test --stacktrace
.\gradlew.bat :jc-recommendation-core:check --stacktrace
```

성공하면 P0-2를 종료하고 P0-3 orchestration·role routing·shadow mode로 진입한다.
