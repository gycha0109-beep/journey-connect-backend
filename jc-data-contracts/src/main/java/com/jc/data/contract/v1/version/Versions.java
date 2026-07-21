package com.jc.data.contract.v1.version;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class Versions {
    private static final Pattern VERSIONED_ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*");
    private static final Pattern BUILD_ID = Pattern.compile("git:[0-9a-f]{40}");
    private static final Set<String> FORBIDDEN = Set.of("latest", "current", "default");

    private Versions() {
    }

    public record ContractVersion(String value) {
        public ContractVersion {
            value = requireVersioned(value, "contractVersion");
        }
    }

    public record SchemaVersion(String value) {
        public SchemaVersion {
            value = requireVersioned(value, "schemaVersion");
        }
    }

    public record CanonicalizationVersion(String value) {
        public CanonicalizationVersion {
            value = requireVersioned(value, "canonicalizationVersion");
        }
    }

    public record ProducerVersion(String value) {
        public ProducerVersion {
            value = requireVersioned(value, "producerVersion");
        }
    }

    public record ConsumerVersion(String value) {
        public ConsumerVersion {
            value = requireVersioned(value, "consumerVersion");
        }
    }

    public record ProducerBuildId(String value) {
        public ProducerBuildId {
            Objects.requireNonNull(value, "value");
            if (!BUILD_ID.matcher(value).matches()) {
                throw new IllegalArgumentException("producerBuildId must be git:<40-lowercase-hex>");
            }
        }
    }

    private static String requireVersioned(String value, String field) {
        Objects.requireNonNull(value, field);
        if (FORBIDDEN.contains(value) || !VERSIONED_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase versioned ID");
        }
        return value;
    }
}
