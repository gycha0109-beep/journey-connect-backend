package com.jc.intelligence.production.search.v1;

import java.time.Instant;
import java.util.Objects;

public record PrivacySafeSearchShadowEvidenceV1(Instant timestamp,String opaqueRunId,String projectionSchemaVersion,
        String eligibilityPolicyVersion,SearchShadowEvidenceStatus status,String latencyBucket,String candidateCountBucket,
        String overlapBucket,String divergenceBucket,String freshnessBucket,String safeReason) {
    public PrivacySafeSearchShadowEvidenceV1 {
        Objects.requireNonNull(timestamp,"timestamp"); if(opaqueRunId==null||!opaqueRunId.matches("run:[a-z0-9_-]{8,80}"))throw new IllegalArgumentException("opaqueRunId invalid");
        if(!SearchProductionContractIds.PROJECTION_SCHEMA.value().equals(projectionSchemaVersion)||!SearchProductionContractIds.ELIGIBILITY_POLICY.value().equals(eligibilityPolicyVersion))throw new IllegalArgumentException("version mismatch");
        Objects.requireNonNull(status,"status");
        for(String v:new String[]{latencyBucket,candidateCountBucket,overlapBucket,divergenceBucket,freshnessBucket,safeReason}) if(v==null||!v.matches("[a-z0-9_]{1,48}"))throw new IllegalArgumentException("evidence bucket invalid");
    }
}
