package com.jc.recommendation.support;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StrictUtcMilliseconds {
    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(?:\\.(\\d{3}))?Z$"
    );

    private StrictUtcMilliseconds() {
    }

    public static long parseEpochMilli(String value, String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName");
        Matcher matcher = value == null ? null : PATTERN.matcher(value);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException(fieldName + " must use strict ISO 8601 UTC Z format");
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));
        int hour = Integer.parseInt(matcher.group(4));
        int minute = Integer.parseInt(matcher.group(5));
        int second = Integer.parseInt(matcher.group(6));
        int millisecond = matcher.group(7) == null ? 0 : Integer.parseInt(matcher.group(7));
        try {
            return LocalDateTime.of(year, month, day, hour, minute, second, millisecond * 1_000_000)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(fieldName + " is not a real UTC calendar timestamp", exception);
        }
    }
}
