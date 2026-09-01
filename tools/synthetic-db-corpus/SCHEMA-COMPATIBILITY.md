# Synthetic DB Corpus Schema Compatibility

Verified against `YTAK99/Journey-Connect` `develop` commit `e05e6167de59c0eaf93fa466451c3fde0d43f14e`.

## Required current contracts

- V1: `user_account`, `journey_post`, likes/bookmarks/comments, `crew`, `crew_member`
- V2: canonical `region`, `region_id`, `post_image`, Crew approval columns
- V5: `tag`, `post_tag`
- V17: `crew.cover_image_url`, `crew_tag`
- V19: `post_place`, `post_image.place_id`

## Restored #79 compatibility deltas

The original PR #79 generator remains available as `legacy_generate.py` and is not modified.

`generate.py` adds only the compatibility layer needed by the current team schema:

1. Every generated post gets at least one `post_place`.
2. Existing route-like stops become ordered `post_place` rows.
3. Non-route posts get one fallback `post_place` at the destination center.
4. Generated `post_image` rows are bound to `post_place` rows through `place_id`.
5. Synthetic nicknames receive a deterministic batch suffix to avoid the global `uk_user_nickname` collision when multiple batches coexist.
6. Crew membership output is checked against the current status domain.

## Deliberately not populated

Recommendation storage, exposure, impression, evaluation, release, content-analysis, notification, password-reset, refresh-token, and admin tables are not directly seeded.

The corpus is intended to populate normal product facts and interactions so application/recommendation code can consume them through its normal paths.
