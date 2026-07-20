package com.jc.intelligence.integration.search.v1;

public record SearchShadowIntegrationResult<T>(
        T legacyResponse,
        SearchShadowStatus shadowStatus,
        SearchShadowComparisonEvidenceV1 comparisonEvidence,
        SearchShadowAuthorityV1 authority) {
    public SearchShadowIntegrationResult {
        if (legacyResponse == null || shadowStatus == null || authority == null) {
            throw new IllegalArgumentException("legacyResponse, shadowStatus and authority are required");
        }
        if (!authority.equals(SearchShadowAuthorityV1.legacyOnly())) {
            throw new IllegalArgumentException("integration result must preserve legacy-only authority");
        }
        if (shadowStatus == SearchShadowStatus.DISABLED && comparisonEvidence != null) {
            throw new IllegalArgumentException("disabled integration must not fabricate comparison evidence");
        }
        if (shadowStatus != SearchShadowStatus.DISABLED && comparisonEvidence == null) {
            throw new IllegalArgumentException("active integration outcome requires evidence");
        }
    }
}
