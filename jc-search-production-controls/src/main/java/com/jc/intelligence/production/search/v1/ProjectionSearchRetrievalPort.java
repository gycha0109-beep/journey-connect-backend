package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchEntityType;
import com.jc.intelligence.contract.v1.search.SearchFilterType;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalRequestV1;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalPort;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalResultV1;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProjectionSearchRetrievalPort implements SearchRetrievalPort {
    private final SearchProjectionStore store; private final Duration maximumStaleness;
    public ProjectionSearchRetrievalPort(SearchProjectionStore store,Duration maximumStaleness){this.store=Objects.requireNonNull(store,"store");this.maximumStaleness=Objects.requireNonNull(maximumStaleness,"maximumStaleness");}
    @Override public SearchRetrievalResultV1 retrieve(RetrievalRequestV1 request){
        if(request==null) throw new IllegalArgumentException("request required");
        if(!request.retrievalStrategyVersion().equals(SearchProductionContractIds.RETRIEVAL_STRATEGY)) return SearchRetrievalResultV1.unavailable("projection_retrieval_version_unsupported");
        if(!request.retrievalSources().equals(List.of(RetrievalSource.DATABASE_POST))) return SearchRetrievalResultV1.unavailable("projection_retrieval_source_unsupported");
        String region=null;
        for(var f:request.filters()) if(f.filterType()==SearchFilterType.REGION){
            region=f.values().get(0);
        }
        var terms=SearchProjectionTextNormalizer.terms(request.query().normalizedQuery(),128);
        var result=store.query(new SearchProjectionQueryV1(terms,region,request.maximumCandidateCount(),SearchProductionContractIds.PROJECTION_SCHEMA,SearchProductionContractIds.ELIGIBILITY_POLICY,request.referenceTime(),maximumStaleness));
        if(result.status()==SearchProjectionAvailabilityStatus.UNAVAILABLE || result.status()==SearchProjectionAvailabilityStatus.STALE) return SearchRetrievalResultV1.unavailable(result.safeReason());
        if(result.status()!=SearchProjectionAvailabilityStatus.AVAILABLE && result.status()!=SearchProjectionAvailabilityStatus.EMPTY) return SearchRetrievalResultV1.unavailable(result.safeReason());
        List<RetrievalCandidateV1> candidates=new ArrayList<>(); int rank=1;
        for(var p:result.documents()){
            candidates.add(new RetrievalCandidateV1(com.jc.intelligence.contract.v1.search.SearchContractIds.SEARCH_RETRIEVAL_RANKING,
                    new EntityRef(p.documentId()),SearchEntityType.POST,Long.toString(p.sourcePostId()),RetrievalSource.DATABASE_POST,
                    null,rank++,request.referenceTime(),new SnapshotRef("snapshot:search-projection-"+p.deterministicContentHash()),
                    SearchEligibilityState.ELIGIBLE,SearchVisibilityState.VISIBLE,"metadata:projection-v1",SearchProductionContractIds.RETRIEVAL_STRATEGY));
        }
        return SearchRetrievalResultV1.success(candidates);
    }
}
