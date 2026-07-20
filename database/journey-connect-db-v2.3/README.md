# Journey Connect DB v2.3

로컬 PostgreSQL과 DBeaver에서 사용하는 Journey Connect 초기 데이터베이스 패키지입니다.

## 실행 순서

빈 데이터베이스에서 아래 순서대로 실행합니다.

1. `01_initial_schema.sql`
2. `02_seed.sql` — `06_security_smoke_test.sql` 실행 전 필요
3. `03_smoke_test.sql`
4. `04_admin_support.sql`
5. `05_security_roles.sql`
6. `06_security_smoke_test.sql`
7. `07_recommendation_storage.sql`
8. `08_recommendation_security_roles.sql`
9. `09_recommendation_smoke_test.sql`
10. `10_backend_runtime.sql`
11. `11_backend_runtime_security_roles.sql`
12. `12_backend_runtime_smoke_test.sql`
13. `13_backend_role_routing.sql`
14. `14_backend_role_routing_smoke_test.sql`
15. `15_backend_role_runtime_fix.sql`
16. `16_backend_role_runtime_fix_smoke_test.sql`
17. `17_recommendation_run_exploration_partition_fix.sql`
18. `18_recommendation_run_exploration_partition_fix_smoke_test.sql`

`03`, `06`, `09`, `12`, `14`, `16`, `18`은 마지막에 `ROLLBACK`되므로 테스트 데이터가 남지 않습니다. 역할을 생성·검증하는 스크립트는 PostgreSQL 슈퍼유저 또는 **해당 객체 소유권과 `CREATEROLE` 권한을 모두 가진 계정**으로 실행합니다. 보안 smoke test는 실제 `SET ROLE` 검증이 가능해야 합니다.

> 빈 DB에는 v2.3의 `01~18`을 순서대로 적용합니다. 이미 v2.2를 적용한 DB에는 `17~18`만 실행합니다. v2.1 적용 DB는 `15~18`, v2.0 적용 DB는 `13~18`, v1.9 적용 DB는 `10~18`, v1.8 적용 DB는 `07~18`을 순서대로 적용합니다.

## 포함 범위

- 사용자와 개인 블로그형 프로필
- 국가·도시·세부 지역 계층
- 장소, 게시글, 이미지, 태그
- 댓글, 좋아요, 북마크, 팔로우
- 지역별 피드·탐색 조회 기반
- 게시글 논리 삭제 및 1년 보관
- 관리자·모더레이터, 신고, 감사 로그
- 일반 API·인증 API·관리자 API의 PostgreSQL 권한 분리
- 추천 snapshot·run·전체 순위·terminal audit·exposure·behavior 신뢰 저장소
- 추천 실행 전용 `jc_recommendation` 역할과 append-only 이력 보호
- SHADOW orchestration의 ranking·diversity·exploration·result replay snapshot 및 최종 후보 저장

추천 알고리즘 자체는 Java Core가 담당하며, 이 패키지는 P0 trusted storage만 포함합니다. 크루 확장 추천, 여행 루트, 타임라인, 알림은 포함하지 않습니다.

## 핵심 규칙

- 공개 게시글에는 제목·본문·대표 지역·장소가 필요합니다.
- 게시글 장소는 대표 지역 또는 그 하위 지역에 속해야 합니다.
- 지역 계층의 순환과 부모·자식 국가 코드 불일치를 차단합니다.
- 게시글 작성 상태와 운영 숨김 상태를 분리합니다.
- 댓글의 사용자 삭제와 운영 삭제를 별도 컬럼으로 관리합니다.
- 운영 숨김 게시글의 핵심 본문·상태뿐 아니라 연결 이미지·장소·태그도 운영 복구 전까지 변경할 수 없습니다.
- 운영 삭제 댓글의 본문·사용자 삭제 상태는 운영 복구 전까지 변경할 수 없습니다.
- 신고 접수 시 대상의 ID뿐 아니라 제목·본문·댓글·이미지 URL·장소·태그 등 당시 정보를 `target_snapshot`에 저장합니다.
- 신고 대상이 이후 영구 삭제되어도 신고 ID와 스냅샷은 남습니다.
- 일반 API의 연결 테이블 UPDATE는 필요한 컬럼으로만 제한합니다.
- 조회수는 `increment_post_view(post_id)`로만 증가하며 게시글 공개 범위도 검사합니다.
- 접근할 수 없는 비공개 게시글·댓글은 신고할 수 없습니다.
- 진행 중 신고가 있는 게시글·소속 댓글은 1년 만료 정리에서 제외됩니다.
- 관리자 조치는 `SECURITY DEFINER` 함수로만 수행하고 `admin_actions`에 남깁니다.
- `admin_actions`는 UPDATE·DELETE가 불가능한 append-only 로그입니다.
- 추천 snapshot은 `journey-connect:snapshot:v1` 도메인과 kind·schema version을 포함한 SHA-256을 사용하고, 이벤트는 canonical payload SHA-256을 사용합니다.
- canonical payload는 `BYTEA`로 저장하며 DB가 hash·길이와 snapshot 16 MiB, exposure 2 MiB, behavior 256 KiB 상한을 재검증합니다.
- 추천 snapshot·run·candidate·exposure·behavior는 append-only이며 UPDATE·DELETE할 수 없습니다.
- run은 ranking·diversity·exploration 입력 snapshot과 immutable ranking result snapshot을 함께 참조합니다.
- 입력 후보는 최종 ranked 후보와 terminal 후보로 완전 분할되며, exploration 삽입으로 final-ranked 수가 scored 수보다 커질 수 있습니다.
- ranked·terminal 후보는 삽입 시 게시글 공개 상태·사용자 접근 가능성·작성자 활성 상태를 검증합니다.
- personalized score·exploration quality는 `[0,1]`, seeded tie-break key는 unsigned 32-bit 범위를 강제합니다.
- 노출 이벤트는 run의 user·session·context·surface와 결속되고, 노출 후보는 저장된 run 후보와 rank·identity·origin·score가 정확히 일치해야 합니다.
- 추천 이력은 게시글 영구 삭제 이후에도 유지되도록 `source_entity_id`를 감사 스냅샷으로 보존합니다.

## 요청 사용자 컨텍스트

인증된 백엔드 트랜잭션마다 서버가 검증한 사용자 ID를 설정합니다.

```sql
SELECT set_config('jc.current_user_id', :verified_user_id::text, true);
```

세 번째 인자는 반드시 `true`여야 하며, 클라이언트가 제출한 ID를 그대로 사용하면 안 됩니다.

## 런타임 역할

| 역할 | 용도 |
|---|---|
| `jc_app` | 일반 콘텐츠 API |
| `jc_auth` | 회원가입·로그인 및 비밀번호 해시 접근 |
| `jc_admin` | 관리자 조회와 관리자 함수 실행 |
| `jc_security_owner` | 로그인 불가 보안 함수 소유자 |
| `jc_recommendation` | 추천 후보 조회와 append-only 이력 INSERT |

PostgreSQL 역할은 클러스터 전체에 존재하므로 이 다섯 이름을 Journey Connect 전용으로 사용해야 합니다. 기존 같은 이름의 역할이 로그인·슈퍼유저·복제·BYPASSRLS 권한이나 다른 역할 상속을 가지면 `05`가 실패합니다.

Spring 백엔드는 **직접 테이블 권한이 없는 단일 로그인 역할**로 접속하고, 각 서비스 트랜잭션 시작 시 `SET LOCAL ROLE`로 `jc_app`, `jc_auth`, `jc_recommendation` 중 하나만 선택합니다. 로그인은 반드시 `NOINHERIT`, `NOSUPERUSER`, `NOBYPASSRLS`여야 합니다. 비밀번호는 SQL 파일이나 Git에 저장하지 않습니다.

```sql
CREATE ROLE jc_backend
  LOGIN
  NOINHERIT
  NOSUPERUSER
  NOCREATEDB
  NOCREATEROLE
  NOREPLICATION
  NOBYPASSRLS
  PASSWORD '배포 환경의 secret으로 주입';

GRANT jc_app, jc_auth, jc_recommendation TO jc_backend;
```

`jc_backend` 자체에는 테이블·시퀀스·함수 권한을 직접 부여하지 않습니다. 관리자 기능은 별도 트랙이며, 현재 일반 백엔드 로그인에 `jc_admin`을 부여하지 않습니다. 애플리케이션은 기본적으로 제한 로그인 여부와 세 역할 전환 가능 여부를 시작 시 검증합니다.

## 애플리케이션 계층 책임

`jc_app`은 여러 최종 사용자가 공유하는 백엔드 역할입니다. 다음 항목은 Spring Security·서비스 계층에서 공통으로 강제해야 합니다.

- `account_status = active`
- 게시글·댓글·이미지·장소 연결·태그 연결·좋아요·북마크·팔로우의 행 소유권
- 피드·상세 조회 시 `status`, `visibility`, `moderation_status`, 댓글 삭제 상태 필터링
- 장소 등록 시 `created_by_user_id`와 인증 사용자 일치
- 인증 사용자 ID와 요청 본문의 작성자 ID 일치
- 공개 API 응답에서 계정 상태·이메일·비밀번호 해시 제외
- 기존 장소 정정은 운영자 또는 별도 검수 절차로 처리
- 이미지 파일 자체의 삭제·보존은 DB 외부 스토리지 정책으로 별도 관리
- 추천 persistence adapter는 `jc_recommendation` 연결을 사용하고 `jc_app`으로 추천 이력을 직접 기록하지 않음
- idempotency key 충돌 시 기존 payload fingerprint를 비교해 동일 재시도와 409 충돌을 구분

## 검증 기준

v1.8 기준선인 `01~06`은 PostgreSQL 15.18과 18.4에서 전체 순차 실행, `05` 재실행, 실제 `SET ROLE` 보안 테스트를 통과했습니다. v1.9의 `07~09`, v2.0의 `10~12`, v2.1의 `13~14`, v2.2의 `15~16`, v2.3의 `17~18`은 PostgreSQL 통합 테스트와 Java 정적 계약 게이트에 포함됩니다. 실제 적용 환경에서도 `18`까지 통과해야 완료로 판정합니다. 데이터베이스 소유자이면서 `CREATEROLE`인 비슈퍼유저로 `01~05`를 적용하는 경로도 두 버전에서 통과했습니다. 세부 내역은 `REVIEW.md`를 확인합니다.
