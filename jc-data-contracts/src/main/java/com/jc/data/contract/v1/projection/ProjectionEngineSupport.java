package com.jc.data.contract.v1.projection;

import java.util.Objects;
import java.util.regex.Pattern;

final class ProjectionEngineSupport {
    private static final Pattern VERSION = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*-v[1-9][0-9]*$");
    private static final Pattern FINGERPRINT = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]*$");
    private static final Pattern REFERENCE = Pattern.compile("^[a-z][a-z0-9_]*:[A-Za-z0-9][A-Za-z0-9._:~-]{0,159}$");
    private static final Pattern SUBJECT = Pattern.compile("^subject:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$");
    private static final Pattern USER = Pattern.compile("^user:[1-9][0-9]*$");

    private ProjectionEngineSupport() {
    }

    static String requireVersion(String value, String field) {
        String checked = requireToken(value, field, 96);
        if (!VERSION.matcher(checked).matches()) {
            throw new IllegalArgumentException(field + " must be a semantic contract version");
        }
        return checked;
    }

    static String requireFingerprint(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!FINGERPRINT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hex");
        }
        return value;
    }

    static String requireToken(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maxLength || !TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    static String requireReference(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!REFERENCE.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    static String requireTypedReference(String value, String type, String field) {
        String checked = requireReference(value, field);
        if (!checked.startsWith(type + ":")) {
            throw new IllegalArgumentException(field + " must use " + type + " namespace");
        }
        return checked;
    }

    static String requireIdentity(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!SUBJECT.matcher(value).matches() && !USER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be subject or user identity");
        }
        return value;
    }

    static String requireSubject(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!SUBJECT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be opaque subject identity");
        }
        return value;
    }

    static String requireUser(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!USER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be numeric user identity");
        }
        return value;
    }

    static String requireCanonicalForm(String value) {
        Objects.requireNonNull(value, "sourceCanonicalForm");
        if (value.isEmpty() || value.length() > 262_144) {
            throw new IllegalArgumentException("sourceCanonicalForm size is invalid");
        }
        return value;
    }
}
