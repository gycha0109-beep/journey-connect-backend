package com.jc.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.common.DomainException;
import com.jc.backend.recommendation.config.RecommendationProperties;
import org.junit.jupiter.api.Test;

class RecommendationCursorCodecTest {

    @Test
    void signedCursorBindsRunOffsetUserAndSession() {
        RecommendationCursorCodec codec = codec();
        String cursor = codec.encode("run:abc", 20, 7L, "jwt-7");

        assertThat(codec.isRecommendationCursor(cursor)).isTrue();
        assertThat(codec.decode(cursor, 7L, "jwt-7"))
                .isEqualTo(new RecommendationCursorCodec.Cursor("run:abc", 20));
        assertThatThrownBy(() -> codec.decode(cursor, 8L, "jwt-7"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> codec.decode(cursor, 7L, "jwt-other"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> codec.decode(cursor + "0", 7L, "jwt-7"))
                .isInstanceOf(DomainException.class);

        RecommendationProperties nextRelease = properties("release:p0-6-next");
        RecommendationCursorCodec nextCodec = new RecommendationCursorCodec(nextRelease);
        assertThatThrownBy(() -> nextCodec.decode(cursor, 7L, "jwt-7"))
                .isInstanceOf(DomainException.class);
    }

    private static RecommendationCursorCodec codec() {
        return new RecommendationCursorCodec(properties("release:p0-6"));
    }

    private static RecommendationProperties properties(String releaseId) {
        RecommendationProperties properties = new RecommendationProperties();
        properties.setCanaryCursorSecret("0123456789abcdef0123456789abcdef");
        properties.setCanaryReleaseId(releaseId);
        return properties;
    }
}
