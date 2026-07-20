package com.jc.backend.recommendation.persistence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RecommendationHashing {

    private static final byte[] SNAPSHOT_DOMAIN =
            "journey-connect:snapshot:v1".getBytes(StandardCharsets.UTF_8);

    private RecommendationHashing() {}

    public static String sha256(byte[] payload) {
        return HexFormat.of().formatHex(digest(payload));
    }

    public static String snapshotSha256(String kind, String schemaVersion, byte[] payload) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(SNAPSHOT_DOMAIN);
            output.write(0);
            output.write(kind.getBytes(StandardCharsets.UTF_8));
            output.write(0);
            output.write(schemaVersion.getBytes(StandardCharsets.UTF_8));
            output.write(0);
            output.write(payload);
            return HexFormat.of().formatHex(digest(output.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Snapshot hash input could not be built.", exception);
        }
    }

    private static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
