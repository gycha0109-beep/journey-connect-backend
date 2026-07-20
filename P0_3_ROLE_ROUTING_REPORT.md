# Journey Connect P0-3 데이터베이스 역할 라우팅 구현 보고서

## 1. 작업 정보

| 항목 | 내용 |
|---|---|
| 작업명 | P0-3 최소 권한 데이터베이스 역할 라우팅 |
| 기준 버전 | P0-2.2 Integration Test Fixes Batch 9 |
| 결과 버전 | P0-3 Role Routing Batch 10 |
| 백엔드 | Java 21 / Spring Boot 3.5.16 / Hibernate 6 / PostgreSQL |
| 데이터베이스 | Journey Connect DB v2.1 |
| 상태 | 컴파일·정적 보안 계약·Java 추천 코어 회귀 통과, PostgreSQL 통합 런타임은 사용자 환경 재검증 필요 |

## 2. 목표

기존 백엔드는 단일 데이터베이스 로그인으로 연결되지만, 요청 처리 중 어떤 권한 집합을 사용해야 하는지 애플리케이션 계층에서 강제하지 않았다. P0-3은 다음 경계를 추가한다.

1. 일반 서비스, 인증 서비스, 추천 서비스가 서로 다른 PostgreSQL 역할을 사용한다.
2. 역할은 트랜잭션 시작 직후 `SET LOCAL ROLE`로 고정한다.
3. 같은 트랜잭션 안에서 역할 변경을 금지한다.
4. 검증된 JWT `sub`만 `jc.current_user_id` transaction-local 설정에 사용할 수 있다.
5. 일반 앱 역할은 이메일, 비밀번호 해시, 사용자 권한 컬럼을 SQL 수준에서 읽을 수 없다.
6. 운영 로그인은 직접 데이터 권한이나 객체 소유권 없이 세 런타임 역할만 위임받는다.
7. 역할 경계 없는 일반 Spring `@Transactional` 사용을 정적 게이트에서 차단한다.

## 3. 역할 모델

| 애플리케이션 역할 | PostgreSQL 역할 | 주요 책임 |
|---|---|---|
| `DatabaseRole.APP` | `jc_app` | 게시물, 크루, 지역, 공개 사용자 프로필 |
| `DatabaseRole.AUTH` | `jc_auth` | 로그인 자격증명, 리프레시 토큰, 본인 프로필 변경 |
| `DatabaseRole.RECOMMENDATION` | `jc_recommendation` | 후보 조회, 스냅샷, run, 노출, 행동 이벤트, replay |

런타임 연결 계정은 위 세 역할의 멤버이지만 `NOINHERIT`여야 한다. 따라서 역할을 명시적으로 선택하지 않은 SQL에는 데이터 접근 권한이 없다.

## 4. 애플리케이션 구현

### 4.1 역할 제한 트랜잭션

신규 패키지:

```text
com.jc.backend.database
├─ DatabaseRole
├─ DatabasePropagation
├─ DatabaseTransactional
├─ DatabaseTransactionalAspect
├─ DatabaseRoleBoundary
├─ DatabaseRequestIdentity
├─ DatabaseRequestIdentityFilter
└─ DatabaseRoleCapabilityVerifier
```

`@DatabaseTransactional`은 다음 항목을 컴파일 단계에서 제한한다.

- 역할: APP / AUTH / RECOMMENDATION
- propagation: REQUIRED / REQUIRES_NEW
- 읽기 전용 여부
- isolation과 timeout

`NOT_SUPPORTED`, `SUPPORTS`, `NEVER`와 같이 역할 경계 밖에서 SQL이 실행될 수 있는 propagation은 제공하지 않는다.

### 4.2 트랜잭션 역할 불변성

`DatabaseRoleBoundary`는 다음 순서로 동작한다.

```text
Spring transaction 시작
→ SET LOCAL ROLE <allowlisted role>
→ current_role 검증
→ SET LOCAL jc.current_user_id
→ role/user identity를 transaction resource에 바인딩
→ 트랜잭션 종료 시 정리
```

한 트랜잭션 안에서 다른 역할을 요청하면 즉시 실패한다. `REQUIRES_NEW`로 외부 트랜잭션이 suspend된 경우에는 내부 역할과 사용자 컨텍스트를 별도로 적용하고, 내부 종료 후 외부 컨텍스트를 복원한다.

### 4.3 요청 사용자 ID 신뢰 경계

`DatabaseRequestIdentityFilter`는 Spring Security 인증이 완료된 뒤 실행된다. 인증 객체가 `JwtAuthenticationToken`이고 `sub`가 양의 정수일 때만 사용자 ID를 바인딩한다.

다음 입력은 데이터베이스 사용자 ID로 사용하지 않는다.

- 요청 파라미터
- 임의 헤더
- 컨트롤러 또는 서비스 메서드 인자
- 클라이언트가 보낸 JSON 필드

익명 요청은 매 트랜잭션마다 `jc.current_user_id`를 빈 문자열로 초기화하여 pooled connection의 이전 값이 재사용되지 않도록 했다.

### 4.4 사용자 매핑 분리

`app_users`에 대한 엔티티를 두 경계로 분리했다.

- `AuthAccount`: AUTH 역할 전용, 이메일과 비밀번호 해시 포함
- `UserAccount`: APP 역할용 안전 매핑

APP 매핑의 이메일과 비밀번호 컬럼은 Hibernate SELECT에서 `NULL`로 치환하며 수정도 금지한다. 역할 컬럼은 APP 매핑에서 제거했다. 따라서 `jc_app` 트랜잭션이 일반 사용자 프로필을 읽을 때 credential 컬럼을 SQL에 포함하지 않는다.

### 4.5 서비스 역할 적용

| 영역 | 적용 역할 |
|---|---|
| `AuthService`, 리프레시 토큰, 본인 프로필 변경 | AUTH |
| `CrewService`, `PostService`, `RegionService`, 공개 사용자 콘텐츠 | APP |
| 추천 후보·snapshot·run·exposure·behavior·replay 저장소 | RECOMMENDATION |

메인 Java 코드의 일반 Spring `@Transactional` import와 annotation은 제거했다.

## 5. 데이터베이스 v2.1

기존 v2.0의 `01~12` SQL은 해시 기준으로 변경하지 않았다. 다음 증분만 추가했다.

### `13_backend_role_routing.sql`

- AUTH 역할의 프로필 조회·수정 권한 정의
- APP 역할의 credential/authority 컬럼 거부 재확인
- 리프레시 토큰 접근을 AUTH로 제한
- APP와 RECOMMENDATION의 refresh-token 접근 제거
- 단일 `NOINHERIT` 로그인 + `SET LOCAL ROLE` 운영 모델 문서화

### `14_backend_role_routing_smoke_test.sql`

- APP 공개 프로필 조회 성공
- APP 이메일·비밀번호·role 조회 실패
- AUTH credential 조회 성공
- AUTH role/account_status 변경 실패
- APP/RECOMMENDATION refresh-token 접근 실패
- AUTH refresh-token CRUD 성공

## 6. 운영 로그인 계약

예시:

```sql
CREATE ROLE jc_backend
  LOGIN
  NOINHERIT
  NOSUPERUSER
  NOCREATEDB
  NOCREATEROLE
  NOREPLICATION
  NOBYPASSRLS
  PASSWORD '<secret>';

GRANT jc_app, jc_auth, jc_recommendation TO jc_backend;
```

금지 사항:

- `jc_admin` 멤버십
- public schema 테이블·컬럼·루틴에 대한 직접 grant
- public schema 객체 소유권
- 데이터베이스 또는 public schema 소유권
- `INHERIT`, `SUPERUSER`, `BYPASSRLS`

`DatabaseRoleCapabilityVerifier`는 애플리케이션 시작 시 위 속성과 역할 전환 가능 여부를 검사하고 잘못된 설정이면 fail-closed로 부팅을 중단한다.

## 7. 독립 리뷰에서 발견하고 수정한 결함

### 7.1 트랜잭션 밖 검증 조회

초기 구현에서 좋아요·북마크 외부 메서드가 `NOT_SUPPORTED`였기 때문에 사전 검증 SELECT가 원본 로그인 권한으로 실행될 수 있었다.

수정:

- 외부 검증을 APP read-only `REQUIRED` 트랜잭션으로 변경
- 실제 쓰기는 APP `REQUIRES_NEW`로 유지
- transactionless propagation 자체를 enum과 정적 게이트에서 제거

### 7.2 서비스 인자 기반 사용자 GUC 재설정

`PostViewCounter`가 서비스 인자로 받은 사용자 ID를 `jc.current_user_id`에 다시 설정하고 있었다. 이는 검증된 JWT 전용 경계를 우회할 수 있었다.

수정:

- `PostViewCounter`의 사용자 ID 인자 제거
- `jc.current_user_id` setter를 `DatabaseRoleBoundary` 한 곳으로 제한
- 정적 게이트가 다른 setter 출현을 거부

### 7.3 과도한 런타임 로그인 권한

역할 전환 가능 여부만 검사하면 로그인 계정이 슈퍼유저, BYPASSRLS, INHERIT 또는 객체 소유자인 경우 경계가 무력화될 수 있었다.

수정:

- `NOSUPERUSER`, `NOBYPASSRLS`, `NOINHERIT` 검증
- recursive membership allowlist 검증
- 직접 table/column/routine/usage grant 차단
- public schema relation/function, database, schema 소유권 차단

## 8. 테스트

신규 PostgreSQL 통합 테스트:

```text
DatabaseRoleRoutingIntegrationTest
```

검증 대상:

- JWT subject → transaction-local 사용자 ID
- APP credential 컬럼 접근 거부
- APP-safe `UserAccount` 조회
- AUTH credential 조회와 posts 접근 거부
- RECOMMENDATION posts 접근과 refresh token 접근 거부
- 익명 GUC 초기화
- 같은 역할 `REQUIRES_NEW` suspend/resume 복원
- 동일 transaction의 cross-role 전환 거부
- 제한된 실제 로그인 capability verifier 통과
- INHERIT 로그인 capability verifier 실패

## 9. 검증 결과

| 게이트 | 결과 |
|---|---|
| Gradle `testClasses` 오프라인 컴파일 | PASS |
| P0 storage 정적 계약 | PASS |
| P0-2 convergence 정적 계약 | PASS |
| P0-3 role-routing 정적 계약 | PASS |
| DB v2.0 → v2.1 `01~12` 무변경 | PASS |
| DB v2.1 ↔ Testcontainers `01~14` exact match | PASS |
| 일반 Spring `@Transactional` 잔존 검사 | PASS — 없음 |
| transactionless propagation 검사 | PASS — 차단 |
| request identity setter 단일화 | PASS |
| 제한 로그인 startup gate 정적 검사 | PASS |
| Java recommendation core Foundation~Wave 7 | PASS |
| `CursorCodecTest` | PASS |
| GitHub Actions YAML parsing | PASS |

실제 실행 결과:

```text
Gradle testClasses: BUILD SUCCESSFUL
Recommendation core check: BUILD SUCCESSFUL
CursorCodecTest: BUILD SUCCESSFUL
P0-3 static gate: PASS
```

## 10. 검증 제한

현재 실행 환경에는 Docker daemon과 PostgreSQL 서버가 없어 `DatabaseRoleRoutingIntegrationTest`를 Testcontainers로 실제 구동하지 못했다. 소스와 테스트 컴파일, SQL exact-copy, 권한 계약 정적 검증은 완료했지만 다음 사용자 로컬 게이트를 통과하기 전까지 PostgreSQL 런타임 완료를 주장하지 않는다.

## 11. 사용자 로컬 검증 명령

압축을 푼 프로젝트 루트에서 실행한다.

```powershell
.\jc-backend\gradlew.bat -p .\jc-backend p0Verification --stacktrace

Push-Location .\jc-backend
.\gradlew.bat clean :test --stacktrace
.\gradlew.bat :jc-recommendation-core:check --stacktrace
Pop-Location
```

## 12. 배포 전 체크리스트

- [ ] DB v2.1 `01~14` 적용
- [ ] `jc_backend` 로그인 생성
- [ ] `NOINHERIT`, `NOSUPERUSER`, `NOBYPASSRLS` 확인
- [ ] 세 역할만 membership 부여
- [ ] 직접 데이터 grant 및 객체 소유권 없음 확인
- [ ] `DB_USERNAME=jc_backend` 설정
- [ ] `require-restricted-login=true` 유지
- [ ] 전체 PostgreSQL 통합 테스트 통과
- [ ] startup capability verifier 통과

## 13. 다음 작업

P0-3 사용자 로컬 게이트 통과 후 다음 작업은 **P0 Spring 추천 orchestration application service**다.

범위:

1. candidate retrieval
2. DB row → Java core input mapper
3. Java core 직접 호출
4. trusted input/result snapshot 저장
5. recommendation run 및 전체 rank 저장
6. SHADOW 모드 실행
7. 장애 시 기존 최신순 피드 fallback
