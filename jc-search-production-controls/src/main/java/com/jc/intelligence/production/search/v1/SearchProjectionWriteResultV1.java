package com.jc.intelligence.production.search.v1;

public record SearchProjectionWriteResultV1(SearchProjectionWriteStatus status, long sourcePostId, String safeReason) {
    public SearchProjectionWriteResultV1 {
        if (status==null || sourcePostId<1 || safeReason==null || !safeReason.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("write result invalid");
        }
    }
}
