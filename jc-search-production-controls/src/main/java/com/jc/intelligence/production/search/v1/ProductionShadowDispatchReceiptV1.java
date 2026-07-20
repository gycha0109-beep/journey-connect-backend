package com.jc.intelligence.production.search.v1;

public record ProductionShadowDispatchReceiptV1<T>(T legacyResponse,ProductionShadowDispatchStatus status,int effectiveSampleBasisPoints,String safeReason) {
    public ProductionShadowDispatchReceiptV1 { if(legacyResponse==null||status==null||effectiveSampleBasisPoints<0||effectiveSampleBasisPoints>10000||safeReason==null||!safeReason.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("dispatch receipt invalid"); }
}
