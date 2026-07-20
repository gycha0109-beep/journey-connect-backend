package com.jc.intelligence.contract.v1.feature;

import com.jc.intelligence.contract.support.ImmutableCollections;
import com.jc.intelligence.contract.v1.authority.FeatureAuthorityClass;
import com.jc.intelligence.contract.v1.snapshot.PrivacyClass;
import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import java.time.Instant;
import java.util.List;

public record IntelligenceFeatureValueV1(
        ContractId contractVersion,
        String featureNamespace,
        String featureName,
        FeatureValueType valueType,
        Object value,
        FeatureAuthorityClass authorityClass,
        String sourceRef,
        FeatureDefinitionVersion definitionVersion,
        Instant observedAt,
        Instant validFrom,
        Instant validUntil,
        Double confidence,
        PrivacyClass privacyClass) {

    public IntelligenceFeatureValueV1 {
        if (!IntelligenceContractIds.INTELLIGENCE_FEATURE_VALUE.equals(contractVersion)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID,
                    "IntelligenceFeatureValueV1 requires intelligence-feature-value-v1");
        }
        featureNamespace = ContractChecks.requireNamespace(featureNamespace, "featureNamespace");
        featureName = ContractChecks.requireFeatureName(featureName, "featureName");
        java.util.Objects.requireNonNull(valueType, "valueType");
        value = normalizeValue(valueType, value);
        java.util.Objects.requireNonNull(authorityClass, "authorityClass");
        sourceRef = ContractChecks.requireSimpleRef(sourceRef, "sourceRef");
        java.util.Objects.requireNonNull(definitionVersion, "definitionVersion");
        ContractChecks.requireInstant(observedAt, "observedAt");
        if (validFrom != null && validUntil != null) {
            ContractChecks.requireOrdered(validFrom, validUntil, "validFrom", "validUntil");
        }
        if (confidence != null) {
            confidence = ContractChecks.requireConfidence(confidence, "confidence");
        }
        if ((authorityClass == FeatureAuthorityClass.SOURCE_FACT
                || authorityClass == FeatureAuthorityClass.OBSERVED_BEHAVIOR)
                && confidence != null) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_FEATURE_INVALID,
                    "source facts and observed behavior must not receive fabricated confidence");
        }
        java.util.Objects.requireNonNull(privacyClass, "privacyClass");
    }

    private static Object normalizeValue(FeatureValueType valueType, Object value) {
        java.util.Objects.requireNonNull(value, "value");
        return switch (valueType) {
            case STRING -> {
                if (!(value instanceof String string) || string.isBlank()) {
                    throw invalidValue();
                }
                yield string;
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw invalidValue();
                }
                yield value;
            }
            case LONG -> {
                if (!(value instanceof Long) && !(value instanceof Integer)) {
                    throw invalidValue();
                }
                yield ((Number) value).longValue();
            }
            case DOUBLE -> {
                if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                    throw invalidValue();
                }
                yield number.doubleValue();
            }
            case STRING_LIST -> {
                if (!(value instanceof List<?> source)) {
                    throw invalidValue();
                }
                java.util.ArrayList<String> strings = new java.util.ArrayList<>();
                for (Object item : source) {
                    if (!(item instanceof String string) || string.isBlank()) {
                        throw invalidValue();
                    }
                    strings.add(string);
                }
                yield ImmutableCollections.orderedCopy(strings, "value");
            }
        };
    }

    private static RuntimeException invalidValue() {
        return ContractChecks.invalid(
                IntelligenceValidationErrorCode.INTELLIGENCE_FEATURE_INVALID,
                "feature value does not match valueType");
    }
}
