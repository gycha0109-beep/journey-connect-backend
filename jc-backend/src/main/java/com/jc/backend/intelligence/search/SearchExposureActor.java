package com.jc.backend.intelligence.search;

public record SearchExposureActor(
        long userId,
        String subjectRef,
        String identityScheme,
        String sessionId) {}
