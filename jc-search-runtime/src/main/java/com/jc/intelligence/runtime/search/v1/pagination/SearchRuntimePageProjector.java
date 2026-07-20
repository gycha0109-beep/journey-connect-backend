package com.jc.intelligence.runtime.search.v1.pagination;

import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorFactoryV1;
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorV1;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import com.jc.intelligence.runtime.search.v1.snapshot.SearchResultSnapshotV1;
import java.time.Duration;
import java.util.List;

public final class SearchRuntimePageProjector {
    public SearchRuntimePageV1 firstPage(
            SearchRuntimeExecutionRequestV1 execution,
            SearchResultSnapshotV1 snapshot,
            RunRef runId) {
        int pageSize = execution.searchRequest().pageRequest().pageSize();
        int end = Math.min(pageSize, snapshot.items().size());
        List<com.jc.intelligence.runtime.search.v1.snapshot.SearchResultItemV1> items =
                List.copyOf(snapshot.items().subList(0, end));
        if (end == snapshot.items().size()) return new SearchRuntimePageV1(items, pageSize, false, null);
        var last = items.get(items.size() - 1);
        SearchCursorV1 cursor = SearchCursorFactoryV1.create(
                new SchemaVersion(com.jc.intelligence.contract.v1.search.SearchContractIds.SEARCH_PAGINATION_CURSOR.value()), runId, snapshot.snapshotId(),
                snapshot.queryFingerprint(), snapshot.filterFingerprint(),
                execution.searchRequest().sort().sortPolicyVersion(), execution.searchRequest().rankingPolicyVersion(),
                execution.searchRequest().context().referenceTime(), end + 1, last.orderingTuple(),
                execution.searchRequest().context().surface(), execution.searchRequest().context().entityScope(),
                execution.searchRequest().context().subjectRef(), execution.searchRequest().context().sessionRef(),
                execution.completedAt(), execution.completedAt().plus(Duration.ofMinutes(5)));
        return new SearchRuntimePageV1(items, pageSize, true,
                new SearchTestCursorV1(cursor, "test_only_unsigned", false));
    }
}
