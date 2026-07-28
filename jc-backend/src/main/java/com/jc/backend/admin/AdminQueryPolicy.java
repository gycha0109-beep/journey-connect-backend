package com.jc.backend.admin;

import com.jc.backend.common.DomainException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

final class AdminQueryPolicy {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_SEARCH_LENGTH = 100;
    static final int MAX_REASON_LENGTH = 1000;

    private static final Pattern SECRET_MATERIAL = Pattern.compile(
            "(?i)(authorization\\s*:|bearer\\s+[a-z0-9._~+/-]{16,}|"
                    + "(?:access|refresh)[_-]?token\\s*[:=]|password\\s*[:=]|"
                    + "cookie\\s*:|eyJ[a-zA-Z0-9_-]{16,}\\.[a-zA-Z0-9_-]{8,})");

    private AdminQueryPolicy() {}

    static PageBounds page(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("page는 0 이상, size는 1 이상 100 이하여야 합니다.");
        }
        if (page > Integer.MAX_VALUE / size) {
            throw invalid("page 범위가 너무 큽니다.");
        }
        return new PageBounds(page, size, page * size);
    }

    static long targetId(long value) {
        if (value < 1) {
            throw invalid("관리 대상 ID는 1 이상이어야 합니다.");
        }
        return value;
    }

    static String search(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value).strip();
        if (normalized.isEmpty()) {
            return null;
        }
        rejectControls(normalized, "검색어");
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw invalid("검색어는 100자 이하여야 합니다.");
        }
        return normalized;
    }

    static String optionalValue(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalize(value).strip().toLowerCase(Locale.ROOT);
        rejectControls(normalized, field);
        if (!allowed.contains(normalized)) {
            throw invalid(field + " 값이 허용 범위를 벗어났습니다.");
        }
        return normalized;
    }

    static String reason(String value) {
        String normalized = value == null ? "" : normalize(value).strip();
        if (normalized.isEmpty() || normalized.length() > MAX_REASON_LENGTH) {
            throw invalid("reason은 1자 이상 1000자 이하여야 합니다.");
        }
        rejectControls(normalized, "reason");
        if (SECRET_MATERIAL.matcher(normalized).find()) {
            throw invalid("reason에는 token, password, cookie 또는 인증 헤더를 포함할 수 없습니다.");
        }
        return normalized;
    }

    static DomainException invalid(String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_COMMAND", message);
    }

    static DomainException notFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "ADMIN_TARGET_NOT_FOUND",
                "관리 대상 정보를 찾을 수 없습니다.");
    }

    static DomainException conflict(String message) {
        return new DomainException(HttpStatus.CONFLICT, "ADMIN_STATE_CONFLICT", message);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC);
    }

    private static void rejectControls(String value, String field) {
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid(field + "에는 제어 문자를 포함할 수 없습니다.");
        }
    }

    record PageBounds(int page, int size, int offset) {}
}
