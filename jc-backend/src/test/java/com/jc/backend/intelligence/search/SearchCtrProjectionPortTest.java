package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.intelligence.search.SearchCtrModels.EvaluationResult;
import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchCtrProjectionPortTest {

    @Test
    void disabledPortDoesNotPretendToPersistProjection() {
        EvaluationResult result = new EvaluationResult(
                SearchCtrContract.METRIC_ID,
                SearchCtrContract.METRIC_VERSION,
                Instant.parse("2026-08-05T00:00:00Z"),
                Instant.parse("2026-08-05T01:00:00Z"),
                SearchCtrContract.PROVISIONAL_STATUS,
                0,
                0,
                null,
                Instant.parse("2026-08-05T02:00:00Z"),
                null,
                List.of(),
                List.of(),
                List.of());
        SearchCtrCanonicalizer.CanonicalProjection canonical = new SearchCtrCanonicalizer(
                new RecommendationCanonicalPayload(new ObjectMapper())).encode(result);

        SearchCtrProjectionPort.WriteResult write =
                SearchCtrProjectionPort.disabledPendingApproval().write(canonical);

        assertEquals(SearchCtrProjectionPort.WriteStatus.DISABLED_PENDING_APPROVAL, write.status());
        assertEquals(canonical.fingerprint(), write.projectionFingerprint());
    }
}
