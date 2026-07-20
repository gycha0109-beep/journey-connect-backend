package com.jc.intelligence.runtime.search.v1.snapshot;

import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.search.cursor.SearchOrderingTupleV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeAuthorityV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankedCandidateV1;
import java.util.ArrayList;
import java.util.List;

public final class SearchResultSnapshotBuilder {
    public SearchResultSnapshotV1 build(
            SearchRuntimeExecutionRequestV1 execution,
            List<SearchRankedCandidateV1> orderedCandidates) {
        List<SearchResultItemV1> items = new ArrayList<>(orderedCandidates.size());
        for (int index = 0; index < orderedCandidates.size(); index++) {
            SearchRankedCandidateV1 ranked = orderedCandidates.get(index);
            int finalPosition = index + 1;
            List<String> tuple = List.of(
                    ranked.rankingScore() == null ? "score:null" : "score:" + Double.toHexString(ranked.rankingScore()),
                    ranked.explicitOrderingKey() == null ? "key:null" : "key:" + ranked.explicitOrderingKey(),
                    ranked.candidate().sourceRank() == null ? "source_rank:null" : "source_rank:" + ranked.candidate().sourceRank(),
                    "entity_type:" + ranked.candidate().entityType().wireValue(),
                    "entity_ref:" + ranked.candidate().entityRef().value());
            items.add(new SearchResultItemV1(ranked.candidate(), ranked.rankingScore(), ranked.explicitOrderingKey(),
                    finalPosition, new SearchOrderingTupleV1(tuple)));
        }
        String requestFingerprint = SearchRuntimeFingerprintV1.request(execution.searchRequest());
        String filterFingerprint = SearchRuntimeFingerprintV1.filters(execution.searchRequest());
        String candidateHash = SearchRuntimeFingerprintV1.candidates(orderedCandidates);
        String contentHash = SearchRuntimeFingerprintV1.sha256(String.join("|",
                requestFingerprint,
                execution.searchRequest().query().queryFingerprint(),
                filterFingerprint,
                execution.searchRequest().rankingPolicyVersion().value(),
                execution.retrievalStrategyVersion().value(),
                execution.searchRequest().context().referenceTime().toString(),
                candidateHash));
        SnapshotRef snapshotId = new SnapshotRef("snapshot:ephemeral-search-output-" + contentHash);
        return new SearchResultSnapshotV1(snapshotId, requestFingerprint,
                execution.searchRequest().query().queryFingerprint(), filterFingerprint,
                execution.searchRequest().rankingPolicyVersion(), execution.retrievalStrategyVersion(),
                execution.searchRequest().context().referenceTime(), items, execution.runtimeVersion(),
                execution.producerBuildId(), execution.completedAt(), contentHash,
                SearchRuntimeAuthorityV1.foundationOnly());
    }
}
