# P0 DB 기준선 수렴 결정

## 결론

추천 P0 저장소의 정식 데이터베이스 기준선은 사용자가 제공한 **Journey Connect DB v1.8**이다.

이 기준선은 다음 테이블명을 사용한다.

```text
app_users
posts
regions
places
comments
post_likes
bookmarks
```

현재 백엔드 저장소의 기존 기본 Flyway 경로는 별도의 과거 스키마를 사용한다.

```text
user_account
journey_post
region
post_comment
post_like
bookmark
```

두 스키마는 이름만 다른 동일 구조가 아니다. 게시글 생명주기, 운영 숨김, 지역 계층, 신고 보존, 권한 역할도 서로 다르다. 따라서 추천 테이블을 기존 `db/migration`의 V1·V2 뒤에 바로 추가하면 잘못된 FK 기준선이 형성되거나 빈 DB 부트스트랩이 실패한다.

## 이번 배치의 처리

- DB v1.8의 `01~06`은 바이트 단위로 보존한다.
- 신규 추천 저장소는 v1.9의 `07~09`로만 추가한다.
- Flyway 호환본은 기본 경로와 분리된 다음 위치에 둔다.

```text
jc-backend/src/main/resources/db/migration-v1_8/
├─ V7__recommendation_storage.sql
├─ V8__recommendation_security_roles.sql
└─ README.md
```

- smoke test는 마이그레이션이 아니므로 다음 테스트 전용 위치에 둔다.

```text
jc-backend/src/test/resources/db/recommendation/
├─ 09_recommendation_smoke_test.sql
└─ README.md
```

- 기본 `db/migration`에는 이번 파일을 넣지 않는다. 현재 레거시 V1·V2 뒤에서 자동 실행되는 사고를 막기 위한 fail-closed 결정이다.

## 기존 v1.8 DB에 적용하는 방법

DBeaver 또는 `psql`에서 다음 순서로 실행한다.

```text
07_recommendation_storage.sql
08_recommendation_security_roles.sql
09_recommendation_smoke_test.sql
```

`08`은 PostgreSQL 슈퍼유저 또는 객체 소유권과 `CREATEROLE`을 모두 가진 전용 마이그레이션 계정으로 실행한다.

Flyway를 사용할 경우 기존 수동 적용 v1.8 DB를 version 6으로 baseline한 뒤 **분리 위치만** 사용한다.

```text
locations = classpath:db/migration-v1_8
baselineOnMigrate = true
baselineVersion = 6
```

정상 애플리케이션 시작 시 이 설정을 바로 사용해서는 안 된다. 현재 JPA 엔티티가 아직 과거 테이블명에 매핑돼 있기 때문이다.

## 다음 수렴 작업

P0-2에서 다음 중 하나를 선택해야 한다.

1. 백엔드 JPA·repository·서비스를 DB v1.8 테이블에 맞춰 전환한다.
2. v1.8과 레거시 스키마 사이의 일회성 데이터 이관 마이그레이션을 작성한 뒤 레거시 테이블을 제거한다.

새 추천 코드가 과거 `journey_post`를 기준으로 작성되는 것은 금지한다. 추천 후보의 정식 원본은 `posts.id`다.

## 금지 사항

- v1.8 `01~06` 수정
- 기본 Flyway V1·V2 뒤에 V7·V8 무조건 배치
- `app_users/posts`와 `user_account/journey_post`를 동시에 운영 모델로 사용
- 추천 로그 FK를 이유로 게시글 1년 정리 정책을 영구 차단
- 추천 코어의 문자열 ID를 DB bigint 타입으로 변경
