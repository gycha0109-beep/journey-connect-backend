package com.jc.backend.recommendation.application;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.RecommendationCandidateRow;
import com.jc.backend.recommendation.config.RecommendationP1Properties;
import com.jc.backend.recommendation.p1.RecommendationP1CandidateMapper;
import com.jc.backend.recommendation.p1.RecommendationP1CandidateSource;
import com.jc.backend.recommendation.p1.RecommendationP1EvidenceStore;
import com.jc.backend.recommendation.p1.RecommendationP1ProfileSource;
import com.jc.backend.recommendation.p2.RecommendationP2AssignmentService;
import com.jc.backend.recommendation.persistence.RecommendationRunStore;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.RankedCandidateWrite;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.RunWrite;
import com.jc.backend.recommendation.persistence.RecommendationSnapshotStore;
import com.jc.backend.recommendation.persistence.RecommendationSnapshotStore.SnapshotWrite;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunMode;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunStatus;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.SnapshotKind;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.Surface;
import com.jc.recommendation.model.context.ContextSurface;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import com.jc.recommendation.model.ranking.RankingEmptyReason;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.p1.evaluation.P1RankingComparison;
import com.jc.recommendation.p1.evaluation.P1RankingComparisonEngine;
import com.jc.recommendation.p1.policy.P1ExperimentAssignment;
import com.jc.recommendation.p1.policy.P1PolicySelection;
import com.jc.recommendation.p1.policy.P1PolicySelector;
import com.jc.recommendation.p1.policy.P1PolicySelectorInput;
import com.jc.recommendation.p1.policy.P1SessionContext;
import com.jc.recommendation.p1.profile.BehaviorProfileBuilder;
import com.jc.recommendation.p1.profile.BehaviorProfilePolicies;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import com.jc.recommendation.p1.profile.BuildBehaviorProfileInput;
import com.jc.recommendation.p1.ranking.P1CandidateInput;
import com.jc.recommendation.p1.ranking.P1RankedCandidate;
import com.jc.recommendation.p1.ranking.P1RankingEngine;
import com.jc.recommendation.p1.ranking.P1RankingInput;
import com.jc.recommendation.p1.ranking.P1RankingResult;
import com.jc.recommendation.p2.P2EvaluationContracts.Variant;
import com.jc.recommendation.p2.P2ExperimentAssigner.Assignment;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecommendationP1RuntimeService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationP1RuntimeService.class);
    private static final String SNAPSHOT_SCHEMA_VERSION = "p1.0.0";
    private static final String CANONICALIZATION_VERSION = "canonical-json-v1";

    private final RecommendationP1Properties properties;
    private final RecommendationP1ModeDecider modeDecider;
    private final RecommendationP1ProfileSource profileSource;
    private final RecommendationP1CandidateSource candidateSource;
    private final RecommendationP1CandidateMapper candidateMapper;
    private final RecommendationP1EvidenceStore evidenceStore;
    private final RecommendationSnapshotStore snapshotStore;
    private final RecommendationRunStore runStore;
    private final RecommendationCanonicalPayload canonicalPayload;
    private final RecommendationP2AssignmentService p2AssignmentService;
    private final BehaviorProfileBuilder profileBuilder = new BehaviorProfileBuilder();
    private final P1PolicySelector policySelector = new P1PolicySelector();
    private final P1RankingEngine rankingEngine = new P1RankingEngine();
    private final P1RankingComparisonEngine comparisonEngine = new P1RankingComparisonEngine();

    public RecommendationP1RuntimeService(
            RecommendationP1Properties properties,
            RecommendationP1ModeDecider modeDecider,
            RecommendationP1ProfileSource profileSource,
            RecommendationP1CandidateSource candidateSource,
            RecommendationP1CandidateMapper candidateMapper,
            RecommendationP1EvidenceStore evidenceStore,
            RecommendationSnapshotStore snapshotStore,
            RecommendationRunStore runStore,
            RecommendationCanonicalPayload canonicalPayload,
            RecommendationP2AssignmentService p2AssignmentService) {
        this.properties = properties;
        this.modeDecider = modeDecider;
        this.profileSource = profileSource;
        this.candidateSource = candidateSource;
        this.candidateMapper = candidateMapper;
        this.evidenceStore = evidenceStore;
        this.snapshotStore = snapshotStore;
        this.runStore = runStore;
        this.canonicalPayload = canonicalPayload;
        this.p2AssignmentService = p2AssignmentService;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public TreatmentOutcome observeShadow(String baselineRunId, long userId, String sessionId) {
        if (!modeDecider.shouldRunShadow()) {
            return TreatmentOutcome.skipped(baselineRunId);
        }
        return executeTreatment(baselineRunId, userId, sessionId, RunMode.SHADOW);
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public String selectCanaryRun(String baselineRunId, long userId, String sessionId) {
        if (!p2AssignmentService.isEnabled()) {
            if (!modeDecider.shouldServeCanary(userId)) {
                return baselineRunId;
            }
            return executeTreatment(baselineRunId, userId, sessionId, RunMode.CANARY).treatmentRunId();
        }
        RecommendationP1EvidenceStore.BaselineRun baseline = evidenceStore.requireBaselineRun(baselineRunId);
        Instant now = Instant.now();
        Instant exposedAt = now.isBefore(baseline.referenceTime()) ? baseline.referenceTime() : now;
        Assignment assignment = p2AssignmentService.assign(userId, exposedAt);
        if (assignment.variant() == Variant.BASELINE) {
            p2AssignmentService.recordExposure(assignment, baselineRunId, userId, sessionId, exposedAt);
            return baselineRunId;
        }
        String treatmentRunId = executeTreatment(baselineRunId, userId, sessionId, RunMode.CANARY).treatmentRunId();
        p2AssignmentService.recordExposure(assignment, treatmentRunId, userId, sessionId, exposedAt);
        return treatmentRunId;
    }

    TreatmentOutcome executeTreatment(
            String baselineRunId,
            long userId,
            String sessionId,
            RunMode runMode) {
        long started = System.nanoTime();
        RecommendationP1EvidenceStore.BaselineRun baseline = evidenceStore.requireBaselineRun(baselineRunId);
        requireBinding(baseline, userId, sessionId, runMode);
        Instant referenceTime = baseline.referenceTime();

        BehaviorProfileSnapshot profile = profileBuilder.build(new BuildBehaviorProfileInput(
                Long.toString(userId),
                referenceTime,
                profileSource.findExplicitPreferences(userId),
                profileSource.findBehaviorEvents(
                        userId,
                        referenceTime.minusSeconds((long) properties.getProfileLookbackDays() * 86_400L),
                        referenceTime,
                        properties.getProfileEventLimit()),
                BehaviorProfilePolicies.V1));
        int priorAssignments = evidenceStore.countPriorP1Assignments(userId, referenceTime);
        P1PolicySelection selection = policySelector.select(new P1PolicySelectorInput(
                profile.segment(),
                ContextSurface.HOME_FEED,
                new P1SessionContext(priorAssignments > 0, priorAssignments),
                P1ExperimentAssignment.TREATMENT));

        List<RecommendationCandidateRow> rows = candidateSource.findEligible(
                userId,
                referenceTime,
                properties.getRetrievalLimit(),
                properties.getCoreCandidateLimit());
        List<P1CandidateInput> candidates = candidateMapper.mapAll(rows);
        String treatmentRunId = identifier("p1-run");
        String requestId = identifier("p1-request");
        String rankingSnapshotId = baselineRunId + ":p1-ranking-input-v1";
        P1RankingResult ranking = rankingEngine.rank(new P1RankingInput(
                rankingSnapshotId,
                Long.toString(userId),
                baseline.contextId(),
                referenceTime,
                profile,
                selection,
                candidates));

        RecommendationCanonicalPayload.Encoded rankingInputPayload = canonicalPayload.encode(Map.of(
                "referenceTime", referenceTime,
                "profile", profile,
                "policySelection", selection,
                "candidates", candidates));
        RecommendationCanonicalPayload.Encoded metadataPayload = canonicalPayload.encode(candidates.stream()
                .map(P1CandidateInput::diversityMetadata)
                .toList());
        RecommendationCanonicalPayload.Encoded exposurePayload = canonicalPayload.encode(candidates.stream()
                .map(candidate -> Map.of(
                        "entityId", candidate.entityId(),
                        "recentExposureCount", candidate.recentExposureCount()))
                .toList());
        RecommendationCanonicalPayload.Encoded resultPayload = canonicalPayload.encode(ranking);

        String storedRankingSnapshotId = storeSnapshot(
                rankingSnapshotId,
                SnapshotKind.RANKING_INPUT_V1,
                rankingInputPayload);
        String metadataSnapshotId = storeSnapshot(
                identifier("p1-diversity"),
                SnapshotKind.DIVERSITY_METADATA_V1,
                metadataPayload);
        String exposureSnapshotId = storeSnapshot(
                identifier("p1-exposure"),
                SnapshotKind.EXPLORATION_METADATA_V1,
                exposurePayload);
        String resultSnapshotId = storeSnapshot(
                identifier("p1-ranking-result"),
                SnapshotKind.RANKING_RESULT_V1,
                resultPayload);

        List<RankedCandidateWrite> writes = ranking.candidates().stream()
                .map(candidate -> rankedWrite(candidate, selection.policyBundle().scorePolicy().policyVersion()))
                .toList();
        boolean empty = writes.isEmpty();
        long durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        runStore.store(new RunWrite(
                treatmentRunId,
                requestId,
                runMode,
                RunStatus.SUCCEEDED,
                userId,
                sessionId,
                baseline.contextId(),
                Surface.HOME,
                referenceTime,
                storedRankingSnapshotId,
                metadataSnapshotId,
                exposureSnapshotId,
                resultSnapshotId,
                selection.policyBundle().policyBundleVersion(),
                "p1-integration-v1",
                baseline.rankingPolicyVersion(),
                selection.policyBundle().scorePolicy().policyVersion(),
                componentVersions(selection),
                selection.policyBundle().diversityPolicy().policyVersion(),
                selection.policyBundle().explorationPolicyVersion(),
                "p1-deterministic-no-random-v1",
                empty ? RankingResultStatus.EMPTY : RankingResultStatus.RANKED,
                empty ? RankingEmptyReason.NO_SCORED_CANDIDATES : null,
                properties.getCoreCandidateLimit(),
                writes.size(),
                writes.size(),
                writes.size(),
                ranking.fingerprint(),
                properties.getCoreBuildId(),
                durationMs,
                null,
                writes,
                List.of()));

        String profileSnapshotId = "p1-profile:" + profile.fingerprint().substring(0, 48);
        evidenceStore.storeProfile(profileSnapshotId, userId, profile);
        evidenceStore.storeAssignment(
                identifier("p1-assignment"),
                baselineRunId,
                treatmentRunId,
                userId,
                sessionId,
                profileSnapshotId,
                properties.getReleaseId(),
                selection);

        List<String> baselineEntityIds = evidenceStore.findBaselineEntityIds(
                baselineRunId,
                properties.getComparisonCutoff());
        P1RankingComparison comparison = comparisonEngine.compare(
                baseline.rankingPolicyVersion(),
                baselineEntityIds,
                ranking.policyBundleVersion(),
                ranking.candidates(),
                properties.getComparisonCutoff());
        evidenceStore.storeComparison(
                identifier("p1-comparison"),
                baselineRunId,
                treatmentRunId,
                baseline.resultFingerprint(),
                ranking.fingerprint(),
                comparison);
        verifyPersistence(treatmentRunId, ranking, comparison, profileSnapshotId);
        return new TreatmentOutcome(
                Status.SUCCEEDED,
                baselineRunId,
                treatmentRunId,
                profile.segment().wireValue(),
                ranking.policyBundleVersion(),
                ranking.rankedCount(),
                comparison.fingerprint());
    }

    private void verifyPersistence(
            String treatmentRunId,
            P1RankingResult ranking,
            P1RankingComparison comparison,
            String profileSnapshotId) {
        RecommendationP1EvidenceStore.PersistedTreatment persisted = evidenceStore.requireTreatment(treatmentRunId);
        if (!persisted.resultFingerprint().equals(ranking.fingerprint())
                || persisted.rankedCandidateCount() != ranking.rankedCount()
                || !persisted.policyBundleVersion().equals(ranking.policyBundleVersion())
                || !persisted.profileSnapshotId().equals(profileSnapshotId)
                || !persisted.comparisonFingerprint().equals(comparison.fingerprint())) {
            throw new IllegalStateException("P1 persistence verification failed");
        }
        List<RecommendationRunStore.PersistedRankedCandidate> candidates = runStore.findRanked(treatmentRunId);
        if (candidates.size() != ranking.candidates().size()) {
            throw new IllegalStateException("P1 persisted candidate count mismatch");
        }
        for (int index = 0; index < candidates.size(); index++) {
            RecommendationRunStore.PersistedRankedCandidate stored = candidates.get(index);
            P1RankedCandidate expected = ranking.candidates().get(index);
            if (stored.absoluteRank() != expected.absoluteRank()
                    || stored.sourceEntityId() != parsePostId(expected.entityId())
                    || stored.score() == null
                    || Double.doubleToRawLongBits(stored.score())
                            != Double.doubleToRawLongBits(expected.score())) {
                throw new IllegalStateException("P1 persisted ranking mismatch at rank " + (index + 1));
            }
        }
    }

    private String storeSnapshot(
            String snapshotId,
            SnapshotKind kind,
            RecommendationCanonicalPayload.Encoded payload) {
        return snapshotStore.store(new SnapshotWrite(
                snapshotId,
                kind,
                SNAPSHOT_SCHEMA_VERSION,
                CANONICALIZATION_VERSION,
                payload.bytes(),
                payload.json())).snapshotId();
    }

    private static RankedCandidateWrite rankedWrite(P1RankedCandidate candidate, String scorePolicyVersion) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("baseScore", candidate.baseScore());
        provenance.put("contextScore", candidate.contextScore());
        provenance.put("interestScore", candidate.interestScore());
        provenance.put("freshnessScore", candidate.freshnessScore());
        provenance.put("rawPopularityScore", candidate.rawPopularityScore());
        provenance.put("adjustedPopularityScore", candidate.adjustedPopularityScore());
        provenance.put("lowExposureBoost", candidate.lowExposureBoost());
        provenance.put("recentExposureCount", candidate.recentExposureCount());
        provenance.put("appliedRelaxations", candidate.appliedRelaxations());
        return new RankedCandidateWrite(
                candidate.absoluteRank(),
                parsePostId(candidate.entityId()),
                ExplorationCandidateOrigin.PERSONALIZED,
                candidate.score(),
                candidate.baseRank(),
                candidate.absoluteRank(),
                null,
                null,
                null,
                null,
                null,
                scorePolicyVersion,
                provenance);
    }

    private static Map<String, String> componentVersions(P1PolicySelection selection) {
        String score = selection.policyBundle().scorePolicy().policyVersion();
        return Map.of(
                "context", score + ":context",
                "interest", score + ":interest",
                "freshness", score + ":freshness",
                "popularity", score + ":popularity");
    }

    private static void requireBinding(
            RecommendationP1EvidenceStore.BaselineRun baseline,
            long userId,
            String sessionId,
            RunMode runMode) {
        if (baseline.userId() != userId || !baseline.sessionId().equals(sessionId)) {
            throw new IllegalStateException("P1 baseline run ownership binding is invalid");
        }
        if (!baseline.runMode().equals(runMode.value())) {
            throw new IllegalStateException("P1 treatment mode does not match baseline mode");
        }
    }

    private static long parsePostId(String entityId) {
        try {
            long value = Long.parseLong(entityId);
            if (value <= 0) {
                throw new NumberFormatException("nonpositive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("P1 supports numeric post IDs only", exception);
        }
    }

    private static String identifier(String prefix) {
        return prefix + ":" + UUID.randomUUID().toString().replace("-", "");
    }

    public enum Status {
        SKIPPED,
        SUCCEEDED
    }

    public record TreatmentOutcome(
            Status status,
            String baselineRunId,
            String treatmentRunId,
            String segment,
            String policyBundleVersion,
            int rankedCount,
            String comparisonFingerprint) {

        static TreatmentOutcome skipped(String baselineRunId) {
            return new TreatmentOutcome(Status.SKIPPED, baselineRunId, null, null, null, 0, null);
        }
    }
}
