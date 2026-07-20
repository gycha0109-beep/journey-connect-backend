package com.jc.intelligence.contract.v1.search.query;

import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import java.time.Instant;

public record SearchContextV1(
        SubjectRef subjectRef,
        String sessionRef,
        SearchSurface surface,
        SearchEntityScope entityScope,
        Instant referenceTime,
        String languageHint,
        String localeHint,
        EntityRef coarseRegionRef,
        String consentPrivacyContextRef) {
    public SearchContextV1 {
        if (sessionRef != null) sessionRef = SearchChecks.requireOpaqueId(sessionRef, "sessionRef");
        SearchChecks.requireNonNull(surface, "surface");
        SearchChecks.requireNonNull(entityScope, "entityScope");
        SearchChecks.requireInstant(referenceTime, "referenceTime");
        if (languageHint != null) languageHint = SearchChecks.requireNoWhitespace(languageHint, "languageHint", 32);
        if (localeHint != null) localeHint = SearchChecks.requireNoWhitespace(localeHint, "localeHint", 64);
        if (coarseRegionRef != null && !"region".equals(coarseRegionRef.entityType())) {
            throw SearchChecks.invalid(
                    com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                    "coarseRegionRef must use region entity type");
        }
        if (consentPrivacyContextRef != null) {
            consentPrivacyContextRef = SearchChecks.requireOptionalRef(
                    consentPrivacyContextRef, "consentPrivacyContextRef");
        }
    }
}
