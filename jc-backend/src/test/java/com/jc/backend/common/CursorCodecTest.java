package com.jc.backend.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec();

    @Test
    void rejectsNonPositiveEntityId() {
        String raw = Instant.parse("2026-07-18T00:00:00Z") + "|0";
        String cursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> codec.decode(cursor))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getCode())
                                .isEqualTo("INVALID_CURSOR"));
    }
}
