package com.jc.intelligence.compat.search.explore.v1;

public record LegacyExploreFixtureCase(
        String name, LegacyExploreRequestView request, LegacyExplorePageView page,
        LegacyExploreCompatibilityContext context, LegacyExploreCompatibilityStatus expectedStatus,
        LegacyExploreMappingFailureCode expectedFailureCode) { }
