package com.jc.intelligence.contract.v1.explanation;

import com.jc.intelligence.contract.support.ImmutableCollections;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.snapshot.PrivacyClass;
import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record IntelligenceExplanationV1(
        ContractId contractVersion,
        String explanationId,
        RunRef runId,
        ExplanationAudience audience,
        List<String> reasonCodes,
        String message,
        List<String> evidenceRefs,
        Map<String, String> attributes,
        PrivacyClass privacyClass,
        Instant createdAt) {

    public IntelligenceExplanationV1 {
        if (!IntelligenceContractIds.INTELLIGENCE_EXPLANATION.equals(contractVersion)) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_CONTRACT_ID_INVALID,
                    "IntelligenceExplanationV1 requires intelligence-explanation-v1");
        }
        explanationId = ContractChecks.requireSimpleRef(explanationId, "explanationId");
        java.util.Objects.requireNonNull(runId, "runId");
        java.util.Objects.requireNonNull(audience, "audience");
        reasonCodes = ImmutableCollections.orderedCopy(reasonCodes, "reasonCodes");
        for (String reasonCode : reasonCodes) {
            ContractChecks.requireReasonCode(reasonCode, "reasonCode");
        }
        if (message != null) {
            message = ContractChecks.requireText(
                    message,
                    "message",
                    IntelligenceValidationErrorCode.INTELLIGENCE_EXPLANATION_INVALID);
        }
        evidenceRefs = ImmutableCollections.orderedCopy(evidenceRefs, "evidenceRefs");
        for (String evidenceRef : evidenceRefs) {
            ContractChecks.requireSimpleRef(evidenceRef, "evidenceRef");
        }
        attributes = ImmutableCollections.insertionOrderedCopy(attributes, "attributes");
        java.util.Objects.requireNonNull(privacyClass, "privacyClass");
        ContractChecks.requireInstant(createdAt, "createdAt");

        if (audience == ExplanationAudience.USER) {
            String combined = ((message == null ? "" : message) + attributes).toLowerCase(java.util.Locale.ROOT);
            if (combined.contains("stack trace")
                    || combined.contains("raw_prompt")
                    || combined.contains("access_token")
                    || combined.contains("score_component")) {
                throw ContractChecks.invalid(
                        IntelligenceValidationErrorCode.INTELLIGENCE_EXPLANATION_INVALID,
                        "user explanation contains internal or sensitive detail");
            }
        }
        if (message == null && reasonCodes.isEmpty() && evidenceRefs.isEmpty()) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_EXPLANATION_INVALID,
                    "empty explanations must be omitted rather than fabricated");
        }
    }
}
