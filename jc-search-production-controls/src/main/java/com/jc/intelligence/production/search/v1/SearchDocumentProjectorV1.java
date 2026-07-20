package com.jc.intelligence.production.search.v1;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

public final class SearchDocumentProjectorV1 {
    public SearchDocumentProjectionV1 project(SearchDocumentSourceV1 source, Instant projectedAt) {
        if (source==null || projectedAt==null) throw new IllegalArgumentException("source/projectedAt required");
        var titleTerms=SearchProjectionTextNormalizer.terms(source.title(),128);
        var bodyTerms=SearchProjectionTextNormalizer.terms(source.body(),1024);
        String material=String.join("\n",
                "search-document-projection-v1", Long.toString(source.sourcePostId()), Long.toString(source.sourceVersion()),
                Long.toString(source.regionId()), source.regionReference(), source.placeReference()==null?"-":source.placeReference(),
                String.join("|",titleTerms), String.join("|",bodyTerms), source.sourceUpdatedAt().toString());
        return new SearchDocumentProjectionV1("post:"+source.sourcePostId(),source.sourcePostId(),source.sourceVersion(),
                SearchProductionContractIds.PROJECTION_SCHEMA,SearchProductionContractIds.ELIGIBILITY_POLICY,
                source.regionId(),source.regionReference(),source.placeReference(),titleTerms,bodyTerms,source.sourceUpdatedAt(),projectedAt,sha256(material));
    }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
}
