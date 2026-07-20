package com.jc.backend.recommendation.application;

import com.jc.backend.recommendation.persistence.RecommendationHashing;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

final class RecommendationSessionIds {

    private RecommendationSessionIds() {}

    static String fromJwt(long userId, String tokenId) {
        if (tokenId != null && tokenId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            return tokenId;
        }
        byte[] material = (userId + ":" + String.valueOf(tokenId))
                .getBytes(StandardCharsets.UTF_8);
        byte[] digest = HexFormat.of().parseHex(RecommendationHashing.sha256(material));
        return "jwt:" + HexFormat.of().formatHex(Arrays.copyOf(digest, 16));
    }
}
