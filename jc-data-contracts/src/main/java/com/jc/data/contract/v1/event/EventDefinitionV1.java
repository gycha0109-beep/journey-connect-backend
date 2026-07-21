package com.jc.data.contract.v1.event;

import java.util.Set;

public record EventDefinitionV1(
        EventFamily family,
        EventType type,
        Set<String> requiredPayloadFields,
        Set<String> optionalPayloadFields,
        boolean entityRequired) {
    public EventDefinitionV1 {
        requiredPayloadFields = Set.copyOf(requiredPayloadFields);
        optionalPayloadFields = Set.copyOf(optionalPayloadFields);
    }

    public Set<String> allowedPayloadFields() {
        java.util.HashSet<String> allowed = new java.util.HashSet<>(requiredPayloadFields);
        allowed.addAll(optionalPayloadFields);
        return Set.copyOf(allowed);
    }
}
