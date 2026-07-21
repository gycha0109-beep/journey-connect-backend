package com.jc.data.contract.v1.canonical;

import com.jc.data.contract.v1.validation.DataContractValidatorsV1;
import com.jc.data.contract.v1.validation.DataValidationErrorCode;
import com.jc.data.contract.v1.validation.ValidationError;
import com.jc.data.contract.v1.validation.PlatformEventEnvelopeValidatorV1;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CanonicalJsonNormalizerV1 {
    public CanonicalizationResultV1 canonicalize(CanonicalizationRequestV1 request) {
        return canonicalize(request, false);
    }

    public CanonicalizationResultV1 canonicalizePlatformEventFingerprintV1(
            CanonicalizationRequestV1 request) {
        return canonicalize(request, true);
    }

    private CanonicalizationResultV1 canonicalize(
            CanonicalizationRequestV1 request,
            boolean platformEventFingerprint) {
        ArrayList<ValidationError> errors = new ArrayList<>();
        if (request == null || request.canonicalizationVersion() == null) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_CANONICALIZATION_VERSION,
                    "canonicalizationVersion", "required"));
            return new CanonicalizationResultV1(null, errors);
        }
        if (!PlatformEventEnvelopeValidatorV1.CANONICALIZATION_VERSION
                .equals(request.canonicalizationVersion().value())) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_CANONICALIZATION_VERSION,
                    "canonicalizationVersion", "unsupported"));
            return new CanonicalizationResultV1(null, errors);
        }
        errors.addAll(platformEventFingerprint
                ? DataContractValidatorsV1.validatePlatformEventFingerprintCanonicalMap(
                        request.approvedFields())
                : DataContractValidatorsV1.validateApprovedCanonicalMap(request.approvedFields()));
        if (!errors.isEmpty()) {
            return new CanonicalizationResultV1(null, errors);
        }
        try {
            StringBuilder output = new StringBuilder();
            appendObject(output, request.approvedFields());
            return new CanonicalizationResultV1(output.toString().getBytes(StandardCharsets.UTF_8), List.of());
        } catch (IllegalArgumentException exception) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.CANONICALIZATION_FAILURE, "canonical", exception.getMessage()));
            return new CanonicalizationResultV1(null, errors);
        }
    }

    private static void appendValue(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String string) {
            appendString(output, string);
        } else if (value instanceof Boolean bool) {
            output.append(bool.booleanValue() ? "true" : "false");
        } else if (value instanceof Instant instant) {
            appendString(output, instant.toString());
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger) {
            output.append(value.toString());
        } else if (value instanceof BigDecimal decimal) {
            appendDecimal(output, decimal);
        } else if (value instanceof Double number) {
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("non-finite number");
            }
            appendDecimal(output, BigDecimal.valueOf(number));
        } else if (value instanceof Float number) {
            if (!Float.isFinite(number)) {
                throw new IllegalArgumentException("non-finite number");
            }
            appendDecimal(output, new BigDecimal(Float.toString(number)));
        } else if (value instanceof Map<?, ?> map) {
            appendObject(output, requireStringKeyMap(map));
        } else if (value instanceof List<?> list) {
            appendArray(output, list);
        } else {
            throw new IllegalArgumentException("unsupported canonical value type");
        }
    }

    private static void appendObject(StringBuilder output, Map<String, ?> values) {
        output.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> entry : new TreeMap<>(values).entrySet()) {
            if (!first) {
                output.append(',');
            }
            first = false;
            appendString(output, entry.getKey());
            output.append(':');
            appendValue(output, entry.getValue());
        }
        output.append('}');
    }

    private static void appendArray(StringBuilder output, List<?> values) {
        output.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            appendValue(output, values.get(index));
        }
        output.append(']');
    }

    private static void appendDecimal(StringBuilder output, BigDecimal value) {
        BigDecimal normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        output.append(normalized.toPlainString());
    }

    private static void appendString(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else if (Character.isHighSurrogate(character)) {
                        if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                            throw new IllegalArgumentException("unpaired high surrogate");
                        }
                        output.append(character).append(value.charAt(++index));
                    } else if (Character.isLowSurrogate(character)) {
                        throw new IllegalArgumentException("unpaired low surrogate");
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }

    private static Map<String, Object> requireStringKeyMap(Map<?, ?> source) {
        TreeMap<String, Object> result = new TreeMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("canonical object key must be string");
            }
            if (result.put(key, entry.getValue()) != null) {
                throw new IllegalArgumentException("duplicate canonical object key");
            }
        }
        return result;
    }
}
