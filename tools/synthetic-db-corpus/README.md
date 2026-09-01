# Journey Connect Synthetic DB Corpus

PR #79의 대규모 개발/데모 데이터 generator를 현재 팀 DB 계약에 맞춰 복원한 도구입니다.

## Target

- `YTAK99/Journey-Connect`
- `develop`
- verified schema commit: `e05e6167de59c0eaf93fa466451c3fde0d43f14e`
- Flyway: `V1..V19`

기존 #79 donor는 `legacy_generate.py`로 그대로 보존하고, `generate.py`가 현재 계약을 보정합니다.

보정 내용:

- V19 `post_place` 생성
- `post_image.place_id` 연결
- 서로 다른 batch 간 `user_account.nickname` unique 충돌 방지
- 현재 Crew status domain 검증 (`OWNER/PENDING/APPROVED/REJECTED/CANCELLED`)

## Default corpus

- 180 users
- 1,800 journey posts
- 약 5,400 post images
- 약 1,100 route-like posts
- route-like stop + fallback `post_place`
- 120 crews
- tags / likes / bookmarks / comments long-tail
- 24 destinations, including Tokyo and Hawaii

## Generate

```bash
python tools/synthetic-db-corpus/generate.py
```

출력:

```text
build/synthetic-db-corpus/
  seed.sql
  purge.sql
  manifest.json
```

작은 smoke corpus:

```bash
python tools/synthetic-db-corpus/generate.py \
  --users 20 \
  --posts 50 \
  --crews 10 \
  --batch-id smoke \
  --output-dir build/synthetic-db-smoke
```

## Load

**개발 PostgreSQL 전용입니다. 운영 DB에서는 실행하지 마십시오.**

```bash
psql "$DB_URL" -v ON_ERROR_STOP=1 -f build/synthetic-db-corpus/seed.sql
```

DBeaver에서는 개발 DB connection을 선택하고 `seed.sql` 전체 스크립트를 실행하면 됩니다.

삭제만 할 때:

```bash
psql "$DB_URL" -v ON_ERROR_STOP=1 -f build/synthetic-db-corpus/purge.sql
```

같은 batch는 seed 실행 전에 기존 synthetic users/posts/crews를 제거하고 다시 생성합니다. shared `region`/`tag` vocabulary는 purge하지 않습니다.

## Scope

직접 채우는 product-domain table:

`region`, `user_account`, `journey_post`, `post_place`, `post_image`, `tag`, `post_tag`, `post_like`, `bookmark`, `post_comment`, `crew`, `crew_member`, `crew_tag`.

Recommendation 전용 persistence/exposure 테이블에는 직접 쓰지 않습니다.

## Verify

```bash
python -m unittest discover -s tools/synthetic-db-corpus/tests -p 'test_*.py' -v
```

팀 `develop`이 target commit 이후 DB 계약을 바꾸면 다시 compatibility audit을 해야 합니다.
