package com.jc.intelligence.integration.search.v1;

public final class SearchShadowResponseGuard {
    public <T> T preserve(T legacyResponse, T candidateResponse) {
        if (legacyResponse == null) throw new IllegalArgumentException("legacyResponse is required");
        if (legacyResponse != candidateResponse) {
            throw new IllegalStateException("shadow integration must return the exact legacy response object");
        }
        return legacyResponse;
    }
}
