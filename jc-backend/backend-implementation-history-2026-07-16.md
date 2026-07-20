# Journey Connect 백엔드 구현 범위 및 누적 변경 이력

## 구현 API

- 인증: 회원가입, 로그인, 액세스·리프레시 토큰 발급, 토큰 재발급, 로그아웃, 현재 사용자 조회
- 피드·탐색: 커서 기반 최신 피드, 호환용 페이지 피드, 키워드·표준 지역 검색
- 게시물: 작성, 상세, 수정, 삭제, 최대 10개 다중 이미지
- 상호작용: 좋아요, 북마크, 댓글
- 크루: 모집 목록, 상세, 생성, 참가 신청, 승인·거절·취소
- 사용자: 프로필 조회·수정, 공개 개인 게시물, 본인 게시물, 북마크 목록
- 지역: 표준 지역 목록, 키워드 검색, PostGIS 반경 검색

모든 API는 `/api/v1`을 기준으로 하며 Swagger UI는 `/swagger-ui.html`에서 확인합니다.

## 인증

로그인과 회원가입 응답의 `accessToken`을 다음 헤더로 전달합니다. `refreshToken`은 클라이언트의 보안 저장소에 보관하고 `/api/v1/auth/refresh`에서 회전합니다.

```http
Authorization: Bearer {accessToken}
```

비밀번호는 BCrypt 단방향 해시로 저장하고, JWT subject에는 사용자 PK만 저장합니다.

---

# 누적 구현 이력

## P0 — 기본 안정화 및 보안 경계

기존 백엔드 구현본에 다음 항목을 반영했습니다.

- Java/Kotlin 혼합 빌드가 가능하도록 Kotlin JVM·Spring·JPA 플러그인과 런타임 의존성 구성
- 중복 보안 설정 제거 및 Kotlin `SecurityConfig`로 통합
- 비공개 게시물은 작성자만 상세·댓글·본인 목록에서 조회하도록 접근 통제
- 공개 사용자 게시물 API와 본인 게시물 관리 API 분리
- JWT·DB 비밀값의 저장소 기본값 제거 및 환경변수 주입 적용
- Spring Security 401·403과 MVC 예외 응답을 공통 오류 형식으로 통일
- 사용자 프로필 로직을 컨트롤러에서 서비스 계층으로 이동
- 코드 포매팅 및 핵심 보안·권한 규칙 주석 보강
- 보안 오류 응답·게시물 공개 범위 통합 테스트 추가

## P1 — 조회 성능·동시성·API 통합 검증

P0 리뷰본에 다음 항목을 누적 반영했습니다.

### 1. 게시물 목록 N+1 제거

- 피드, 탐색, 공개 사용자 게시물, 본인 게시물, 북마크 목록에서 작성자를 `EntityGraph`로 함께 조회
- 게시물마다 실행하던 좋아요 수·북마크 수 조회를 페이지 단위 집계 쿼리 각각 1회로 변경
- 댓글 목록 작성자도 `EntityGraph`로 함께 조회
- 게시물 상세·권한 검사 조회에서도 작성자를 함께 가져오도록 전용 Repository 메서드 추가

변경 후 게시물 목록 조회 쿼리는 게시물 수에 비례하지 않고 다음 최대 범위로 고정됩니다.

1. 게시물 페이지 조회
2. 페이지 전체 개수 조회
3. 좋아요 수 일괄 집계
4. 북마크 수 일괄 집계

### 2. 크루 목록 N+1 제거

- 크루 목록과 상세 조회에서 소유자를 함께 조회
- 크루마다 개별 실행하던 멤버 수 조회를 페이지 단위 집계 쿼리 1회로 변경

변경 후 크루 목록 조회 쿼리는 다음 최대 범위로 고정됩니다.

1. 크루 페이지 조회
2. 페이지 전체 개수 조회
3. 멤버 수 일괄 집계

### 3. 좋아요·북마크 동시 중복 요청 처리

- 빠른 중복 확인 후 별도 트랜잭션에서 INSERT 수행
- 호출 측의 외부 트랜잭션을 중단해 별도 트랜잭션용 DB 연결을 중첩 보유하지 않도록 구성
- 동시에 들어온 요청이 모두 사전 조회를 통과하더라도 DB 유니크 제약조건으로 중복 데이터 차단
- 경쟁 요청 중 실제 반응이 생성된 경우 뒤 요청도 오류가 아닌 멱등 성공으로 처리
- 같은 좋아요·북마크 요청을 반복해도 최종 데이터는 1건만 유지

### 4. 크루 정원 동시성 제어

- 참가 처리 시 크루 행을 `PESSIMISTIC_WRITE` 잠금으로 조회
- 모집 상태 확인, 중복 참가 확인, 현재 인원 확인, 참가 저장을 동일 트랜잭션에서 수행
- 같은 크루의 동시 참가 요청을 순차 처리하여 정원 초과 방지
- 서로 다른 크루의 참가 요청에는 잠금이 전파되지 않도록 크루 단위로 제한

### 5. 사용자 게시물 공개 범위 유지 검증

- `GET /api/v1/users/{userId}/posts`: 공개 게시물만 반환
- `GET /api/v1/users/me/posts`: 본인의 공개·비공개 게시물 모두 반환
- Security matcher 우선순위와 실제 API 응답을 MockMvc 통합 테스트로 검증

### 6. 추가된 P1 테스트

- `PostListQueryIntegrationTest`
  - 게시물 목록 조회 쿼리가 게시물 수에 비례하지 않는지 검증
  - 좋아요·북마크 집계 결과 검증
- `CrewListQueryIntegrationTest`
  - 크루 목록 조회 쿼리가 크루 수에 비례하지 않는지 검증
  - 소유자·멤버 수 결과 검증
- `PostInteractionIntegrationTest`
  - 좋아요·북마크 동시 요청 및 반복 요청의 멱등성 검증
- `CrewConcurrencyIntegrationTest`
  - 동시 참가 요청에서도 크루 정원이 초과되지 않는지 검증
- `PostApiIntegrationTest`
  - 공개/본인 게시물 API 분리와 반복 좋아요 API 검증

---

# P2 — 데이터 모델 확장 및 운영 기반

P1 누적본에 다음 항목을 추가했습니다.

## 1. Flyway 마이그레이션 전환

- `flyway-core`, PostgreSQL 데이터베이스 모듈 추가
- `ddl-auto`를 `update`에서 `validate`로 변경
- 기존 비어 있지 않은 로컬 스키마를 위한 baseline version `0` 적용
- `V1__baseline_schema.sql`: P0/P1 스키마 기준선
- `V2__p2_domain_extensions.sql`: 지역, 이미지, 크루 승인, 토큰 확장
- 기존 자유 문자열 지역과 대표 이미지를 신규 테이블로 이관하는 데이터 보정 SQL 포함

## 2. 지역 코드·PostGIS 공간 모델

- `Region` 엔티티와 고정 `code`, 국가 코드, 표시명 도입
- 게시물·크루가 `region_id`를 기준으로 지역을 참조
- 기존 응답 호환을 위해 `region_name` 표시명 캐시는 유지
- WGS84 SRID 4326 `Point` 중심 좌표와 GiST 인덱스 추가
- 지역 목록·검색 및 `ST_DWithin` 기반 반경 검색 API 추가
- Hibernate ORM 6 기준 표준 PostgreSQL Dialect를 사용하며 별도 레거시 PostGIS Dialect는 지정하지 않음

## 3. 게시물 다중 이미지

- `post_image` 테이블과 `PostImage` 엔티티 추가
- 게시물당 최대 10개 이미지, 정렬 순서, 대체 텍스트 지원
- 이미지 목록 전체 교체 방식으로 수정 계약 단순화
- 첫 번째 이미지를 기존 `coverImageUrl`과 동기화해 목록 카드 호환 유지
- 빈 이미지 배열을 전달하면 전체 이미지를 삭제

## 4. 크루 참가 신청·승인 상태

- 상태를 `OWNER`, `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`로 확장
- 승인 필요 크루와 즉시 참가 크루를 모두 지원
- 신청 목록 조회, 크루장 승인·거절, 사용자 참가 취소 API 추가
- 승인 시 기존 P1의 크루 행 비관적 잠금과 정원 검사를 그대로 적용
- 목록 응답에 승인 멤버 수와 대기 신청 수를 분리

## 5. 피드 커서 페이지네이션

- `/api/v1/feed`: `createdAt + id` 복합 커서 기반 무한 스크롤
- 동일 생성 시각에도 PK 보조 정렬키를 사용해 중복·누락 방지
- `size + 1` 조회로 `hasNext` 계산, 전체 개수 쿼리 제거
- 기존 페이지 번호 방식은 `/api/v1/feed/page`로 호환 유지

## 6. 리프레시 토큰·로그아웃

- 256비트 난수 기반 불투명 리프레시 토큰 발급
- DB에는 원문 대신 SHA-256 해시만 저장
- 재발급 시 행 잠금 후 기존 토큰 폐기 및 신규 토큰 회전
- 같은 리프레시 토큰의 동시 재사용 중 한 요청만 성공
- 로그아웃은 토큰 폐기 방식이며 반복 호출도 성공 처리

## 7. Repository 물리적 분리

- 게시물·좋아요·북마크·댓글 Repository를 개별 파일로 분리
- 크루·크루 멤버 Repository와 집계 Projection을 개별 파일로 분리
- 신규 지역·리프레시 토큰 Repository도 독립 파일로 구성

## 8. 추가된 P2 테스트

- `AuthRefreshTokenIntegrationTest`: 토큰 회전, 이전 토큰 재사용 차단, 로그아웃 폐기
- `FeedCursorIntegrationTest`: 여러 페이지에서 게시물 중복·누락 방지
- `PostImageIntegrationTest`: 이미지 순서·대표 이미지 동기화·전체 삭제
- `CrewApprovalIntegrationTest`: 대기 신청과 크루장 승인 흐름
- 기존 P1 성능·동시성 테스트를 신규 지역·승인 모델에 맞게 갱신

## P2 API 추가·변경

```text
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/feed?cursor={cursor}&size=20
GET    /api/v1/feed/page?page=0&size=20
GET    /api/v1/regions
GET    /api/v1/regions/nearby?latitude=...&longitude=...&radiusKm=20
POST   /api/v1/crews/{crewId}/join
DELETE /api/v1/crews/{crewId}/join
GET    /api/v1/crews/{crewId}/applications
PATCH  /api/v1/crews/{crewId}/applications/{applicationId}
```

## 실행 환경 요구사항

- PostgreSQL 이미지에 PostGIS 확장이 포함되어 있어야 `V2` 마이그레이션이 성공합니다.
- 현재 로컬 포트 설정은 기존 값 그대로 유지했습니다.
- 테스트 프로필은 H2와 Hibernate `create-drop`을 사용하며 Flyway는 비활성화합니다.

## 검증 명령

```bash
./gradlew clean test
```

이번 P2 작업에서는 구현과 정적 점검까지만 수행하며, P0~P2 최종 종합 리뷰는 다음 작업으로 분리합니다.

---

# PR CI — 최종 리뷰 시 추가

`.github/workflows/backend-pr-ci.yml`을 추가했습니다.

PR 생성·갱신 시 다음 두 검증이 독립적으로 실행됩니다.

1. **Gradle tests (H2)**
   - Java 21
   - Gradle Wrapper 무결성 검증 및 캐시
   - `./gradlew clean test --stacktrace --no-daemon`
2. **PostgreSQL 18 / PostGIS migration smoke**
   - `postgis/postgis:18-3.6-alpine` 서비스 사용
   - 실제 Flyway V1·V2 마이그레이션 실행
   - Hibernate `ddl-auto: validate`를 포함한 애플리케이션 기동 확인
   - `/api-docs` 응답으로 기동 완료 확인

CI 환경의 DB 비밀번호와 JWT Secret은 일회성 테스트 값이며 저장소 운영 Secret을 사용하지 않습니다.
