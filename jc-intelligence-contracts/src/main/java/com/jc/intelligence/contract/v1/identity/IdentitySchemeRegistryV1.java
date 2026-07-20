package com.jc.intelligence.contract.v1.identity;

import java.util.Map;

public final class IdentitySchemeRegistryV1 {
    private static final Map<IdentitySchemeId, IdentitySchemeDefinitionV1> DEFINITIONS = Map.of(
            IdentitySchemeId.PLATFORM_SUBJECT_V1,
            new IdentitySchemeDefinitionV1(
                    IdentitySchemeId.PLATFORM_SUBJECT_V1,
                    "subject:",
                    "active",
                    false),
            IdentitySchemeId.LEGACY_USER_NUMERIC_V1,
            new IdentitySchemeDefinitionV1(
                    IdentitySchemeId.LEGACY_USER_NUMERIC_V1,
                    "user:",
                    "protected_compatibility",
                    false));

    private IdentitySchemeRegistryV1() {
    }

    public static IdentitySchemeDefinitionV1 definition(IdentitySchemeId id) {
        return DEFINITIONS.get(java.util.Objects.requireNonNull(id, "id"));
    }

    public static Map<IdentitySchemeId, IdentitySchemeDefinitionV1> definitions() {
        return DEFINITIONS;
    }
}
