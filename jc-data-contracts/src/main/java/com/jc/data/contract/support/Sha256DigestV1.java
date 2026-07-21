package com.jc.data.contract.support;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class Sha256DigestV1 {
    private static final char[] LOWERCASE_HEX = "0123456789abcdef".toCharArray();

    private Sha256DigestV1() {
    }

    public static String lowercaseHex(byte[] value) {
        Objects.requireNonNull(value, "value");
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
        char[] output = new char[digest.length * 2];
        for (int index = 0; index < digest.length; index++) {
            int unsigned = digest[index] & 0xff;
            output[index * 2] = LOWERCASE_HEX[unsigned >>> 4];
            output[index * 2 + 1] = LOWERCASE_HEX[unsigned & 0x0f];
        }
        return new String(output);
    }
}
