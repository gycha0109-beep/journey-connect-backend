package com.jc.intelligence.contract.v1.search.retrieval;

import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.query.SearchFilterCanonicalizerV1;
import com.jc.intelligence.contract.v1.search.query.SearchFilterV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryV1;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record RetrievalRequestV1(
        ContractId contractVersion,
        RunRef searchRunId,
        SearchQueryV1 query,
        SearchEntityScope entityScope,
        List<SearchFilterV1> filters,
        SchemaVersion retrievalStrategyVersion,
        List<RetrievalSource> retrievalSources,
        Instant referenceTime,
        int maximumCandidateCount,
        SnapshotRef visibilitySnapshotRef,
        ProducerBuildId producerBuildId) {
    public RetrievalRequestV1 {
        SearchVersionValidatorV1.requireContract(contractVersion, SearchContractIds.SEARCH_RETRIEVAL_RANKING);
        SearchChecks.requireNonNull(searchRunId, "searchRunId");
        SearchChecks.requireNonNull(query, "query");
        SearchChecks.requireNonNull(entityScope, "entityScope");
        filters = SearchFilterCanonicalizerV1.canonicalize(filters);
        SearchChecks.requireNonNull(retrievalStrategyVersion, "retrievalStrategyVersion");
        if (retrievalSources == null || retrievalSources.isEmpty()) {
            throw SearchChecks.invalid(
                    com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                    "retrievalSources are required");
        }
        LinkedHashSet<RetrievalSource> unique = new LinkedHashSet<>();
        for (RetrievalSource source : retrievalSources) unique.add(SearchChecks.requireNonNull(source, "retrievalSource"));
        retrievalSources = List.copyOf(new ArrayList<>(unique));
        SearchChecks.requireInstant(referenceTime, "referenceTime");
        SearchChecks.requireRange(maximumCandidateCount, 1, 1000, "maximumCandidateCount");
        SearchChecks.requireNonNull(producerBuildId, "producerBuildId");
    }
}
