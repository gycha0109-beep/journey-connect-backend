package com.jc.intelligence.contract.v1.search.cursor;

import com.jc.intelligence.contract.support.ContractJsonWireV1;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;

public final class SearchCursorChecksumV1 {
    private static final String DOMAIN = "journey-connect:search-cursor-checksum:v1\n";
    private SearchCursorChecksumV1() { }

    public static String compute(
            com.jc.intelligence.contract.v1.version.SchemaVersion cursorVersion,
            com.jc.intelligence.contract.v1.identity.RunRef searchRunId,
            com.jc.intelligence.contract.v1.identity.SnapshotRef resultSnapshotRef,
            String queryFingerprint,
            String filterFingerprint,
            com.jc.intelligence.contract.v1.version.PolicyVersion sortPolicyVersion,
            com.jc.intelligence.contract.v1.version.PolicyVersion rankingPolicyVersion,
            java.time.Instant referenceTime,
            int nextRank,
            SearchOrderingTupleV1 lastOrderingTuple,
            com.jc.intelligence.contract.v1.search.SearchSurface surface,
            com.jc.intelligence.contract.v1.search.SearchEntityScope entityScope,
            com.jc.intelligence.contract.v1.identity.SubjectRef subjectBindingRef,
            String sessionBindingRef,
            java.time.Instant issuedAt,
            java.time.Instant expiresAt) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("cursorVersion", cursorVersion.value());
        map.put("searchRunId", searchRunId.value());
        map.put("resultSnapshotRef", resultSnapshotRef.value());
        map.put("queryFingerprint", queryFingerprint);
        map.put("filterFingerprint", filterFingerprint);
        map.put("sortPolicyVersion", sortPolicyVersion.value());
        map.put("rankingPolicyVersion", rankingPolicyVersion.value());
        map.put("referenceTime", referenceTime.toString());
        map.put("nextRank", nextRank);
        map.put("lastOrderingTuple", lastOrderingTuple.components());
        map.put("surface", surface.wireValue());
        map.put("entityScope", entityScope.wireValue());
        if (subjectBindingRef != null) map.put("subjectBindingRef", subjectBindingRef.value());
        if (sessionBindingRef != null) map.put("sessionBindingRef", sessionBindingRef);
        map.put("issuedAt", issuedAt.toString());
        map.put("expiresAt", expiresAt.toString());
        try {
            byte[] bytes = (DOMAIN + ContractJsonWireV1.stringify(map)).getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public static boolean matches(SearchCursorV1 cursor) {
        return compute(cursor.cursorVersion(), cursor.searchRunId(), cursor.resultSnapshotRef(),
                cursor.queryFingerprint(), cursor.filterFingerprint(), cursor.sortPolicyVersion(),
                cursor.rankingPolicyVersion(), cursor.referenceTime(), cursor.nextRank(),
                cursor.lastOrderingTuple(), cursor.surface(), cursor.entityScope(),
                cursor.subjectBindingRef(), cursor.sessionBindingRef(), cursor.issuedAt(),
                cursor.expiresAt()).equals(cursor.checksum());
    }
}
