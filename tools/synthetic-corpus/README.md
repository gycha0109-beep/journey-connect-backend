# Journey Connect Synthetic Social Corpus

개발/데모/추천 검증용으로 **재현 가능한 대규모 여행 소셜 데이터**를 생성합니다.

기본 프로필은 다음 규모입니다.

- 180 synthetic users
- 1,800 posts
- 약 5,400 post images
- 약 1,100 route-like posts / 약 4,000 route stops in JSON manifest
- 120 crews + owner/member states
- tags, likes, bookmarks, comments, engagement long-tail
- 24 destination regions

> 이 도구는 운영 데이터를 가장하거나 운영 DB에 자동 주입하지 않습니다. SQL/JSON을 생성한 뒤 개발 DB에서 명시적으로 실행하는 구조입니다.

## Target schema

`YTAK99/Journey-Connect`의 `develop` 브랜치에 현재 존재하는 PostgreSQL/Flyway 계약을 기준으로 생성합니다.

직접 채우는 테이블:

- `region`
- `user_account`
- `journey_post`
- `post_image`
- `tag`
- `post_tag`
- `post_like`
- `bookmark`
- `post_comment`
- `crew`
- `crew_member`
- `crew_tag`

현재 팀 `develop` 스키마에는 별도 route/route_stop 영속 테이블이 없으므로 route-like stop 데이터는 `manifest.json`에 함께 생성합니다. 향후 route persistence 계약이 생기면 이 데이터를 그대로 매핑할 수 있습니다.

## Destinations

서울, 부산, 제주, 도쿄, 오사카, 교토, 후쿠오카, 뉴욕, 로스앤젤레스, 샌프란시스코, 라스베이거스, 하와이, 괌, 방콕, 타이베이, 가오슝, 파리, 런던, 프라하, 바르셀로나, 마드리드, 로마, 피렌체, 베네치아를 포함합니다.

`DESTINATIONS`에 항목 하나를 추가하면 지역/게시물/크루/사진 키워드가 같이 확장됩니다.

## Image strategy

사용자가 사진을 수천 장 직접 준비하지 않아도 되도록 개발 데이터는 원격 placeholder를 사용합니다.

- 게시물/크루: LoremFlickr keyword + `lock` 기반 URL
- 프로필: DiceBear 10.x deterministic avatar URL

같은 seed와 batch로 생성하면 URL도 동일합니다. 게시물 이미지는 목적지별 키워드를 사용합니다.

외부 서비스는 개발 편의를 위한 의존성입니다. 실제 공개/운영 데이터로 전환할 때는 자체 보유 이미지나 별도 라이선스/attribution 정책이 확정된 asset pipeline으로 교체하십시오.

## Generate

Python 표준 라이브러리만 사용합니다.

```bash
python tools/synthetic-corpus/generate.py
```

기본 출력:

```text
build/synthetic-corpus/
  seed.sql
  purge.sql
  manifest.json
```

규모 변경:

```bash
python tools/synthetic-corpus/generate.py \
  --users 150 \
  --posts 1500 \
  --crews 100 \
  --batch-id demo-v2 \
  --seed 20260825
```

작은 smoke fixture:

```bash
python tools/synthetic-corpus/generate.py \
  --users 20 \
  --posts 50 \
  --crews 10 \
  --batch-id smoke \
  --output-dir build/synthetic-smoke
```

## Load into a development PostgreSQL DB

Flyway migrations가 최신 상태인 개발 DB에서만 실행합니다.

```bash
psql "$DB_URL" -v ON_ERROR_STOP=1 -f build/synthetic-corpus/seed.sql
```

같은 `batch-id`로 다시 실행하면 해당 batch synthetic users/posts/crews를 먼저 지우고 재생성하므로 결과가 중첩되지 않습니다.

삭제만 할 때:

```bash
psql "$DB_URL" -v ON_ERROR_STOP=1 -f build/synthetic-corpus/purge.sql
```

## Synthetic identity boundary

모든 synthetic user email은 다음 패턴을 사용합니다.

```text
synthetic.<batch-id>.<sequence>@journey-connect.local
```

`bio`와 crew description에도 `[synthetic:<batch-id>]` marker가 들어갑니다. 따라서 운영 사용자와 구분 가능하고 batch 단위 purge가 가능합니다.

## Data shape

게시물은 완전 균등 random이 아닙니다.

- 지역은 가중 분포
- 조회/좋아요/댓글은 long-tail 분포
- 이미지 1~5장
- 태그 최대 5개
- 최근 180일에 생성 시점 분산
- 약 62%는 route-like stop 포함
- route stop은 지역 중심점 주변 synthetic 좌표를 사용

그래서 feed pagination, explore, search, recommendation, popularity, crew discovery 테스트에 단순 placeholder보다 유용합니다.

## Validation

```bash
python -m unittest discover -s tools/synthetic-corpus/tests -p 'test_*.py'
```

검증 항목:

- 같은 seed의 deterministic output
- email/nickname/key uniqueness
- 이미지/태그/제목/crew capacity 계약 범위
- 목적지 coverage
- SQL이 batch marker와 핵심 테이블을 포함하는지 확인

## Important

`seed.sql`은 대량 interaction row까지 생성합니다. 기본 1,800 post corpus는 개발 DB에서 수만 개의 like/bookmark/comment row를 추가할 수 있습니다. 운영 DB에서는 실행하지 마십시오.
