package com.jc.backend.search.shadow.stage;

public record StageSearchShadowSubmissionResult(StageSearchShadowSubmissionStatus status, String safeCode) {
    public StageSearchShadowSubmissionResult {
        if (status == null) throw new IllegalArgumentException("status is required");
        if (safeCode == null || !safeCode.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeCode must be lowercase_snake_case");
        }
    }
}
