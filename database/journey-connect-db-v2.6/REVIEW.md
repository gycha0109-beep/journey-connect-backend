# Journey Connect DB v2.5 누적 리뷰

## v2.5 P0-7 Behavior Runtime

1. run-bound behavior의 사용자·세션·ranked candidate 결속을 강제했습니다.
2. like·unlike·save·unsave 상태 변경과 behavior event를 보안 함수 한 트랜잭션으로 원자화했습니다.
3. event ID와 idempotency key 동시 충돌을 advisory transaction lock으로 직렬화했습니다.
4. 동일 재시도는 dedupe하고 다른 payload 충돌은 상태 변경 없이 거부합니다.
5. `jc_app` 직접 behavior INSERT 금지와 append-only 이력을 유지했습니다.
6. PostgreSQL 15.18에서 `01~22` 전체 순차 실행과 smoke test를 통과했습니다.

---

## v1.9 P0 추천 신뢰 저장소

### 반영 내용

1. `recommendation_snapshot`, `recommendation_run`, 전체 순위·terminal·exposure·behavior 테이블을 추가했습니다.
2. canonical `BYTEA`와 도메인 분리 snapshot SHA-256·event SHA-256을 DB에서 재검증하고 payload 길이·상한을 강제합니다.
3. 7개 추천 이력 테이블 모두 UPDATE·DELETE trigger 차단과 runtime 권한 차단을 적용했습니다.
4. run의 입력·결과 snapshot kind, active user, surface, ranked·terminal 후보 원본 게시글 접근성·작성자 상태, exposure provenance, behavior user/session binding을 검증합니다.
5. `jc_recommendation` NOLOGIN 역할을 추가하고 `jc_app`, `jc_auth`, `jc_admin`과 상속을 분리했습니다.
6. 기존 v1.8 `01~06`의 SHA-256 manifest와 canonical/Flyway exact-copy 검증기를 추가했습니다.
7. PostgreSQL 15·18 service에서 `01~09`와 역할 재실행을 검증하는 CI를 추가했습니다.

### 기준선 충돌 판정

현재 백엔드 기본 Flyway/JPA의 `user_account`, `journey_post`는 DB v1.8의 `app_users`, `posts`와 호환되지 않습니다. 추천 V7·V8은 기본 Flyway 위치에 넣지 않고 `db/migration-v1_8`로 분리했습니다. P0-2에서 JPA 기준선을 v1.8로 수렴하기 전 자동 애플리케이션 마이그레이션에 사용하면 안 됩니다.

### 현재 검증

| 항목 | 결과 |
|---|---:|
| v1.8 `01~06` SHA-256 보존 | 6/6 통과 |
| canonical ↔ Flyway SQL | exact match |
| canonical ↔ smoke resource | exact match |
| SQL lexical/static contract | 통과 |
| PostgreSQL AST parse `01~09` | 통과 |
| 신규 GitHub Actions YAML parse | 통과 |
| PostgreSQL 15·18 실제 CI | workflow 추가, 원격 실행 필요 |

---

## v1.8 최종 보완 및 실행 검증

### 반영한 문제

1. **신고 증거 보존**
   - `reports.target_entity_id`, `reports.target_snapshot`을 추가했습니다.
   - 게시글 신고 시 제목·본문·작성자·이미지 URL·장소·태그를 JSON 스냅샷으로 저장합니다.
   - 댓글·사용자 신고도 신고 당시 정보를 저장합니다.
   - 대상 FK는 `ON DELETE SET NULL`로 변경해 원본이 삭제되어도 신고 이력을 보존합니다.
   - 신고 대상·스냅샷·사유는 접수 후 변경할 수 없도록 트리거를 추가했습니다.

2. **운영 제재 후 콘텐츠 변조 차단**
   - `moderation_status = hidden`인 게시글은 대표 지역·제목·본문·공개 범위·작성 상태를 복구 전까지 변경할 수 없습니다.
   - 숨김 게시글에 연결된 이미지·장소·태그도 INSERT·UPDATE·DELETE할 수 없습니다.
   - `moderation_deleted_at IS NOT NULL`인 댓글은 본문과 사용자 삭제 상태를 복구 전까지 변경할 수 없습니다.
   - 부모 게시글 영구 삭제에 따른 이미지·장소 연결·태그의 FK 연쇄 삭제는 정상 허용됩니다.

3. **연결 테이블 최소 권한**
   - `post_tags`, `post_likes`, `bookmarks`, `follows`의 UPDATE 권한을 제거했습니다.
   - `post_images`는 이미지 메타데이터 컬럼만, `post_places`는 장소·순서·메모만 UPDATE할 수 있습니다.
   - `post_id`, 사용자 ID, 생성 시각 등 식별·감사 컬럼은 수정할 수 없습니다.
   - 트리거 전용 함수는 일반 API의 직접 호출 엔드포인트로 노출하지 않습니다.

4. **비공개 데이터 접근 통제**
   - `can_user_view_post()`를 공통 접근 판정 함수로 추가했습니다.
   - 게시글 신고와 조회수 증가는 `public`, 작성자 본인, 실제 팔로워 공개 조건을 검사합니다.
   - 접근할 수 없는 비공개 게시글과 그 댓글은 신고할 수 없습니다.

5. **영구 삭제 이후 신고 보존**
   - 열린 신고가 있는 게시글 또는 그 소속 댓글은 영구 삭제를 보류합니다.
   - 처리 완료 후 원본 게시글이 삭제되더라도 신고 행과 스냅샷은 유지됩니다.
   - 운영 숨김 상태이면서 작성자 삭제 상태인 게시글도 만료 정리 시 자식 행과 함께 정상 삭제됩니다.

6. **PostgreSQL 역할 안전성**
   - `jc_app`, `jc_auth`, `jc_admin`, `jc_security_owner`는 모두 `NOLOGIN`, 비슈퍼유저, 비복제, `NOBYPASSRLS` 역할이어야 합니다.
   - 같은 이름의 기존 역할이 위험한 속성이나 다른 역할 상속을 가지면 마이그레이션이 실패하도록 했습니다.
   - `jc_security_owner`의 임시 스키마 생성 권한과 마이그레이션 계정 멤버십은 커밋 전에 회수합니다.
   - 데이터베이스 소유자이면서 `CREATEROLE`인 비슈퍼유저 실행 경로도 PostgreSQL 15·18에서 검증했습니다.

7. **보안 Smoke Test 자체 오류 수정**
   - `jc_app` 역할로 신고 테이블을 직접 조회하던 테스트 오류를 수정했습니다.
   - 런타임 역할에서는 콘텐츠 수정만 수행하고, 신고 스냅샷 검증은 역할을 해제한 테스트 계정에서 수행합니다.

### 실제 실행 검증 결과

| 검증 항목 | PostgreSQL 15.18 | PostgreSQL 18.4 |
|---|---:|---:|
| `01_initial_schema.sql` | 통과 | 통과 |
| `02_seed.sql` | 통과 | 통과 |
| `03_smoke_test.sql` | 통과 | 통과 |
| `04_admin_support.sql` | 통과 | 통과 |
| `05_security_roles.sql` | 통과 | 통과 |
| `06_security_smoke_test.sql` | 통과 | 통과 |
| `05_security_roles.sql` 재실행 | 통과 | 통과 |
| 비슈퍼유저 DB 소유자 + `CREATEROLE`로 `01~05` 실행 | 통과 | 통과 |
| 숨김 게시글 본문·이미지·장소·태그 변조 공격 | 차단 | 동일 SQL 기준 통과 |
| 운영 삭제 댓글 변조 공격 | 차단 | 동일 SQL 기준 통과 |
| 숨김·삭제 게시글의 만료 FK 연쇄 삭제 | 통과 | 동일 SQL 기준 통과 |
| 신고 스냅샷 원본 보존 | 통과 | 동일 SQL 기준 통과 |

SQL 6개 파일은 PostgreSQL AST 정적 파싱도 모두 통과했습니다.

### 최종 판정

현재 확정 범위에서 **DBeaver 초기 적용 가능한 버전**입니다. 스키마·마이그레이션 순서·기본 무결성·역할 권한·보안 회귀 테스트를 PostgreSQL 15.18과 18.4에서 실제 실행했습니다.

다만 이 구조는 여러 최종 사용자가 `jc_app`이라는 공유 백엔드 역할을 사용하는 방식입니다. 따라서 일반 CRUD의 행 소유권, 공개 범위 필터링, 정지 사용자 차단은 Spring Security와 서비스 계층에서 공통 검증해야 합니다. 이 항목은 SQL 오류가 아니라 현재 프로젝트가 선택한 애플리케이션 신뢰 경계입니다.

## 누적 변경 이력

### v1.0
- 로컬 PostgreSQL·DBeaver용 초기 구조 작성
- 사용자, 지역, 장소, 게시글, 이미지, 태그, 댓글, 좋아요, 북마크, 팔로우 구성
- 대표 지역, 조회수, 논리 삭제 후 1년 보관 정책 반영

### v1.1
- 공개 게시글 장소 최소 1개 검증
- 대표 지역과 장소 지역 계층 일치 검증
- 지역 순환과 국가 코드 불일치 차단
- 지역·장소 변경 시 기존 공개 게시글 무결성 재검증

### v1.2
- 국가 루트 국가 코드 중복 차단
- 중복 인덱스 제거
- Seed 재실행 동기화 강화

### v1.3
- 사용자 역할·계정 상태 추가
- 신고 테이블과 관리자 감사 로그 추가

### v1.4
- 일반·인증·관리자 DB 역할 분리
- 비밀번호 해시 접근 격리
- 관리자 변경을 보안 함수와 감사 로그로 일원화

### v1.5
- 관리자 역할의 일반 앱 권한 상속 제거
- 트랜잭션 사용자 컨텍스트 도입
- 최소 권한 `jc_security_owner` 도입
- 감사 로그 FK 제거와 스냅샷 보존

### v1.6
- 작성 상태와 운영 상태 분리
- 댓글 사용자 삭제와 운영 삭제 분리
- 열린 신고가 있는 게시글 영구 삭제 보류
- 시퀀스 권한 최소화와 실제 역할 기반 보안 테스트 도입

### v1.7
- 루트 지역 국가 코드 변경 무결성 보완
- 사용자 삭제와 운영 제재 독립성 보장
- 조회수 전용 증가 함수와 직접 수정 차단
- 공유 장소 직접 수정 차단
- 관리자 대상 행 잠금 후 권한 검증

### v1.8
- 신고 당시 콘텐츠 스냅샷과 안정적인 대상 ID 추가
- 원본 삭제 후 신고 보존
- 운영 제재 콘텐츠와 이미지·장소·태그 변경 차단
- 연결 테이블 컬럼 단위 UPDATE 권한 적용
- 게시글 접근 가능 여부 기반 신고·조회수 처리
- 보안 역할 속성·상속 fail-closed 검증
- PostgreSQL 15.18·18.4 실제 마이그레이션 및 공격 시나리오 검증

## 남은 운영 결정

- 회원 탈퇴 시 콘텐츠 익명화 또는 보존 정책
- 실제 이미지 파일의 신고 증거 보존 기간
- 장소 중복 후보 병합·운영자 정정 절차
- 조회수 중복 방지 기준(IP·세션·사용자·시간창)
- `purge_expired_deleted_posts()` 실행 주기와 전용 운영 계정
- 공유 DB 역할 구조에서 Spring 서비스 계층 소유권·공개 범위 검증의 공통화
