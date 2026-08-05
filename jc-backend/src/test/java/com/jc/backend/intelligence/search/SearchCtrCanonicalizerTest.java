package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.intelligence.search.SearchCtrModels.Attribution;
import com.jc.backend.intelligence.search.SearchCtrModels.EvaluationResult;
import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchCtrCanonicalizerTest {

    private static final String GOLDEN_FINGERPRINT =
            "0204c56356a47ec12e7af1f1a1e81ba4eb0104fc8598656ee12aab48ea2375fc";

    private final SearchCtrCanonicalizer canonicalizer = new SearchCtrCanonicalizer(
            new RecommendationCanonicalPayload(new ObjectMapper()));

    @Test
    void canonicalProjectionMatchesGoldenAndExcludesRawIdentity() throws IOException {
        SearchCtrCanonicalizer.CanonicalProjection canonical = canonicalizer.encode(result(attributions()));

        assertEquals(goldenFixture(), canonical.json());
        assertEquals(GOLDEN_FINGERPRINT, canonical.fingerprint());
        assertFalse(canonical.json().contains("userId"));
        assertFalse(canonical.json().contains("numericUser"));
        assertFalse(canonical.json().contains("subjectRef"));
        assertFalse(canonical.json().contains("sessionId"));
        assertFalse(canonical.json().contains("rawQuery"));
    }

    @Test
    void attributionInputOrderingDoesNotChangeFingerprint() {
        List<Attribution> reversed = new ArrayList<>(attributions());
        Collections.reverse(reversed);

        SearchCtrCanonicalizer.CanonicalProjection first = canonicalizer.encode(result(attributions()));
        SearchCtrCanonicalizer.CanonicalProjection second = canonicalizer.encode(result(reversed));

        assertEquals(first.json(), second.json());
        assertEquals(first.fingerprint(), second.fingerprint());
    }

    @Test
    void changedMetricCountsChangeFingerprint() {
        EvaluationResult baseline = result(attributions());
        EvaluationResult changed = new EvaluationResult(
                baseline.metricId(), baseline.metricVersion(), baseline.windowStart(), baseline.windowEnd(),
                baseline.status(), 3, baseline.attributedExposureCount(), 3_333,
                baseline.computedAt(), baseline.sourceMaxReceivedAt(), baseline.attributedExposureIds(),
                baseline.attributions(), baseline.unattributedClickEventIds());

        assertNotEquals(
                canonicalizer.encode(baseline).fingerprint(),
                canonicalizer.encode(changed).fingerprint());
    }

    private EvaluationResult result(List<Attribution> attributions) {
        return new EvaluationResult(
                SearchCtrContract.METRIC_ID,
                SearchCtrContract.METRIC_VERSION,
                Instant.parse("2026-08-05T00:00:00Z"),
                Instant.parse("2026-08-05T01:00:00Z"),
                SearchCtrContract.PROVISIONAL_STATUS,
                2,
                1,
                5_000,
                Instant.parse("2026-08-05T02:00:00Z"),
                Instant.parse("2026-08-05T00:11:01Z"),
                List.of("exp-newer"),
                attributions,
                List.of());
    }

    private List<Attribution> attributions() {
        return List.of(new Attribution("click-1", "exp-newer", 60_000L));
    }

    private String goldenFixture() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/intelligence/search/search-ctr-projection-canonical-v1.json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
