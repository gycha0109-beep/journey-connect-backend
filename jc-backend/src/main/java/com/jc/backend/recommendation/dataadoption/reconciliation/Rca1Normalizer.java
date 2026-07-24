package com.jc.backend.recommendation.dataadoption.reconciliation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Rca1Normalizer {
    private Rca1Normalizer() {}

    public static Rca1Contracts.NormalizedValue map(Map<?,?> source) {
        TreeMap<String,String> sorted = new TreeMap<>();
        source.forEach((key,value) -> sorted.put(String.valueOf(key), String.valueOf(value)));
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        sorted.forEach((key,value) -> joiner.add(escape(key)+"="+escape(value)));
        return new Rca1Contracts.NormalizedValue(joiner.toString());
    }

    public static Rca1Contracts.NormalizedValue collection(Collection<?> source) {
        TreeSet<String> sorted = new TreeSet<>();
        for (Object value : source) sorted.add(String.valueOf(value));
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        sorted.forEach(value -> joiner.add(escape(value)));
        return new Rca1Contracts.NormalizedValue(joiner.toString());
    }

    public static Rca1Contracts.NormalizedValue scalar(Object value) {
        return new Rca1Contracts.NormalizedValue(escape(String.valueOf(value)));
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte item : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", item));
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static String safe(String value) {
        if (value == null || value.isBlank()) return "<empty>";
        if (value.startsWith("synthetic-subject:") || value.startsWith("synthetic-user:")
                || value.startsWith("synthetic-session:") || value.startsWith("synthetic-run:")) {
            return "sha256:" + hash(value);
        }
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String json(String value) {
        StringBuilder out = new StringBuilder(value.length()+8);
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            switch(c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> { if(c<0x20) out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c)); else out.append(c); }
            }
        }
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("\\","\\\\").replace(",","\\,").replace("=","\\=")
                .replace("[","\\[").replace("]","\\]").replace("{","\\{").replace("}","\\}")
                .replace("\r\n","\n").replace('\r','\n');
    }
}
