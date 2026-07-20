package com.jc.intelligence.production.search.v1;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class SearchProjectionTextNormalizer {
    private SearchProjectionTextNormalizer() { }
    public static List<String> terms(String value, int maximumTerms) {
        if (maximumTerms < 1 || maximumTerms > 2048) throw new IllegalArgumentException("maximumTerms must be 1..2048");
        if (value == null || value.isBlank()) return List.of();
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        StringBuilder token = new StringBuilder();
        for (int offset=0; offset<normalized.length();) {
            int cp=normalized.codePointAt(offset); offset+=Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) token.appendCodePoint(cp);
            else flush(token, unique, maximumTerms);
            if (unique.size()>=maximumTerms) break;
        }
        flush(token, unique, maximumTerms);
        return List.copyOf(new ArrayList<>(unique));
    }
    private static void flush(StringBuilder token, LinkedHashSet<String> unique, int maximumTerms) {
        if (token.length()>0 && unique.size()<maximumTerms) {
            String value=token.toString();
            if (value.length()<=128) unique.add(value);
            token.setLength(0);
        } else token.setLength(0);
    }
}
