package com.jc.backend.recommendation;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorEventType;
import com.jc.backend.recommendation.persistence.RecommendationBehaviorStore.BehaviorWrite;
import com.jc.backend.recommendation.persistence.RecommendationExposureStore;
import com.jc.backend.recommendation.persistence.RecommendationExposureStore.ExposureCandidateWrite;
import com.jc.backend.recommendation.persistence.RecommendationExposureStore.ExposureWrite;
import com.jc.backend.recommendation.persistence.RecommendationReplayStore;
import com.jc.backend.recommendation.persistence.RecommendationRunStore;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.RankedCandidateWrite;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.RunWrite;
import com.jc.backend.recommendation.persistence.RecommendationSnapshotStore;
import com.jc.backend.recommendation.persistence.RecommendationSnapshotStore.SnapshotWrite;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunMode;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunStatus;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.SnapshotKind;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.Surface;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@CanonicalPostgresTest
@Transactional
class RecommendationPersistenceIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private RecommendationSnapshotStore snapshots;
    @Autowired private RecommendationRunStore runs;
    @Autowired private RecommendationExposureStore exposures;
    @Autowired private RecommendationBehaviorStore behaviors;
    @Autowired private RecommendationReplayStore replayStore;

    @Test
    void canonicalSnapshotsRunExposureAndBehaviorRoundTrip() {
        UserAccount user = users.save(new UserAccount(
                "persistence-user@example.com", "hash", "persistence-user"));
        Region seoul = region(regions, "KR-SEOUL");
        JourneyPost post = posts.saveAndFlush(publishedPost(
                places, user, seoul, "persisted recommendation", "persisted content"));

        storeSnapshot("snapshot-ranking", SnapshotKind.RANKING_INPUT_V1, "ranking");
        storeSnapshot("snapshot-metadata", SnapshotKind.DIVERSITY_METADATA_V1, "metadata");
        storeSnapshot("snapshot-exploration", SnapshotKind.EXPLORATION_METADATA_V1, "exploration");
        SnapshotWrite resultSnapshot = snapshot(
                "snapshot-result", SnapshotKind.RANKING_RESULT_V1, "result");
        snapshots.store(resultSnapshot);
        assertThat(snapshots.store(resultSnapshot).contentHash())
                .isEqualTo(snapshots.find("snapshot-result").orElseThrow().contentHash());
        SnapshotWrite duplicateContent = snapshot(
                "snapshot-result-duplicate", SnapshotKind.RANKING_RESULT_V1, "result");
        assertThat(snapshots.store(duplicateContent).snapshotId()).isEqualTo("snapshot-result");
        assertThat(snapshots.find("snapshot-result-duplicate")).isEmpty();

        Instant referenceTime = Instant.now();
        runs.store(new RunWrite(
                "run-persistence-1",
                "request-persistence-1",
                RunMode.SHADOW,
                RunStatus.SUCCEEDED,
                user.getId(),
                "session-persistence-1",
                "context-persistence-1",
                Surface.HOME,
                referenceTime,
                "snapshot-ranking",
                "snapshot-metadata",
                "snapshot-exploration",
                "snapshot-result",
                "ranking-v3",
                "ranking-v2",
                "ranking-v1",
                "score-v1",
                Map.of("interest", "interest-v1", "freshness", "freshness-v1"),
                "diversity-v1",
                "exploration-v1",
                "seed-persistence-1",
                RankingResultStatus.RANKED,
                null,
                20,
                20,
                1,
                1,
                "a".repeat(64),
                "java-core-1.0.0",
                12,
                null,
                List.of(new RankedCandidateWrite(
                        1,
                        post.getId(),
                        ExplorationCandidateOrigin.PERSONALIZED,
                        0.75,
                        1,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "score-v1",
                        Map.of("selection", "personalized"))),
                List.of()));

        RecommendationRunStore.PersistedRankedCandidate persisted =
                runs.findRanked("run-persistence-1").getFirst();
        assertThat(persisted.sourceEntityId()).isEqualTo(post.getId());
        assertThat(persisted.score()).isEqualTo(0.75);
        assertThat(persisted.scoreIsNegativeZero()).isFalse();
        RecommendationReplayStore.ReplayBundle replay = replayStore
                .load("run-persistence-1")
                .orElseThrow();
        assertThat(new String(replay.rankingInput().canonicalPayload(), StandardCharsets.UTF_8))
                .isEqualTo("ranking");
        assertThat(new String(replay.rankingResult().canonicalPayload(), StandardCharsets.UTF_8))
                .isEqualTo("result");
        assertThat(replay.rankedCandidates()).hasSize(1);
        assertThat(replay.terminalCandidates()).isEmpty();

        ExposureWrite exposure = new ExposureWrite(
                "exposure-persistence-1",
                "exposure-idempotency-1",
                "exposure-event-v1",
                bytes("exposure"),
                "run-persistence-1",
                user.getId(),
                "session-persistence-1",
                "context-persistence-1",
                Surface.HOME,
                referenceTime.plusSeconds(1),
                "replay-persistence-1",
                "b".repeat(64),
                "ranking-cursor-v3",
                1,
                1,
                false,
                List.of(new ExposureCandidateWrite(
                        1,
                        post.getId(),
                        ExplorationCandidateOrigin.PERSONALIZED,
                        0.75,
                        Map.of("page", 1))));
        exposures.store(exposure);
        exposures.store(exposure);
        ExposureWrite conflictingExposure = new ExposureWrite(
                exposure.eventId(),
                exposure.idempotencyKey(),
                exposure.schemaVersion(),
                exposure.canonicalPayload(),
                exposure.runId(),
                exposure.userId(),
                exposure.sessionId(),
                exposure.contextId(),
                exposure.surface(),
                exposure.servedAt(),
                exposure.replayKey(),
                exposure.pageFingerprint(),
                exposure.cursorVersion(),
                exposure.pageStartRank(),
                exposure.pageEndRank(),
                exposure.hasNextPage(),
                List.of(new ExposureCandidateWrite(
                        1,
                        post.getId(),
                        ExplorationCandidateOrigin.PERSONALIZED,
                        0.75,
                        Map.of("page", 2))));
        assertThatThrownBy(() -> exposures.store(conflictingExposure))
                .isInstanceOf(IllegalStateException.class);

        BehaviorWrite behavior = new BehaviorWrite(
                "behavior-persistence-1",
                "behavior-idempotency-1",
                "behavior-event-v1",
                bytes("behavior"),
                user.getId(),
                "session-persistence-1",
                "run-persistence-1",
                BehaviorEventType.CLICK,
                "post",
                post.getId(),
                referenceTime.plusSeconds(2),
                Map.of("surface", "home"));
        behaviors.store(behavior);
        behaviors.store(behavior);
        BehaviorWrite conflictingBehavior = new BehaviorWrite(
                behavior.eventId(),
                behavior.idempotencyKey(),
                behavior.schemaVersion(),
                behavior.canonicalPayload(),
                behavior.userId(),
                behavior.sessionId(),
                behavior.runId(),
                behavior.eventType(),
                behavior.entityType(),
                behavior.sourceEntityId(),
                behavior.occurredAt(),
                Map.of("surface", "detail"));
        assertThatThrownBy(() -> behaviors.store(conflictingBehavior))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void persistsExplorationCandidateInsideFinalRankedPartition() {
        UserAccount user = users.save(new UserAccount(
                "exploration-user@example.com", "hash", "exploration-user"));
        Region seoul = region(regions, "KR-SEOUL");
        JourneyPost personalizedPost = posts.saveAndFlush(publishedPost(
                places, user, seoul, "personalized candidate", "personalized content"));
        JourneyPost explorationPost = posts.saveAndFlush(publishedPost(
                places, user, seoul, "exploration candidate", "exploration content"));

        storeSnapshot("exploration-ranking", SnapshotKind.RANKING_INPUT_V1, "ranking");
        storeSnapshot("exploration-metadata", SnapshotKind.DIVERSITY_METADATA_V1, "metadata");
        storeSnapshot("exploration-input", SnapshotKind.EXPLORATION_METADATA_V1, "exploration");
        storeSnapshot("exploration-result", SnapshotKind.RANKING_RESULT_V1, "result");

        runs.store(new RunWrite(
                "run-exploration-partition",
                "request-exploration-partition",
                RunMode.SHADOW,
                RunStatus.SUCCEEDED,
                user.getId(),
                "session-exploration-partition",
                "context-exploration-partition",
                Surface.HOME,
                Instant.now(),
                "exploration-ranking",
                "exploration-metadata",
                "exploration-input",
                "exploration-result",
                "ranking-v3",
                "ranking-v2",
                "ranking-v1",
                "score-v1",
                Map.of("interest", "interest-v1", "freshness", "freshness-v1"),
                "diversity-v1",
                "exploration-v1",
                "seed-exploration-partition",
                RankingResultStatus.RANKED,
                null,
                20,
                2,
                2,
                1,
                "d".repeat(64),
                "java-core-1.0.0",
                2,
                null,
                List.of(
                        new RankedCandidateWrite(
                                1,
                                personalizedPost.getId(),
                                ExplorationCandidateOrigin.PERSONALIZED,
                                0.8,
                                1,
                                1,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "score-v1",
                                Map.of("selection", "personalized")),
                        new RankedCandidateWrite(
                                2,
                                explorationPost.getId(),
                                ExplorationCandidateOrigin.EXPLORATION,
                                null,
                                null,
                                null,
                                0.45,
                                0,
                                123L,
                                1,
                                2,
                                "score-v1",
                                Map.of("selection", "exploration"))),
                List.of()));

        assertThat(runs.findRanked("run-exploration-partition"))
                .extracting(RecommendationRunStore.PersistedRankedCandidate::origin)
                .containsExactly("personalized", "exploration");
    }

    @Test
    void rejectsDuplicateRunCandidateSourceIdsBeforeDatabaseWrite() {
        RankedCandidateWrite first = new RankedCandidateWrite(
                1,
                100L,
                ExplorationCandidateOrigin.PERSONALIZED,
                0.8,
                1,
                1,
                null,
                null,
                null,
                null,
                null,
                "score-v1",
                Map.of());
        RankedCandidateWrite duplicate = new RankedCandidateWrite(
                2,
                100L,
                ExplorationCandidateOrigin.PERSONALIZED,
                0.7,
                2,
                2,
                null,
                null,
                null,
                null,
                null,
                "score-v1",
                Map.of());
        RunWrite invalid = new RunWrite(
                "run-duplicate",
                "request-duplicate",
                RunMode.SHADOW,
                RunStatus.SUCCEEDED,
                1L,
                "session-duplicate",
                "context-duplicate",
                Surface.HOME,
                Instant.parse("2026-07-18T00:00:00Z"),
                "ranking",
                "metadata",
                "exploration",
                "result",
                "ranking-v3",
                "integration-v2",
                "base-v1",
                "score-v1",
                Map.of("interest", "interest-v1"),
                "diversity-v1",
                "exploration-v1",
                "seed",
                RankingResultStatus.RANKED,
                null,
                20,
                20,
                2,
                2,
                "c".repeat(64),
                "java-core-1.0.0",
                1,
                null,
                List.of(first, duplicate),
                List.of());

        assertThatThrownBy(() -> runs.store(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate source IDs");
    }

    private void storeSnapshot(String id, SnapshotKind kind, String payload) {
        snapshots.store(snapshot(id, kind, payload));
    }

    private SnapshotWrite snapshot(String id, SnapshotKind kind, String payload) {
        return new SnapshotWrite(
                id,
                kind,
                "1.0.0",
                "canonical-json-v1",
                bytes(payload),
                "{\"value\":\"" + payload + "\"}");
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
