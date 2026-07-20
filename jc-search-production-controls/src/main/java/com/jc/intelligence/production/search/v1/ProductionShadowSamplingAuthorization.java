package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.wiring.search.v1.DeterministicSearchShadowSampler;
import com.jc.intelligence.wiring.search.v1.SearchShadowSamplingDecisionV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowSamplingPolicyV1;

public record ProductionShadowSamplingAuthorization(int proposedBasisPoints, boolean productionApproved, boolean technicalTestOverride) {
    public ProductionShadowSamplingAuthorization {
        if(proposedBasisPoints<0||proposedBasisPoints>10_000) throw new IllegalArgumentException("basis points must be 0..10000");
        if(productionApproved&&technicalTestOverride) throw new IllegalArgumentException("approval and test override are mutually exclusive");
        if(productionApproved && proposedBasisPoints > 0) throw new IllegalArgumentException("IP-11.5 cannot grant production sampling approval");
    }
    public int effectiveBasisPoints(){return technicalTestOverride?proposedBasisPoints:0;}
    public SearchShadowSamplingDecisionV1 decide(String stableKey){return new DeterministicSearchShadowSampler().decide(stableKey,new SearchShadowSamplingPolicyV1(effectiveBasisPoints(),SearchProductionContractIds.SAMPLING_POLICY));}
    public static ProductionShadowSamplingAuthorization productionDefault(){return new ProductionShadowSamplingAuthorization(0,false,false);}
    public static ProductionShadowSamplingAuthorization technicalTestOnly(int bps){return new ProductionShadowSamplingAuthorization(bps,false,true);}
}
