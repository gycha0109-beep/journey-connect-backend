package com.jc.backend.admin;

import com.jc.backend.common.DomainException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;

final class AdminQueryPolicy {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_SEARCH_LENGTH = 100;
    static final int MAX_REASON_LENGTH = 1000;

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

    static String search(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw invalid("검색어는 100자 이하여야 합니다.");
        }
        return normalized;
    }

    static String optionalValue(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalid(field + " 값이 허용 범위를 벗어났습니다.");
        }
        return normalized;
    }

    static String reason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_REASON_LENGTH) {
            throw invalid("reason은 1자 이상 1000자 이하여야 합니다.");
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

    record PageBounds(int page, int size, int offset) {}
}
