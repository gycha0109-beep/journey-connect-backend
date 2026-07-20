# 추천 DB smoke test

`09_recommendation_smoke_test.sql`은 DB v1.8 `01~06`과 추천 마이그레이션 V7·V8 적용 후 PostgreSQL 슈퍼유저로 실행합니다.

```bash
psql --set ON_ERROR_STOP=1 --file 09_recommendation_smoke_test.sql
```

테스트는 실제 `SET ROLE` 권한 경계, canonical hash, run/exposure 무결성, append-only 차단을 확인하고 마지막에 `ROLLBACK`합니다.
