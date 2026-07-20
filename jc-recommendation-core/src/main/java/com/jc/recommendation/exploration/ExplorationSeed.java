package com.jc.recommendation.exploration;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ExplorationSeed {
    private ExplorationSeed() {
    }

    public static long fnv1a32Utf8(String value) {
        Objects.requireNonNull(value, "value");
        int hash = 0x811c9dc5;
        for (byte signedByte : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= Byte.toUnsignedInt(signedByte);
            hash *= 0x01000193;
        }
        return Integer.toUnsignedLong(hash);
    }

    public static String material(
            String explorationPolicyVersion,
            String rankingSnapshotId,
            String metadataSnapshotId,
            String explorationSnapshotId,
            String explorationSeed,
            String entityType,
            String entityId
    ) {
        return String.join("\u0000",
                explorationPolicyVersion,
                rankingSnapshotId,
                metadataSnapshotId,
                explorationSnapshotId,
                explorationSeed,
                entityType,
                entityId
        );
    }
}
