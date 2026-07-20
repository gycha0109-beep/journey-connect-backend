package com.jc.intelligence.runtime.search.v1.port;

import com.jc.intelligence.contract.v1.search.retrieval.RetrievalRequestV1;

@FunctionalInterface
public interface SearchRetrievalPort {
    SearchRetrievalResultV1 retrieve(RetrievalRequestV1 request);
}
