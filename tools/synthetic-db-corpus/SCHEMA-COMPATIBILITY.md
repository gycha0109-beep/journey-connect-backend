# Synthetic DB Corpus Schema Compatibility

## Profiles

| Profile | Intended schema | V19 `post_place` | `post_image.place_id` |
|---|---|---:|---:|
| `team-v19` | `YTAK99/Journey-Connect` `develop` @ `e05e6167de59c0eaf93fa466451c3fde0d43f14e` | yes | yes |
| `local-pre-v19` | local development PostgreSQL with the restored #79 V17/V18 product tables but no V19 | no | no |

## Common required contracts

The restored donor SQL requires the product contracts that existed by V17:

- V1: `user_account`, `journey_post`, likes/bookmarks/comments, `crew`, `crew_member`
- V2: canonical `region`, `region_id`, `post_image`, Crew approval/review columns
- V5: `tag`, `post_tag`
- V17: `crew.cover_image_url`, `crew_tag`

The `local-pre-v19` profile stops here. It deliberately does not reference V19-only relations or columns.

## `team-v19` additional contracts

- V19: `post_place`, `post_image.place_id`

For this profile `generate.py` adds:

1. Every generated post gets at least one `post_place`.
2. Existing route-like stops become ordered `post_place` rows.
3. Non-route posts get one fallback `post_place` at the destination center.
4. Generated `post_image` rows are bound to `post_place` rows through `place_id`.

## Common restored #79 compatibility deltas

The original PR #79 generator remains available as `legacy_generate.py` and is not modified.

Both profiles apply:

1. Synthetic nicknames receive a deterministic batch suffix to avoid the global `uk_user_nickname` collision when multiple batches coexist.
2. Crew membership output is checked against the current status domain.

## Deliberately not populated

Recommendation storage, exposure, impression, evaluation, release, content-analysis, notification, password-reset, refresh-token, and admin tables are not directly seeded.

The corpus is intended to populate normal product facts and interactions so application/recommendation code can consume them through its normal paths.
