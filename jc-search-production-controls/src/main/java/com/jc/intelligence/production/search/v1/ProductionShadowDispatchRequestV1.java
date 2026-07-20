package com.jc.intelligence.production.search.v1;

public record ProductionShadowDispatchRequestV1<T>(T legacyResponse,String stableSamplingKey,String opaqueInternalCohortKey,Runnable shadowTask) {
    public ProductionShadowDispatchRequestV1 { if(legacyResponse==null||stableSamplingKey==null||stableSamplingKey.isBlank()||opaqueInternalCohortKey==null||shadowTask==null)throw new IllegalArgumentException("dispatch request fields required"); }
}
