package com.jc.intelligence.production.search.v1;

import java.util.Optional;

public interface SearchProjectionStore {
    Optional<SearchDocumentProjectionV1> findBySourcePostId(long sourcePostId);
    SearchProjectionWriteResultV1 upsert(SearchDocumentProjectionV1 projection);
    SearchProjectionWriteResultV1 remove(long sourcePostId, String reason);
    SearchProjectionQueryResultV1 query(SearchProjectionQueryV1 query);
    int size();
}
