package com.jc.backend.intelligence.search;

public record SearchExposureActor(
        long userId,
        String subjectRef,
        String identityScheme,
        String identityMappingVersion,
        String sessionId) {

    public SearchExposureActor(
            long userId,
            String subjectRef,
            String identityScheme,
            String sessionId) {
        this(
                userId,
                subjectRef,
                identityScheme,
                SearchExposureContract.IDENTITY_MAPPING_VERSION,
                sessionId);
    }
}
