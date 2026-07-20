package com.jc.backend.search.shadow.production;

import com.jc.intelligence.production.search.v1.ProductionShadowDispatchStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowSamplingDecisionV1;

public record ProductionSearchShadowActivationDecision(
        ProductionSearchShadowActivationReason reason,
        ProductionShadowDispatchStatus dispatchStatus,
        SearchShadowSamplingDecisionV1 samplingDecision) {
    public ProductionSearchShadowActivationDecision {
        if (reason == null || dispatchStatus == null) {
            throw new IllegalArgumentException("activation decision fields are required");
        }
    }

    public boolean dispatched() {
        return dispatchStatus == ProductionShadowDispatchStatus.SUBMITTED;
    }
}
