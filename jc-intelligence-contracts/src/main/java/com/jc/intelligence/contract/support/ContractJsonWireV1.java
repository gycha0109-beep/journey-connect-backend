package com.jc.intelligence.contract.support;

import java.lang.reflect.Array;
import java.util.Map;

public final class ContractJsonWireV1 {
    private ContractJsonWireV1() {
    }

    public static String stringify(Object value) {
        StringBuilder builder = new StringBuilder();
        append(value, builder);
        return builder.toString();
    }

    private static void append(Object value, StringBuilder builder) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String string) {
            appendString(string, builder);
        } else if (value instanceof Boolean bool) {
            builder.append(bool.booleanValue() ? "true" : "false");
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            builder.append(value);
        } else if (value instanceof Float number) {
            appendDouble(number.doubleValue(), builder);
        } else if (value instanceof Double number) {
            appendDouble(number.doubleValue(), builder);
        } else if (value instanceof Map<?, ?> map) {
            appendMap(map, builder);
        } else if (value instanceof Iterable<?> iterable) {
            appendIterable(iterable, builder);
        } else if (value.getClass().isArray()) {
            appendArray(value, builder);
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
        }
    }

    private static void appendMap(Map<?, ?> map, StringBuilder builder) {
        builder.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON object keys must be strings");
            }
            if (!first) {
                builder.append(',');
            }
            first = false;
            appendString(key, builder);
            builder.append(':');
            append(entry.getValue(), builder);
        }
        builder.append('}');
    }

    private static void appendIterable(Iterable<?> iterable, StringBuilder builder) {
        builder.append('[');
        boolean first = true;
        for (Object item : iterable) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            append(item, builder);
        }
        builder.append(']');
    }

    private static void appendArray(Object array, StringBuilder builder) {
        builder.append('[');
        int length = Array.getLength(array);
        for (int index = 0; index < length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            append(Array.get(array, index), builder);
        }
        builder.append(']');
    }

    private static void appendDouble(double value, StringBuilder builder) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON numbers must be finite");
        }
        if (value == 0.0d) {
            builder.append('0');
            return;
        }
        String text = Double.toString(value);
        int exponentIndex = Math.max(text.indexOf('E'), text.indexOf('e'));
        if (exponentIndex < 0) {
            builder.append(text.endsWith(".0") ? text.substring(0, text.length() - 2) : text);
            return;
        }
        String mantissa = text.substring(0, exponentIndex);
        if (mantissa.endsWith(".0")) {
            mantissa = mantissa.substring(0, mantissa.length() - 2);
        }
        String exponent = text.substring(exponentIndex + 1);
        if (exponent.startsWith("+")) {
            exponent = exponent.substring(1);
        }
        boolean negative = exponent.startsWith("-");
        String digits = negative ? exponent.substring(1) : exponent;
        int nonZero = 0;
        while (nonZero < digits.length() - 1 && digits.charAt(nonZero) == '0') {
            nonZero++;
        }
        builder.append(mantissa).append('e');
        if (negative) {
            builder.append('-');
        }
        builder.append(digits.substring(nonZero));
    }

    private static void appendString(String value, StringBuilder builder) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        builder.append('"');
    }
}
