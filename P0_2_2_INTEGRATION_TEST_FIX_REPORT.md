# P0-2.2 Integration Test Fix Report

## Scope

This batch fixes the five remaining PostgreSQL integration-test failures after canonical schema validation succeeded.

## Applied fixes

### 1. Crew concurrency fixture transaction

- File: `jc-backend/src/test/java/com/jc/backend/crew/CrewConcurrencyIntegrationTest.java`
- The crew row and its mandatory OWNER membership are now created within one `TransactionTemplate` transaction.
- The deferred aggregate constraint remains intact; the test no longer commits an invalid intermediate aggregate.

### 2. PostgreSQL cursor parameter typing

- Files:
  - `jc-backend/src/main/java/com/jc/backend/post/JourneyPostRepository.java`
  - `jc-backend/src/main/java/com/jc/backend/post/PostService.java`
- The initial feed query no longer passes a nullable timestamp into `:cursorPublishedAt is null`.
- Initial-page and continuation-page queries are separated, preventing PostgreSQL SQLSTATE `42P18` (`could not determine data type of parameter`).

### 3. Fixed-query-count post summaries

- File: `jc-backend/src/main/java/com/jc/backend/post/JourneyPost.java`
- Added Hibernate `@BatchSize(size = 100)` to the post image collection.
- Cover-image access now batch-loads image collections instead of issuing one query per post.
- Expected query contract: feed query + image batch + like counts + bookmark counts, at most four statements for a partial first page.

### 4. Recommendation idempotency timestamp precision

- Files:
  - `RecommendationExposureStore.java`
  - `RecommendationBehaviorStore.java`
- `servedAt` and `occurredAt` are truncated to PostgreSQL microsecond precision before insertion.
- Identical retries therefore compare against the exact persisted timestamp instead of potentially differing through nanosecond-to-microsecond rounding.

### 5. Recommendation validation/conflict exception contract

- Files:
  - `RecommendationSnapshotStore.java`
  - `RecommendationRunStore.java`
  - `RecommendationExposureStore.java`
  - `RecommendationBehaviorStore.java`
- These JDBC-backed application stores now use `@Component` rather than `@Repository`.
- `JdbcTemplate` still translates SQL failures to Spring `DataAccessException` types.
- Intentional pre-write `IllegalArgumentException` and idempotency `IllegalStateException` contracts are no longer wrapped by `PersistenceExceptionTranslationInterceptor` as `InvalidDataAccessApiUsageException`.

## Static verification completed

- Eight source/test files changed.
- Nullable cursor predicate removed.
- Initial and continuation feed methods present.
- Crew aggregate fixture transaction present.
- Image batch-fetch annotation present.
- Recommendation timestamp normalization present.
- Recommendation store exception boundaries present.

## Environment limitation

The artifact environment had Java 21 but no Docker, no Gradle distribution cache, and no outbound access to download Gradle. Therefore PostgreSQL/Testcontainers execution could not be completed here.

## Required verification on the project machine

Run from `jc-backend`:

```powershell
.\gradlew.bat :test `
  --tests "com.jc.backend.crew.CrewConcurrencyIntegrationTest" `
  --tests "com.jc.backend.post.FeedCursorIntegrationTest" `
  --tests "com.jc.backend.post.PostListQueryIntegrationTest" `
  --tests "com.jc.backend.recommendation.RecommendationPersistenceIntegrationTest" `
  --rerun-tasks `
  --stacktrace
```

Then run the full backend gate:

```powershell
.\gradlew.bat clean :test --stacktrace
```

Expected result:

```text
27 tests completed, 0 failed
BUILD SUCCESSFUL
```
