package com.jc.intelligence.production.search.v1;

import java.time.Clock;
import java.util.Objects;

public final class SearchProjectionProjectorService {
    private final SearchDocumentEligibilityPolicyV1 eligibilityPolicy;
    private final SearchDocumentProjectorV1 projector;
    private final SearchProjectionStore store;
    private final Clock clock;
    public SearchProjectionProjectorService(SearchDocumentEligibilityPolicyV1 eligibilityPolicy,SearchDocumentProjectorV1 projector,SearchProjectionStore store,Clock clock){
        this.eligibilityPolicy=Objects.requireNonNull(eligibilityPolicy,"eligibilityPolicy");this.projector=Objects.requireNonNull(projector,"projector");this.store=Objects.requireNonNull(store,"store");this.clock=Objects.requireNonNull(clock,"clock");
    }
    public SearchProjectionWriteResultV1 apply(SearchDocumentSourceV1 source){
        if(source==null) throw new IllegalArgumentException("source required");
        var decision=eligibilityPolicy.evaluate(source,clock.instant());
        if(!decision.eligible()) return store.remove(source.sourcePostId(),decision.safeReason());
        return store.upsert(projector.project(source,clock.instant()));
    }
    public SearchProjectionWriteResultV1 removeMissingSource(long sourcePostId){return store.remove(sourcePostId,"source_missing");}
}
