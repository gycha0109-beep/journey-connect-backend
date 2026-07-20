package com.jc.backend.common;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 피드 정렬 키(publishedAt, id)를 URL-safe Base64 문자열로 변환합니다. */
@Component
public class CursorCodec {

    private static final String SEPARATOR = "|";

    public String encode(Instant publishedAt, Long id) {
        String raw = publishedAt + SEPARATOR + id;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public CursorPosition decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return CursorPosition.initial();
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            Instant publishedAt = Instant.parse(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw invalidCursor();
            }
            return new CursorPosition(publishedAt, id);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidCursor();
        }
    }

    private DomainException invalidCursor() {
        return new DomainException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CURSOR",
                "피드 커서 형식이 올바르지 않습니다.");
    }

    public record CursorPosition(Instant publishedAt, Long id) {
        public static CursorPosition initial() {
            return new CursorPosition(null, null);
        }

        public boolean isInitial() {
            return publishedAt == null || id == null;
        }
    }
}
