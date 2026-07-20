package com.jc.backend.recommendation.application;

import com.jc.backend.common.DomainException;
import com.jc.backend.recommendation.config.RecommendationProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Signed opaque cursor binding a CANARY page to its user, session, run, and offset. */
@Component
public final class RecommendationCursorCodec {

    public static final String PREFIX = "rc1.";
    public static final String VERSION = "recommendation-cursor-v1";

    private final RecommendationProperties properties;

    public RecommendationCursorCodec(RecommendationProperties properties) {
        this.properties = properties;
    }

    public String encode(String runId, int offset, long userId, String sessionId) {
        if (runId == null || !runId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("runId format is invalid");
        }
        if (offset < 0 || userId <= 0 || sessionId == null
                || !sessionId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("cursor binding is invalid");
        }
        String body = runId + "|" + offset + "|" + userId + "|" + sessionId
                + "|" + properties.getCanaryReleaseId();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded + "." + signature(encoded);
    }

    public Cursor decode(String cursor, long expectedUserId, String expectedSessionId) {
        try {
            if (!isRecommendationCursor(cursor) || cursor.length() > 512
                    || expectedUserId <= 0 || expectedSessionId == null) {
                throw invalid();
            }
            String[] parts = cursor.substring(PREFIX.length()).split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(
                    HexFormat.of().parseHex(parts[1]),
                    HexFormat.of().parseHex(signature(parts[0])))) {
                throw invalid();
            }
            String raw = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = raw.split("\\|", -1);
            if (values.length != 5
                    || !values[0].matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
                throw invalid();
            }
            int offset = Integer.parseInt(values[1]);
            long userId = Long.parseLong(values[2]);
            if (offset < 0 || userId != expectedUserId
                    || !values[3].equals(expectedSessionId)
                    || !values[4].equals(properties.getCanaryReleaseId())) {
                throw invalid();
            }
            return new Cursor(values[0], offset);
        } catch (DomainException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    public boolean isRecommendationCursor(String cursor) {
        return cursor != null && cursor.startsWith(PREFIX);
    }

    private String signature(String encodedBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getCanaryCursorSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    ("journey-connect:recommendation-cursor:v1:" + encodedBody)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign recommendation cursor", exception);
        }
    }

    private DomainException invalid() {
        return new DomainException(
                HttpStatus.BAD_REQUEST,
                "INVALID_RECOMMENDATION_CURSOR",
                "추천 피드 커서가 유효하지 않습니다.");
    }

    public record Cursor(String runId, int offset) {}
}
