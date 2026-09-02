# Journey Connect Synthetic DB Corpus

PR #79의 대규모 개발/데모 데이터 generator를 복원한 도구입니다.

## Schema profiles

### `team-v19` (default)

팀 저장소의 검증된 현재 스키마용입니다.

- `YTAK99/Journey-Connect`
- `develop`
- verified schema commit: `e05e6167de59c0eaf93fa466451c3fde0d43f14e`
- Flyway: `V1..V19`
- V19 `post_place` 생성
- `post_image.place_id` 연결

```bash
python tools/synthetic-db-corpus/generate.py
```

기본 출력:

```text
build/synthetic-db-corpus/
  seed.sql
  purge.sql
  manifest.json
```

### `local-pre-v19`

`post_place`가 아직 없는 로컬 개발 PostgreSQL을 위한 호환 프로필입니다. 원본 #79 SQL 계약(V17~V18 수준)을 유지하고 **V19 전용 `post_place` / `post_image.place_id` SQL만 생성하지 않습니다.**

Windows에서는 아래 래퍼를 권장합니다.

```powershell
.\tools\synthetic-db-corpus\generate-local.ps1
```

기본 출력:

```text
build/synthetic-db-local/
  seed.sql
  purge.sql
  manifest.json
```

동일한 동작을 Python으로 직접 실행하려면:

```bash
python tools/synthetic-db-corpus/generate.py \
  --schema-profile local-pre-v19 \
  --batch-id local-v1 \
  --output-dir build/synthetic-db-local
```

이 프로필은 V19을 억지로 로컬 DB에 적용하지 않습니다. 로컬 runtime이 V19 이전 스키마를 사용하고 있을 때 그 스키마에 맞춰 corpus를 넣기 위한 용도입니다.

## Default corpus

- 180 users
- 1,800 journey posts
- 약 5,400 post images
- 약 1,100 route-like posts
- 120 crews
- tags / likes / bookmarks / comments long-tail
- 24 destinations, including Tokyo and Hawaii

`team-v19`에서는 route-like stop + fallback `post_place`도 materialize합니다. `local-pre-v19`에서는 route stop 정보는 manifest에 유지되지만 DB `post_place`에는 쓰지 않습니다.

## Common compatibility fixes

기존 #79 donor는 `legacy_generate.py`로 그대로 보존하고 `generate.py`가 프로필을 선택합니다.

공통 보정:

- 서로 다른 batch 간 `user_account.nickname` unique 충돌 방지
- 현재 Crew status domain 검증 (`OWNER/PENDING/APPROVED/REJECTED/CANCELLED`)

## Small smoke corpus

팀 V19:

```bash
python tools/synthetic-db-corpus/generate.py \
  --users 20 \
  --posts 50 \
  --crews 10 \
  --batch-id smoke \
  --output-dir build/synthetic-db-smoke
```

로컬 pre-V19:

```powershell
.\tools\synthetic-db-corpus\generate-local.ps1 -Users 20 -Posts 50 -Crews 10 -BatchId local-smoke -OutputDir build/synthetic-db-local-smoke
```

## Load

**개발 PostgreSQL 전용입니다. 운영 DB에서는 실행하지 마십시오.**

팀 V19 예시:

```bash
psql "$DB_URL" -v ON_ERROR_STOP=1 -f build/synthetic-db-corpus/seed.sql
```

로컬 pre-V19 예시:

```bash
psql "$DB_URL" -v ON_ERROR_STOP=1 -f build/synthetic-db-local/seed.sql
```

DBeaver에서는 해당 개발 DB connection을 선택하고 올바른 프로필에서 생성된 `seed.sql` 전체 스크립트를 처음부터 실행하면 됩니다.

삭제는 같은 출력 폴더의 `purge.sql`을 실행합니다. 같은 batch는 seed 실행 전에 기존 synthetic users/posts/crews를 제거하고 다시 생성합니다. shared `region`/`tag` vocabulary는 purge하지 않습니다.

## Scope

공통으로 직접 채우는 product-domain table:

`region`, `user_account`, `journey_post`, `post_image`, `tag`, `post_tag`, `post_like`, `bookmark`, `post_comment`, `crew`, `crew_member`, `crew_tag`.

`team-v19`만 추가로 `post_place`와 `post_image.place_id`를 채웁니다.

Recommendation 전용 persistence/exposure 테이블에는 직접 쓰지 않습니다.

## Verify

```bash
python -m unittest discover -s tools/synthetic-db-corpus/tests -p 'test_*.py' -v
```

팀 `develop`이 target commit 이후 DB 계약을 바꾸면 `team-v19` compatibility audit을 다시 해야 합니다.
