package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SearchDocumentProjectionV1(
        String documentId, long sourcePostId, long sourceVersion,
        SchemaVersion projectionSchemaVersion, PolicyVersion eligibilityPolicyVersion,
        long regionId, String regionReference, String placeReference,
        List<String> normalizedTitleTerms, List<String> normalizedBodyTerms,
        Instant sourceUpdatedAt, Instant projectedAt, String deterministicContentHash) {
    public SearchDocumentProjectionV1 {
        if (documentId == null || !documentId.matches("post:[1-9][0-9]{0,18}")) throw new IllegalArgumentException("documentId invalid");
        if (sourcePostId<1 || sourceVersion<1 || regionId<1) throw new IllegalArgumentException("numeric fields must be positive");
        if (!documentId.equals("post:"+sourcePostId)) throw new IllegalArgumentException("documentId must bind sourcePostId");
        Objects.requireNonNull(projectionSchemaVersion,"projectionSchemaVersion"); Objects.requireNonNull(eligibilityPolicyVersion,"eligibilityPolicyVersion");
        if (!projectionSchemaVersion.equals(SearchProductionContractIds.PROJECTION_SCHEMA)) throw new IllegalArgumentException("unsupported projection schema");
        if (!eligibilityPolicyVersion.equals(SearchProductionContractIds.ELIGIBILITY_POLICY)) throw new IllegalArgumentException("unsupported eligibility policy");
        if (regionReference==null || !regionReference.matches("[a-z0-9][a-z0-9-]{0,79}")) throw new IllegalArgumentException("regionReference invalid");
        if (placeReference != null && !placeReference.matches("place:[1-9][0-9]{0,18}")) throw new IllegalArgumentException("placeReference invalid");
        normalizedTitleTerms=List.copyOf(Objects.requireNonNull(normalizedTitleTerms,"normalizedTitleTerms")); normalizedBodyTerms=List.copyOf(Objects.requireNonNull(normalizedBodyTerms,"normalizedBodyTerms"));
        if (normalizedTitleTerms.size()>128 || normalizedBodyTerms.size()>1024) throw new IllegalArgumentException("term boundary exceeded");
        for (String term : normalizedTitleTerms) validateTerm(term); for (String term : normalizedBodyTerms) validateTerm(term);
        Objects.requireNonNull(sourceUpdatedAt,"sourceUpdatedAt"); Objects.requireNonNull(projectedAt,"projectedAt");
        if (projectedAt.isBefore(sourceUpdatedAt)) throw new IllegalArgumentException("projectedAt cannot precede sourceUpdatedAt");
        if (deterministicContentHash==null || !deterministicContentHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("content hash invalid");
    }
    private static void validateTerm(String term) { if (term==null || term.isBlank() || !term.equals(term.trim()) || term.length()>128 || term.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("projection term invalid"); }
}
