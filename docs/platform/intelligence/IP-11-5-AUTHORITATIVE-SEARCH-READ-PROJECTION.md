# IP-11.5 Authoritative Search Read Projection

## 계약

- Schema: `search-document-projection-v1`
- Eligibility: `search-document-eligibility-v1`
- Operations authority: `search-document-operational-eligibility-v1`
- Storage: `public.search_document_projection_v1`
- Writer: `public.project_search_document_v1(bigint)`
- Rebuild: `public.rebuild_search_document_projection_v1()`

## Source of truth

Canonical `posts`, active author, active region, post-place relation과 Operations eligibility row를 결속한다. Operations row가 없거나 상태를 해석할 수 없으면 default deny다. IP-10 synthetic catalog와 legacy response는 source가 아니다.

## Projection fields

문서·source version, schema/policy version, region/place opaque reference, normalized term arrays, fixed public/published/eligible/active state, source/projected timestamp, deterministic SHA-256을 보존한다. 사용자 프로필, JWT, email, 전화번호, 행동 profile, exposure는 포함하지 않는다.

## Determinism and replay

동일 source version/hash는 `unchanged`, 낮은 version은 `stale_ignored`, 동일 version의 다른 hash는 `hash_mismatch_rejected`다. private/draft/deleted/moderation blocked/Operations deny는 row를 제거한다. Full rebuild는 source ID 순으로 replay한다.

## Failure matrix

| 상태 | 결과 |
|---|---|
| public+published+visible+active authority | upsert |
| private/draft/deleted/hidden | remove |
| Operations authority missing/excluded | remove |
| stale source version | ignore |
| same version/hash | unchanged |
| same version/hash mismatch | reject |
| DB unavailable | provider unavailable; legacy unaffected |

## Rollback strategy

Migration 27은 additive다. 활성 production bridge가 없으므로 긴급 시 reader graph를 사용하지 않고 projection table/function은 forward remediation 대상으로 유지할 수 있다. 기존 `01..26`은 수정하지 않는다.

## 보호 결론

- Production shadow: `DISABLED`
- Effective production sampling: `0 BPS`
- Legacy `/api/v1/explore` response authority: `legacy`
- Search response cutover: `NOT STARTED`
- Recommendation exposure/persistence/release authority: `NONE`
- IP-12: `HOLD`
- Gradle/Spring/PostgreSQL: `NOT EXECUTED — USER-DIRECTED SKIP`
