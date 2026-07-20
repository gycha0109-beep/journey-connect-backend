package com.jc.intelligence.contract.v1.identity;

import java.util.Set;

public final class EntityTypeRegistryV1 {
    private static final Set<String> REGISTERED = Set.of(
            "post", "journey", "place", "crew", "user", "tag", "region", "itinerary", "content");

    private EntityTypeRegistryV1() {
    }

    public static boolean isRegistered(String entityType) {
        return REGISTERED.contains(entityType);
    }

    public static Set<String> registeredTypes() {
        return REGISTERED;
    }
}
