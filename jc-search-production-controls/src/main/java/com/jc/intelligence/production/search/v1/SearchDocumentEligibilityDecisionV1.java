package com.jc.intelligence.production.search.v1;

public record SearchDocumentEligibilityDecisionV1(boolean eligible, String safeReason) {
    public SearchDocumentEligibilityDecisionV1 {
        if (safeReason == null || !safeReason.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeReason must be lowercase_snake_case");
        }
        if (eligible && !"eligible".equals(safeReason)) throw new IllegalArgumentException("eligible decision reason mismatch");
        if (!eligible && "eligible".equals(safeReason)) throw new IllegalArgumentException("ineligible decision reason mismatch");
    }
    public static SearchDocumentEligibilityDecisionV1 allow() { return new SearchDocumentEligibilityDecisionV1(true,"eligible"); }
    public static SearchDocumentEligibilityDecisionV1 deny(String reason) { return new SearchDocumentEligibilityDecisionV1(false,reason); }
}
