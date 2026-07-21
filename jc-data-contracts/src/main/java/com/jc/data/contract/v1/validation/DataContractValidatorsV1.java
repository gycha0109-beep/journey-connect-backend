package com.jc.data.contract.v1.validation;

import com.jc.data.contract.v1.event.EventDefinitionV1;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class DataContractValidatorsV1 {
    private static final Pattern SNAKE_CASE = Pattern.compile("[a-z][a-z0-9]*(?:_[a-z0-9]+)*");
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "accesstoken", "refreshtoken", "authorization", "password", "secret", "secretkey",
            "apikey", "token", "rawuserid", "userid", "accountid", "actorref", "subjectref",
            "email", "phone", "rawquery", "query", "latitude", "longitude", "gps",
            "freetext", "rawtext", "rawcontent", "rawprompt", "credential");
    private static final Set<String> PLATFORM_EVENT_FINGERPRINT_KEYS = Set.of(
            "contractVersion", "schemaVersion", "canonicalizationVersion", "eventFamily",
            "eventType", "occurredAt", "actorRef", "sessionRef", "entityRef",
            "causationId", "payload");
    private static final int MAX_DEPTH = 8;
    private static final int MAX_ENTRIES = 100;
    private static final int MAX_ARRAY_LENGTH = 100;
    private static final int MAX_STRING_LENGTH = 2048;
    private static final int MAX_ESTIMATED_BYTES = 8192;

    private DataContractValidatorsV1() {
    }

    public static boolean isLowerSnakeCase(String value) {
        return value != null && SNAKE_CASE.matcher(value).matches();
    }

    public static ValidationResult<Instant> parseCanonicalUtcInstant(String value, String field) {
        ArrayList<ValidationError> errors = new ArrayList<>();
        if (value == null || value.isBlank()) {
            errors.add(new ValidationError(DataValidationErrorCode.REQUIRED_FIELD_MISSING, field, "timestamp required"));
            return ValidationResult.invalid(errors);
        }
        if (!value.endsWith("Z")) {
            errors.add(new ValidationError(DataValidationErrorCode.NON_UTC_TIMESTAMP, field, "UTC Z required"));
            return ValidationResult.invalid(errors);
        }
        try {
            return ValidationResult.valid(Instant.parse(value));
        } catch (DateTimeParseException exception) {
            errors.add(new ValidationError(DataValidationErrorCode.INVALID_TIMESTAMP, field, "invalid ISO-8601 instant"));
            return ValidationResult.invalid(errors);
        }
    }

    public static List<ValidationError> validatePayload(Map<String, Object> payload, EventDefinitionV1 definition) {
        ArrayList<ValidationError> errors = new ArrayList<>();
        validateValue(payload, "payload", 0, errors, new Counter());
        if (definition != null) {
            for (String required : definition.requiredPayloadFields()) {
                if (!payload.containsKey(required) || payload.get(required) == null) {
                    errors.add(new ValidationError(
                            DataValidationErrorCode.REQUIRED_FIELD_MISSING, "payload." + required, "required payload field"));
                }
            }
            Set<String> allowed = definition.allowedPayloadFields();
            for (String field : payload.keySet()) {
                if (!allowed.contains(field)) {
                    errors.add(new ValidationError(
                            DataValidationErrorCode.INVALID_PAYLOAD, "payload." + field, "field not allowed for event type"));
                }
            }
        }
        return List.copyOf(errors);
    }

    public static List<ValidationError> validateApprovedCanonicalMap(Map<String, Object> values) {
        ArrayList<ValidationError> errors = new ArrayList<>();
        validateValue(values, "canonical", 0, errors, new Counter());
        return List.copyOf(errors);
    }

    public static List<ValidationError> validatePlatformEventFingerprintCanonicalMap(
            Map<String, Object> values) {
        ArrayList<ValidationError> errors = new ArrayList<>();
        if (values == null) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.REQUIRED_FIELD_MISSING, "canonical", "required"));
            return List.copyOf(errors);
        }
        if (!values.keySet().equals(PLATFORM_EVENT_FINGERPRINT_KEYS)) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.INVALID_PAYLOAD,
                    "canonical",
                    "exact platform event fingerprint fields required"));
            return List.copyOf(errors);
        }
        Counter counter = new Counter();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            counter.add(entry.getKey().length());
            validateValue(entry.getValue(), "canonical." + entry.getKey(), 1, errors, counter);
        }
        checkBudget("canonical", counter, errors);
        return List.copyOf(errors);
    }

    private static void validateValue(
            Object value,
            String path,
            int depth,
            List<ValidationError> errors,
            Counter counter) {
        if (depth > MAX_DEPTH) {
            errors.add(new ValidationError(DataValidationErrorCode.INVALID_PAYLOAD, path, "maximum nesting exceeded"));
            return;
        }
        if (value == null || value instanceof Boolean || value instanceof Instant) {
            counter.add(4);
            return;
        }
        if (value instanceof String string) {
            if (string.length() > MAX_STRING_LENGTH) {
                errors.add(new ValidationError(DataValidationErrorCode.INVALID_PAYLOAD, path, "string too long"));
            }
            counter.add(string.length());
            checkBudget(path, counter, errors);
            return;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigInteger
                || value instanceof BigDecimal) {
            if (value instanceof Double number && !Double.isFinite(number)) {
                errors.add(new ValidationError(DataValidationErrorCode.INVALID_PAYLOAD, path, "number must be finite"));
            }
            if (value instanceof Float number && !Float.isFinite(number)) {
                errors.add(new ValidationError(DataValidationErrorCode.INVALID_PAYLOAD, path, "number must be finite"));
            }
            counter.add(32);
            checkBudget(path, counter, errors);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_ENTRIES) {
                errors.add(new ValidationError(DataValidationErrorCode.INVALID_PAYLOAD, path, "too many object fields"));
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    errors.add(new ValidationError(DataValidationErrorCode.INVALID_PAYLOAD, path, "object key must be string"));
                    continue;
                }
                String normalized = normalizeKey(key);
                if (isForbidden(normalized)) {
                    errors.add(new ValidationError(
                            DataValidationErrorCode.FORBIDDEN_PAYLOAD_FIELD, path + "." + key, "sensitive/raw field forbidden"));
                }
                counter.add(key.length());
                validateValue(entry.getValue(), path + "." + key, depth + 1, errors, counter);
            }
            checkBudget(path, counter, errors);
            return;
        }
        if (value instanceof List<?> list) {
            if (list.size() > MAX_ARRAY_LENGTH) {
                errors.add(new ValidationError(DataValidationErrorCode.INVALID_PAYLOAD, path, "array too long"));
            }
            for (int index = 0; index < list.size(); index++) {
                validateValue(list.get(index), path + "[" + index + "]", depth + 1, errors, counter);
            }
            checkBudget(path, counter, errors);
            return;
        }
        errors.add(new ValidationError(
                DataValidationErrorCode.INVALID_PAYLOAD, path, "unsupported value type: " + value.getClass().getName()));
    }

    private static String normalizeKey(String key) {
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < key.length(); index++) {
            char character = Character.toLowerCase(key.charAt(index));
            if (Character.isLetterOrDigit(character)) {
                normalized.append(character);
            }
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isForbidden(String normalized) {
        return FORBIDDEN_KEYS.contains(normalized)
                || normalized.endsWith("token")
                || normalized.endsWith("password")
                || normalized.endsWith("secret")
                || normalized.endsWith("credential");
    }

    private static void checkBudget(String path, Counter counter, List<ValidationError> errors) {
        if (counter.value > MAX_ESTIMATED_BYTES
                && errors.stream().noneMatch(error -> error.field().equals(path) && error.detail().contains("size"))) {
            errors.add(new ValidationError(DataValidationErrorCode.INVALID_PAYLOAD, path, "estimated payload size exceeded"));
        }
    }

    private static final class Counter {
        private int value;

        private void add(int amount) {
            value = Math.min(Integer.MAX_VALUE, value + amount);
        }
    }
}
