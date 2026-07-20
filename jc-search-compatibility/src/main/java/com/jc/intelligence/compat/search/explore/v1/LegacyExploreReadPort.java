package com.jc.intelligence.compat.search.explore.v1;

@FunctionalInterface
public interface LegacyExploreReadPort {
    LegacyExplorePageView read(LegacyExploreRequestView request);
}
