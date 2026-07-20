# DB v1.8 전용 Flyway 위치

이 디렉터리는 Journey Connect DB v1.8의 `app_users`, `posts`, `regions` 기준선 전용입니다.
현재 기본 `classpath:db/migration`은 과거 `user_account`, `journey_post`, `region` 스키마이므로 두 위치를 동시에 사용하면 안 됩니다.

기존 v1.8 데이터베이스에 Flyway를 연결할 때만 다음 설정을 사용합니다.

```yaml
spring:
  flyway:
    locations: classpath:db/migration-v1_8
    baseline-on-migrate: true
    baseline-version: 6
```

적용 전제:

- DB v1.8 `01~06` 적용 완료
- 마이그레이션 계정이 객체 소유권과 `CREATEROLE` 보유
- 현재 JPA를 v1.8로 수렴시키는 P0-2 작업 완료 전 애플리케이션 자동 시작에 사용 금지

`09_recommendation_smoke_test.sql`은 롤백되는 테스트 파일이므로 Flyway 위치에 포함하지 않습니다.
