package com.jc.backend.intelligence.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.recommendation.application.RecommendationCanonicalPayload;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchExposureCanonicalizerTest {

    private static final String GOLDEN_FINGERPRINT =
            "14879159c3b4f671ac25de0f18af96f920c39cf99fd5e7e1ef66e21239d53293";

    private final SearchExposureCanonicalizer canonicalizer =
            new SearchExposureCanonicalizer(
                    new RecommendationCanonicalPayload(new ObjectMapper()));

    @Test
    void canonicalPayloadMatchesGoldenFixtureAndPrivacyBoundary() throws IOException {
        SearchExposureCanonicalizer.CanonicalBatch canonical =
                canonicalizer.encode(command(items()));

        assertEquals(goldenFixture(), canonical.json());
        assertEquals(GOLDEN_FINGERPRINT, canonical.fingerprint());
        assertFalse(canonical.json().contains("rawQuery"));
        assertFalse(canonical.json().contains("\"userId\""));
        assertFalse(canonical.json().contains("서울 카페"));
        assertEquals(
                List.of("search-exp-1", "search-exp-2"),
                canonical.items().stream()
                        .map(SearchExposureCanonicalizer.CanonicalItem::exposureId)
                        .toList());
    }

    @Test
    void itemOrderingDoesNotChangeCanonicalBatch() {
        List<SearchExposureCommand.Item> reversed = new ArrayList<>(items());
        Collections.reverse(reversed);

        SearchExposureCanonicalizer.CanonicalBatch first =
                canonicalizer.encode(command(items()));
        SearchExposureCanonicalizer.CanonicalBatch second =
                canonicalizer.encode(command(reversed));

        assertEquals(first.json(), second.json());
        assertEquals(first.fingerprint(), second.fingerprint());
    }

    @Test
    void changedEvidenceChangesBatchAndItemFingerprints() {
        SearchExposureCanonicalizer.CanonicalBatch baseline =
                canonicalizer.encode(command(items()));
        List<SearchExposureCommand.Item> changed = new ArrayList<>(items());
        SearchExposureCommand.Item first = changed.getFirst();
        changed.set(0, new SearchExposureCommand.Item(
                first.exposureId(),
                first.idempotencyKey(),
                first.postId(),
                first.absoluteRank(),
                first.pagePosition(),
                first.visibleRatioBasisPoints(),
                first.dwellMilliseconds() + 1,
                first.exposedAt()));

        SearchExposureCanonicalizer.CanonicalBatch modified =
                canonicalizer.encode(command(changed));

        assertNotEquals(baseline.fingerprint(), modified.fingerprint());
        assertNotEquals(
                baseline.items().getFirst().fingerprint(),
                modified.items().getFirst().fingerprint());
    }

    private SearchExposureCommand command(List<SearchExposureCommand.Item> items) {
        return new SearchExposureCommand(
                SearchExposureContract.SCHEMA_VERSION,
                "subject:01J0SEARCHSUBJECT",
                SearchExposureContract.IDENTITY_SCHEME,
                "search-jwt:session-1",
                "search:run-1",
                "b".repeat(64),
                "a".repeat(64),
                SearchRankingPolicy.POLICY_VERSION,
                "search-page:occ-1",
                SearchExposureContract.CANDIDATE_VISIBILITY_RULE_VERSION,
                "web-20260804.1",
                items);
    }

    private List<SearchExposureCommand.Item> items() {
        return List.of(
                new SearchExposureCommand.Item(
                        "search-exp-1",
                        "search-idem-1",
                        11L,
                        1,
                        1,
                        6_000,
                        1_500L,
                        Instant.parse("2026-08-04T06:00:02Z")),
                new SearchExposureCommand.Item(
                        "search-exp-2",
                        "search-idem-2",
                        12L,
                        2,
                        2,
                        7_000,
                        1_800L,
                        Instant.parse("2026-08-04T06:00:03Z")));
    }

    private String goldenFixture() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/intelligence/search/search-exposure-canonical-v1.json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
