package com.jc.backend.intelligence.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.common.DomainException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchContextCodecTest {

    private static final String SECRET =
            "search-context-test-secret-with-at-least-thirty-two-bytes";
    private final SearchContextCodec codec = new SearchContextCodec(SECRET, 900);
    private final Instant issuedAt = Instant.parse("2026-08-04T06:00:00Z");
    private final String queryFingerprint = "a".repeat(64);
    private final String snapshotFingerprint = "b".repeat(64);

    @Test
    void snapshotRoundTripPreservesStableBinding() {
        String token = codec.encodeSnapshot(
                "search:run-1",
                41L,
                queryFingerprint,
                issuedAt.minusSeconds(1),
                20,
                snapshotFingerprint,
                SearchRankingPolicy.POLICY_VERSION,
                issuedAt);

        var decoded = codec.decodeSnapshot(
                token,
                41L,
                queryFingerprint,
                20,
                issuedAt.plusSeconds(30));

        assertThat(decoded.runId()).isEqualTo("search:run-1");
        assertThat(decoded.referenceTime()).isEqualTo(issuedAt.minusSeconds(1));
        assertThat(decoded.snapshotFingerprint()).isEqualTo(snapshotFingerprint);
    }

    @Test
    void snapshotRejectsTamperingUserQuerySizeAndExpiry() {
        String token = snapshot();

        assertSnapshotExpired(token.substring(0, token.length() - 1) + "0", 41L, queryFingerprint, 20,
                issuedAt.plusSeconds(1));
        assertSnapshotExpired(token, 42L, queryFingerprint, 20, issuedAt.plusSeconds(1));
        assertSnapshotExpired(token, 41L, "c".repeat(64), 20, issuedAt.plusSeconds(1));
        assertSnapshotExpired(token, 41L, queryFingerprint, 10, issuedAt.plusSeconds(1));
        assertSnapshotExpired(token, 41L, queryFingerprint, 20, issuedAt.plusSeconds(901));
    }

    @Test
    void resultContextBindsOnlyDeliveredPostAndRankPairs() {
        var snapshot = codec.decodeSnapshot(
                snapshot(),
                41L,
                queryFingerprint,
                20,
                issuedAt.plusSeconds(1));
        String token = codec.encodeResultContext(
                snapshot,
                List.of(
                        new SearchContextCodec.ResultBinding(11L, 1),
                        new SearchContextCodec.ResultBinding(12L, 2)),
                issuedAt.plusSeconds(1));

        var result = codec.decodeResultContext(token, 41L, issuedAt.plusSeconds(2));

        assertThat(result.contains(11L, 1)).isTrue();
        assertThat(result.contains(11L, 2)).isFalse();
        assertThat(result.contains(99L, 1)).isFalse();
        assertThat(result.queryFingerprint()).isEqualTo(queryFingerprint);
    }

    @Test
    void resultContextRejectsWrongUserAndTampering() {
        var snapshot = codec.decodeSnapshot(
                snapshot(),
                41L,
                queryFingerprint,
                20,
                issuedAt.plusSeconds(1));
        String token = codec.encodeResultContext(
                snapshot,
                List.of(new SearchContextCodec.ResultBinding(11L, 1)),
                issuedAt.plusSeconds(1));

        assertThatThrownBy(() -> codec.decodeResultContext(
                token, 42L, issuedAt.plusSeconds(2)))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getCode())
                .isEqualTo("SEARCH_RESULT_CONTEXT_INVALID");
        assertThatThrownBy(() -> codec.decodeResultContext(
                token.substring(0, token.length() - 1) + "0",
                41L,
                issuedAt.plusSeconds(2)))
                .isInstanceOf(DomainException.class);
    }

    private String snapshot() {
        return codec.encodeSnapshot(
                "search:run-1",
                41L,
                queryFingerprint,
                issuedAt,
                20,
                snapshotFingerprint,
                SearchRankingPolicy.POLICY_VERSION,
                issuedAt);
    }

    private void assertSnapshotExpired(
            String token,
            long userId,
            String query,
            int size,
            Instant now) {
        assertThatThrownBy(() -> codec.decodeSnapshot(token, userId, query, size, now))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getCode())
                .isEqualTo("SEARCH_SNAPSHOT_EXPIRED");
    }
}
