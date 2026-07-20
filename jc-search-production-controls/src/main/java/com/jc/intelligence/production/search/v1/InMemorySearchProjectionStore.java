package com.jc.intelligence.production.search.v1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InMemorySearchProjectionStore implements SearchProjectionStore {
    private final Map<Long,SearchDocumentProjectionV1> documents=new LinkedHashMap<>();
    private boolean available=true;
    public synchronized void setAvailable(boolean value){available=value;}
    @Override public synchronized Optional<SearchDocumentProjectionV1> findBySourcePostId(long id){return Optional.ofNullable(documents.get(id));}
    @Override public synchronized SearchProjectionWriteResultV1 upsert(SearchDocumentProjectionV1 p){
        var current=documents.get(p.sourcePostId());
        if(current!=null){
            if(current.sourceVersion()>p.sourceVersion()) return new SearchProjectionWriteResultV1(SearchProjectionWriteStatus.STALE_IGNORED,p.sourcePostId(),"stale_source_version");
            if(current.sourceVersion()==p.sourceVersion()){
                if(current.deterministicContentHash().equals(p.deterministicContentHash())) return new SearchProjectionWriteResultV1(SearchProjectionWriteStatus.UNCHANGED,p.sourcePostId(),"duplicate_source_event");
                return new SearchProjectionWriteResultV1(SearchProjectionWriteStatus.HASH_MISMATCH_REJECTED,p.sourcePostId(),"source_hash_mismatch");
            }
        }
        documents.put(p.sourcePostId(),p);
        return new SearchProjectionWriteResultV1(current==null?SearchProjectionWriteStatus.INSERTED:SearchProjectionWriteStatus.UPDATED,p.sourcePostId(),current==null?"projection_inserted":"projection_updated");
    }
    @Override public synchronized SearchProjectionWriteResultV1 remove(long id,String reason){
        if(id<1) throw new IllegalArgumentException("sourcePostId invalid");
        boolean removed=documents.remove(id)!=null;
        return new SearchProjectionWriteResultV1(removed?SearchProjectionWriteStatus.REMOVED:SearchProjectionWriteStatus.SOURCE_MISSING,id,removed?reason:"source_missing");
    }
    @Override public synchronized SearchProjectionQueryResultV1 query(SearchProjectionQueryV1 q){
        if(!available) return SearchProjectionQueryResultV1.unavailable(SearchProjectionAvailabilityStatus.UNAVAILABLE,"projection_store_unavailable");
        if(!q.projectionSchemaVersion().equals(SearchProductionContractIds.PROJECTION_SCHEMA)) return SearchProjectionQueryResultV1.unavailable(SearchProjectionAvailabilityStatus.UNSUPPORTED_SCHEMA,"projection_schema_unsupported");
        if(!q.eligibilityPolicyVersion().equals(SearchProductionContractIds.ELIGIBILITY_POLICY)) return SearchProjectionQueryResultV1.unavailable(SearchProjectionAvailabilityStatus.UNSUPPORTED_POLICY,"eligibility_policy_unsupported");
        Set<String> terms=Set.copyOf(q.queryTerms());
        List<SearchDocumentProjectionV1> result=new ArrayList<>();
        boolean matchingStale=false;
        for(var p:documents.values()){
            if(q.regionReference()!=null && !p.regionReference().equals(q.regionReference())) continue;
            if(!terms.isEmpty() && score(p,terms)==0) continue;
            if(p.sourceUpdatedAt().isBefore(q.referenceTime().minus(q.maximumStaleness()))) { matchingStale=true; continue; }
            result.add(p);
        }
        result.sort(Comparator.<SearchDocumentProjectionV1>comparingInt(p->score(p,terms)).reversed()
                .thenComparing(SearchDocumentProjectionV1::sourceUpdatedAt,Comparator.reverseOrder())
                .thenComparing(SearchDocumentProjectionV1::sourcePostId,Comparator.reverseOrder()));
        if(result.size()>q.maximumCandidateCount()) result=new ArrayList<>(result.subList(0,q.maximumCandidateCount()));
        if(result.isEmpty() && matchingStale) return SearchProjectionQueryResultV1.unavailable(SearchProjectionAvailabilityStatus.STALE,"projection_stale");
        return SearchProjectionQueryResultV1.available(result);
    }
    private static int score(SearchDocumentProjectionV1 p,Set<String> q){int s=0; for(String t:q){if(p.normalizedTitleTerms().contains(t))s+=2;if(p.normalizedBodyTerms().contains(t))s++;}return s;}
    @Override public synchronized int size(){return documents.size();}
}
