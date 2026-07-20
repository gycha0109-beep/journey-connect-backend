package com.jc.intelligence.readiness.search.v1;

import java.util.Objects;

public record SearchActivationPrerequisiteV1(
        String prerequisiteId,
        SearchPrerequisiteStatus status,
        SearchPrerequisiteRequirement requirement,
        String ownerRef,
        String evidenceRef) {
    public SearchActivationPrerequisiteV1 {
        if (prerequisiteId == null || !prerequisiteId.matches("[a-z][a-z0-9_]{0,79}")) {
            throw new IllegalArgumentException("prerequisiteId must be lowercase_snake_case");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requirement, "requirement");
        ownerRef = safeRef(ownerRef, "ownerRef");
        evidenceRef = safeRef(evidenceRef, "evidenceRef");
        if (status == SearchPrerequisiteStatus.RESOLVED && "unassigned".equals(ownerRef)
                && requirement != SearchPrerequisiteRequirement.NOT_REQUIRED_FOR_DISABLED_REGRESSION) {
            throw new IllegalArgumentException("resolved prerequisite requires an assigned owner");
        }
    }
    private static String safeRef(String value, String field) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9_./:-]{0,199}")) {
            throw new IllegalArgumentException(field + " must be a safe reference");
        }
        return value;
    }
    public boolean blocksActivation() {
        return requirement == SearchPrerequisiteRequirement.REQUIRED_BEFORE_ACTIVATION
                && status != SearchPrerequisiteStatus.RESOLVED;
    }
    public boolean blocksCutover() {
        return requirement == SearchPrerequisiteRequirement.REQUIRED_BEFORE_CUTOVER
                && status != SearchPrerequisiteStatus.RESOLVED;
    }
}
