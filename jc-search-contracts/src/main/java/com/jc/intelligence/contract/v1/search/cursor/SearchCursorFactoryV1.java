package com.jc.intelligence.contract.v1.search.cursor;

import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;

public final class SearchCursorFactoryV1 {
    private SearchCursorFactoryV1() { }
    public static SearchCursorV1 create(
            SchemaVersion cursorVersion,
            RunRef searchRunId,
            SnapshotRef resultSnapshotRef,
            String queryFingerprint,
            String filterFingerprint,
            PolicyVersion sortPolicyVersion,
            PolicyVersion rankingPolicyVersion,
            Instant referenceTime,
            int nextRank,
            SearchOrderingTupleV1 lastOrderingTuple,
            SearchSurface surface,
            SearchEntityScope entityScope,
            SubjectRef subjectBindingRef,
            String sessionBindingRef,
            Instant issuedAt,
            Instant expiresAt) {
        String checksum = SearchCursorChecksumV1.compute(cursorVersion, searchRunId, resultSnapshotRef,
                queryFingerprint, filterFingerprint, sortPolicyVersion, rankingPolicyVersion,
                referenceTime, nextRank, lastOrderingTuple, surface, entityScope,
                subjectBindingRef, sessionBindingRef, issuedAt, expiresAt);
        return new SearchCursorV1(cursorVersion, searchRunId, resultSnapshotRef, queryFingerprint,
                filterFingerprint, sortPolicyVersion, rankingPolicyVersion, referenceTime, nextRank,
                lastOrderingTuple, surface, entityScope, subjectBindingRef, sessionBindingRef,
                issuedAt, expiresAt, checksum);
    }
}
