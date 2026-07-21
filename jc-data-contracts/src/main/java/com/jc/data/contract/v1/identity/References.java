package com.jc.data.contract.v1.identity;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class References {
    private static final Pattern SIMPLE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._~-]{0,127}");
    private static final Pattern OPAQUE_SUBJECT = Pattern.compile("subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}");
    private static final Pattern LEGACY_USER = Pattern.compile("user:[1-9][0-9]{0,18}");
    private static final Pattern IDEMPOTENCY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:~-]{0,127}");
    private static final Set<String> ENTITY_TYPES = Set.of(
            "post", "journey", "place", "crew", "user", "tag", "region", "itinerary", "profile", "search_result");

    private References() {
    }

    public record EventId(String value) {
        public EventId {
            value = requirePrefixed(value, "event");
        }
    }

    public record SessionRef(String value) {
        public SessionRef {
            value = requirePrefixed(value, "session");
        }
    }

    public record RequestRef(String value) {
        public RequestRef {
            value = requirePrefixed(value, "request");
        }
    }

    public record CorrelationRef(String value) {
        public CorrelationRef {
            value = requirePrefixed(value, "correlation");
        }
    }

    public record CausationRef(String value) {
        public CausationRef {
            value = requirePrefixed(value, "event", "command", "request", "operation");
        }
    }

    public record SubjectRef(IdentityScheme scheme, String value) {
        public SubjectRef {
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(value, "value");
            boolean valid = switch (scheme) {
                case PLATFORM_SUBJECT_V1 -> OPAQUE_SUBJECT.matcher(value).matches();
                case LEGACY_USER_NUMERIC_V1 -> LEGACY_USER.matcher(value).matches();
            };
            if (!valid) {
                throw new IllegalArgumentException("identity reference does not match scheme");
            }
        }
    }

    public record ActorRef(SubjectRef subject) {
        public ActorRef {
            Objects.requireNonNull(subject, "subject");
            if (subject.scheme() != IdentityScheme.PLATFORM_SUBJECT_V1) {
                throw new IllegalArgumentException("canonical actor requires platform_subject_v1");
            }
        }

        public String value() {
            return subject.value();
        }
    }

    public record EntityRef(String value) {
        public EntityRef {
            Objects.requireNonNull(value, "value");
            int separator = value.indexOf(':');
            if (separator <= 0 || separator == value.length() - 1 || value.length() > 160 || containsWhitespace(value)) {
                throw new IllegalArgumentException("malformed entity reference");
            }
            String entityType = value.substring(0, separator);
            String sourceId = value.substring(separator + 1);
            if (!entityType.matches("[a-z][a-z0-9_]{0,31}") || !ENTITY_TYPES.contains(entityType)) {
                throw new IllegalArgumentException("unsupported entity type");
            }
            if (sourceId.length() > 128) {
                throw new IllegalArgumentException("entity source id too long");
            }
        }

        public String entityType() {
            return value.substring(0, value.indexOf(':'));
        }

        public String sourceId() {
            return value.substring(value.indexOf(':') + 1);
        }
    }

    public record IdempotencyKey(String value) {
        public IdempotencyKey {
            Objects.requireNonNull(value, "value");
            if (!IDEMPOTENCY.matcher(value).matches()) {
                throw new IllegalArgumentException("invalid idempotency key");
            }
        }
    }

    private static String requirePrefixed(String value, String... allowedPrefixes) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || value.length() > 160 || containsWhitespace(value)) {
            throw new IllegalArgumentException("malformed prefixed reference");
        }
        String prefix = value.substring(0, separator);
        String id = value.substring(separator + 1);
        boolean prefixAllowed = false;
        for (String allowedPrefix : allowedPrefixes) {
            if (allowedPrefix.equals(prefix)) {
                prefixAllowed = true;
                break;
            }
        }
        if (!prefixAllowed || !SIMPLE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("unsupported reference prefix or id");
        }
        return value;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
