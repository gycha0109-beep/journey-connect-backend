package com.jc.intelligence.runtime.search.v1.fixture;

import com.jc.intelligence.contract.v1.search.retrieval.RetrievalRequestV1;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalPort;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalResultV1;
import java.util.Objects;

public final class InMemorySearchRetrievalPort implements SearchRetrievalPort {
    private final SearchRetrievalResultV1 result;
    public InMemorySearchRetrievalPort(SearchRetrievalResultV1 result) { this.result = Objects.requireNonNull(result, "result"); }
    @Override public SearchRetrievalResultV1 retrieve(RetrievalRequestV1 request) {
        Objects.requireNonNull(request, "request");
        return result;
    }
}
