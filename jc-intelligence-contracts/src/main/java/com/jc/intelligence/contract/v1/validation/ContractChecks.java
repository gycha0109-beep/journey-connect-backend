package com.jc.intelligence.contract.v1.validation;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ContractChecks {
    private static final Pattern CONTRACT_ID =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*");
    private static final Pattern VERSION =
            Pattern.compile("[a-z0-9]+(?:[-._][a-z0-9]+)*-v[1-9][0-9]*");
    private static final Pattern BUILD_ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@/+\\-]{0,127}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern NAMESPACE =
            Pattern.compile("[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9_]*)*");
    private static final Pattern FEATURE_NAME =
            Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)*");
    private static final Pattern SIMPLE_REF =
            Pattern.compile("[a-z][a-z0-9_\\-]*:[^\\s:][^\\s]{0,254}");

    private ContractChecks() {
    }

    public static String requireText(
            String value,
            String field,
            IntelligenceValidationErrorCode code) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalid(code, field + " must be nonblank and trimmed");
        }
        return value;
    }

    public static String requireContractId(String value, String field) {
        requireText(value, field, IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID);
        String lower = value.toLowerCase(Locale.ROOT);
        if (!value.equals(lower)
                || value.equals("latest")
                || value.equals("current")
                || value.equals("default")
                || value.equals("unknown")
                || !CONTRACT_ID.matcher(value).matches()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID,
                    field + " must be lowercase kebab-case and end in -vN");
        }
        return value;
    }

    public static String requireVersion(String value, String field) {
        requireText(value, field, IntelligenceValidationErrorCode.INTELLIGENCE_VERSION_INVALID);
        String lower = value.toLowerCase(Locale.ROOT);
        if (!value.equals(lower)
                || value.equals("latest")
                || value.equals("current")
                || value.equals("default")
                || value.equals("unknown")
                || !VERSION.matcher(value).matches()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_VERSION_INVALID,
                    field + " must be an explicit versioned identifier");
        }
        return value;
    }

    public static String requireBuildId(String value, String field) {
        requireText(value, field, IntelligenceValidationErrorCode.INTELLIGENCE_VERSION_INVALID);
        if (value.equalsIgnoreCase("latest")
                || value.equalsIgnoreCase("current")
                || value.equalsIgnoreCase("default")
                || value.equalsIgnoreCase("unknown")
                || !BUILD_ID.matcher(value).matches()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_VERSION_INVALID,
                    field + " must identify an immutable producer build");
        }
        return value;
    }

    public static String requireHash(String value, String field) {
        requireText(value, field, IntelligenceValidationErrorCode.INTELLIGENCE_HASH_INVALID);
        if (!HASH.matcher(value).matches()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_HASH_INVALID,
                    field + " must be lowercase SHA-256 hexadecimal");
        }
        return value;
    }

    public static String requireReasonCode(String value, String field) {
        requireText(value, field, IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID);
        if (!REASON_CODE.matcher(value).matches()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID,
                    field + " must be UPPER_SNAKE_CASE");
        }
        return value;
    }

    public static String requireNamespace(String value, String field) {
        requireText(value, field, IntelligenceValidationErrorCode.INTELLIGENCE_FEATURE_INVALID);
        if (!NAMESPACE.matcher(value).matches()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_FEATURE_INVALID,
                    field + " is not a valid feature namespace");
        }
        return value;
    }

    public static String requireFeatureName(String value, String field) {
        requireText(value, field, IntelligenceValidationErrorCode.INTELLIGENCE_FEATURE_INVALID);
        if (!FEATURE_NAME.matcher(value).matches()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_FEATURE_INVALID,
                    field + " is not a valid feature name");
        }
        return value;
    }

    public static String requireSimpleRef(String value, String field) {
        requireText(value, field, IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID);
        if (!SIMPLE_REF.matcher(value).matches()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_REFERENCE_INVALID,
                    field + " must use a registered prefix and nonblank source identifier");
        }
        return value;
    }

    public static Instant requireInstant(Instant value, String field) {
        return Objects.requireNonNull(value, field);
    }

    public static void requireOrdered(
            Instant start,
            Instant end,
            String startField,
            String endField) {
        requireInstant(start, startField);
        requireInstant(end, endField);
        if (end.isBefore(start)) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_TIME_RANGE_INVALID,
                    endField + " must not be before " + startField);
        }
    }

    public static Duration requireNonnegative(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isNegative()) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_TIME_RANGE_INVALID,
                    field + " must be nonnegative");
        }
        return value;
    }

    public static double requireConfidence(Double value, String field) {
        Objects.requireNonNull(value, field);
        double result = value.doubleValue();
        if (!Double.isFinite(result) || result < 0.0d || result > 1.0d) {
            throw invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_FEATURE_INVALID,
                    field + " must be in [0,1]");
        }
        return result;
    }

    public static IntelligenceContractValidationException invalid(
            IntelligenceValidationErrorCode code,
            String message) {
        return new IntelligenceContractValidationException(code, message);
    }
}
