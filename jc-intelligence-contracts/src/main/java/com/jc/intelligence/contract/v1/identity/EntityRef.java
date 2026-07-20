package com.jc.intelligence.contract.v1.identity;

import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;

public record EntityRef(String value) {
    public EntityRef {
        ContractChecks.requireText(
                value,
                "entityRef",
                IntelligenceValidationErrorCode.INTELLIGENCE_ENTITY_REF_INVALID);
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':')) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_ENTITY_REF_INVALID,
                    "entityRef must contain one ':' separator");
        }
        String entityType = value.substring(0, separator);
        String sourceId = value.substring(separator + 1);
        if (!entityType.matches("[a-z][a-z0-9_\\-]*")
                || !EntityTypeRegistryV1.isRegistered(entityType)
                || sourceId.isBlank()
                || sourceId.length() > 128
                || sourceId.chars().anyMatch(Character::isWhitespace)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_ENTITY_REF_INVALID,
                    "entityRef contains an unregistered type or invalid source identifier");
        }
    }

    public String entityType() {
        return value.substring(0, value.indexOf(':'));
    }

    public String sourceId() {
        return value.substring(value.indexOf(':') + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
