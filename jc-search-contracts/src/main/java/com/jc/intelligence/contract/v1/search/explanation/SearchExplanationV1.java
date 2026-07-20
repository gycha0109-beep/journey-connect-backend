package com.jc.intelligence.contract.v1.search.explanation;

import com.jc.intelligence.contract.v1.explanation.ExplanationAudience;
import com.jc.intelligence.contract.v1.explanation.IntelligenceExplanationV1;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchExplanationReason;
import com.jc.intelligence.contract.v1.search.validation.SearchChecks;
import com.jc.intelligence.contract.v1.search.validation.SearchVersionValidatorV1;
import com.jc.intelligence.contract.v1.snapshot.PrivacyClass;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SearchExplanationV1(
        ContractId contractVersion,
        String explanationId,
        RunRef runId,
        ExplanationAudience audience,
        List<SearchExplanationReason> reasonCodes,
        String message,
        List<String> evidenceRefs,
        Map<String, String> attributes,
        PrivacyClass privacyClass,
        Instant createdAt) {
    public SearchExplanationV1 {
        SearchVersionValidatorV1.requireContract(contractVersion, SearchContractIds.SEARCH_DOMAIN);
        explanationId = SearchChecks.requireOptionalRef(explanationId, "explanationId");
        SearchChecks.requireNonNull(runId, "runId");
        SearchChecks.requireNonNull(audience, "audience");
        if (reasonCodes == null || reasonCodes.isEmpty()) {
            throw SearchChecks.invalid(
                    com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                    "search explanation reasonCodes are required");
        }
        for (SearchExplanationReason reasonCode : reasonCodes) {
            SearchChecks.requireNonNull(reasonCode, "reasonCode");
        }
        reasonCodes = List.copyOf(new ArrayList<>(reasonCodes));
        if (message != null) {
            message = SearchChecks.requireText(message, "message");
            if (message.length() > 512) throw SearchChecks.invalid(
                    com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                    "message exceeds 512 characters");
        }
        List<String> evidenceCopy = new ArrayList<>();
        if (evidenceRefs != null) {
            for (String evidenceRef : evidenceRefs) {
                evidenceCopy.add(SearchChecks.requireOptionalRef(
                        SearchChecks.requireText(evidenceRef, "evidenceRef"), "evidenceRef"));
            }
        }
        evidenceRefs = List.copyOf(evidenceCopy);
        LinkedHashMap<String, String> attributeCopy = new LinkedHashMap<>();
        if (attributes != null) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = SearchChecks.requireNoWhitespace(entry.getKey(), "attributeKey", 128);
                String value = SearchChecks.requireText(entry.getValue(), "attributeValue");
                attributeCopy.put(key, value);
            }
        }
        attributes = java.util.Collections.unmodifiableMap(attributeCopy);
        SearchChecks.requireNonNull(privacyClass, "privacyClass");
        SearchChecks.requireInstant(createdAt, "createdAt");
        if (audience == ExplanationAudience.USER) {
            if (privacyClass == PrivacyClass.RESTRICTED) {
                throw SearchChecks.invalid(
                        com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                        "restricted explanation cannot be user-facing");
            }
            String combined = ((message == null ? "" : message) + attributes).toLowerCase(java.util.Locale.ROOT);
            for (String forbidden : List.of("stack trace", "raw_prompt", "access_token", "score_component")) {
                if (combined.contains(forbidden)) {
                    throw SearchChecks.invalid(
                            com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode.SEARCH_REQUEST_INVALID,
                            "user explanation contains internal or sensitive detail");
                }
            }
        }
    }

    public IntelligenceExplanationV1 toIntelligenceExplanation() {
        return new IntelligenceExplanationV1(
                IntelligenceContractIds.INTELLIGENCE_EXPLANATION,
                explanationId,
                runId,
                audience,
                reasonCodes.stream().map(SearchExplanationReason::name).toList(),
                message,
                evidenceRefs,
                attributes,
                privacyClass,
                createdAt);
    }
}
