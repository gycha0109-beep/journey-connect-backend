package com.jc.backend.verification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class StaticContractSupport {
    private StaticContractSupport() {
    }

    static void requireContains(String text, String needle, String label) {
        assertTrue(text.contains(needle), () -> "missing " + label + ": " + needle);
    }

    static void requireNotContains(String text, String needle, String label) {
        assertFalse(text.contains(needle), () -> "forbidden " + label + ": " + needle);
    }

    static void requireNoRegex(String text, String regex, String label) {
        assertFalse(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text).find(),
                () -> "forbidden " + label + " matched: " + regex);
    }

    static void assertExactCopy(Path source, Path target, String label) throws IOException {
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target),
                () -> label + ": " + RepositoryLayout.relative(source)
                        + " != " + RepositoryLayout.relative(target));
    }

    static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static void assertEqualsWithLabel(Object expected, Object actual, String label) {
        assertEquals(expected, actual, label);
    }

    static void failContract(String message) {
        fail(message);
    }
}
