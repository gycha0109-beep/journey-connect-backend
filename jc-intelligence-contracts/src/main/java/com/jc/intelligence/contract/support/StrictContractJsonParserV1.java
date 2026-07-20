package com.jc.intelligence.contract.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrictContractJsonParserV1 {
    private StrictContractJsonParserV1() {
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("JSON contains trailing content");
        }
        return value;
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = java.util.Objects.requireNonNull(text, "text");
        }

        private Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw error("unexpected end");
            }
            return switch (text.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (take('}')) {
                return result;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') {
                    throw error("object key must be a string");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                if (result.containsKey(key)) {
                    throw error("duplicate object key");
                }
                result.put(key, readValue());
                skipWhitespace();
                if (take('}')) {
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (take(']')) {
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (take(']')) {
                    return result;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (!atEnd()) {
                char character = text.charAt(index++);
                if (character == '"') {
                    return builder.toString();
                }
                if (character == '\\') {
                    if (atEnd()) {
                        throw error("unterminated escape");
                    }
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> builder.append(escaped);
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'n' -> builder.append('\n');
                        case 'r' -> builder.append('\r');
                        case 't' -> builder.append('\t');
                        case 'u' -> builder.append(readUnicodeEscape());
                        default -> throw error("invalid escape");
                    }
                } else {
                    if (character < 0x20) {
                        throw error("unescaped control character");
                    }
                    builder.append(character);
                }
            }
            throw error("unterminated string");
        }

        private char readUnicodeEscape() {
            if (index + 4 > text.length()) {
                throw error("incomplete unicode escape");
            }
            int value = 0;
            for (int offset = 0; offset < 4; offset++) {
                int digit = Character.digit(text.charAt(index++), 16);
                if (digit < 0) {
                    throw error("invalid unicode escape");
                }
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private Object readNumber() {
            int start = index;
            if (take('-') && atEnd()) {
                throw error("invalid number");
            }
            if (take('0')) {
                if (!atEnd() && Character.isDigit(text.charAt(index))) {
                    throw error("leading zero");
                }
            } else {
                requireDigits();
            }
            boolean floating = false;
            if (take('.')) {
                floating = true;
                requireDigits();
            }
            if (!atEnd() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                floating = true;
                index++;
                if (!atEnd() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                requireDigits();
            }
            String number = text.substring(start, index);
            try {
                if (!floating) {
                    return Long.valueOf(number);
                }
                double value = Double.parseDouble(number);
                if (!Double.isFinite(value)) {
                    throw error("number must be finite");
                }
                return Double.valueOf(value);
            } catch (NumberFormatException exception) {
                throw error("invalid number", exception);
            }
        }

        private void requireDigits() {
            int start = index;
            while (!atEnd() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw error("expected digit");
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw error("invalid literal");
            }
            index += literal.length();
            return value;
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char character = text.charAt(index);
                if (character != ' ' && character != '\n' && character != '\r' && character != '\t') {
                    return;
                }
                index++;
            }
        }

        private boolean take(char expected) {
            if (!atEnd() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) {
                throw error("expected '" + expected + "'");
            }
        }

        private boolean atEnd() {
            return index >= text.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid JSON at index " + index + ": " + message);
        }

        private IllegalArgumentException error(String message, Exception cause) {
            return new IllegalArgumentException("Invalid JSON at index " + index + ": " + message, cause);
        }
    }
}
