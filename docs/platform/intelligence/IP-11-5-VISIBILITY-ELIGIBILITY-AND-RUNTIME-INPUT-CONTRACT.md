# IP-11.5 Visibility, Eligibility and Runtime Input Contract

## Eligibility order

1. visibility `PUBLIC`
2. publication `PUBLISHED`
3. deletion `ACTIVE`
4. moderation `ELIGIBLE`
5. Operations exclusion false and authority present
6. region/place valid
7. known schema/policy version
8. freshness within policy

Unknown, malformed, stale or unavailable authority is deny/unavailable.

## Runtime provider

`ProjectionExploreRuntimeInputProviderFactory` converts keyword/region/page constraints into existing Search execution contracts. It does not receive legacy response candidates. Offset page, unsupported filter or invalid request returns `UNSUPPORTED`/`INVALID`. Fingerprint is SHA-256 and contains no raw query.

`ProjectionSearchRetrievalPort` consumes only `SearchProjectionStore`, checks schema/policy/source, bounds candidates and uses deterministic source rank. Stale/unavailable projection maps to Search dependency unavailable, not HTTP failure.

## Backend adapter

`JdbcSearchDocumentProjectionStore` is read-only. Projection writes remain SQL security-owner functions. `JdbcSearchProjectionRebuildService` is an explicit maintenance capability and is not called by request flow.

## Legacy isolation

PostController, PostService, Repository, DTO, security and response mapping remain unchanged from IP-11 baseline. Capability bean existence does not connect it to `ExploreSearchShadowBridge`.

## 보호 결론

- Production shadow: `DISABLED`
- Effective production sampling: `0 BPS`
- Legacy `/api/v1/explore` response authority: `legacy`
- Search response cutover: `NOT STARTED`
- Recommendation exposure/persistence/release authority: `NONE`
- IP-12: `HOLD`
- Gradle/Spring/PostgreSQL: `NOT EXECUTED — USER-DIRECTED SKIP`
