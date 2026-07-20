# P0-3 Backend Role Routing

## 목표

Spring 백엔드의 단일 데이터소스 연결이 원본 로그인 권한으로 SQL을 실행하지 않도록, 모든 DB 접근을 역할이 고정된 트랜잭션으로 제한합니다.

## 런타임 로그인 생성

비밀번호는 배포 secret에서 주입하고 저장소에 기록하지 않습니다.

```sql
CREATE ROLE jc_backend
  LOGIN
  NOINHERIT
  NOSUPERUSER
  NOCREATEDB
  NOCREATEROLE
  NOREPLICATION
  NOBYPASSRLS
  PASSWORD 'deployment-secret';

GRANT jc_app, jc_auth, jc_recommendation TO jc_backend;
```

`jc_backend`에는 직접 테이블·시퀀스·함수 권한을 부여하지 않습니다.

## 애플리케이션 설정

```text
DB_USERNAME=jc_backend
DB_PASSWORD=<secret>
DB_ROLE_ROUTING_VERIFY=true
DB_ROLE_ROUTING_REQUIRE_RESTRICTED_LOGIN=true
```

애플리케이션 시작 시 다음 조건을 모두 검증합니다.

- 로그인 역할이 `NOSUPERUSER`
- 로그인 역할이 `NOBYPASSRLS`
- 로그인 역할이 `NOINHERIT`
- `SET LOCAL ROLE jc_app` 가능
- `SET LOCAL ROLE jc_auth` 가능
- `SET LOCAL ROLE jc_recommendation` 가능

## 트랜잭션 규칙

- 일반 콘텐츠: `DatabaseRole.APP`
- 회원가입·로그인·프로필 자격증명: `DatabaseRole.AUTH`
- 추천 후보·snapshot·run·event: `DatabaseRole.RECOMMENDATION`
- 전파 모드는 `REQUIRED`, `REQUIRES_NEW`만 허용
- 하나의 트랜잭션 안에서 역할 변경 금지
- `jc.current_user_id`는 검증된 JWT `sub`만 transaction-local로 주입
- 익명 요청은 transaction-local 빈 값으로 명시 초기화
