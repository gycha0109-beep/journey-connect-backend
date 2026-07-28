# Recommendation

The team baseline retains only the recommendation code, database objects and tests required by the running service.

Presentation flow:

1. Collect post, region and interaction data.
2. Query eligible recommendation candidates.
3. Apply the current ordering policy.
4. Return recommendation results to the feed.
5. Verify behavior through database and regression tests.

Internal P0/P1/P2 phase history and enterprise authority contracts are intentionally excluded from this branch.
