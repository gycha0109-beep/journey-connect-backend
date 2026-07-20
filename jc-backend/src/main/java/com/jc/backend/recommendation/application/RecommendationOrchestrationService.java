package com.jc.backend.recommendation.application;

import com.jc.backend.database.DatabasePropagation;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.RecommendationCandidateSource;
import com.jc.backend.recommendation.RecommendationCoreInputMapper;
import com.jc.backend.recommendation.RecommendationCoreCandidate;
import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.persistence.RecommendationHashing;
import com.jc.backend.recommendation.persistence.RecommendationRunStore;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.RankedCandidateWrite;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.RunWrite;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.TerminalCandidateWrite;
import com.jc.backend.recommendation.persistence.RecommendationSnapshotStore;
import com.jc.backend.recommendation.persistence.RecommendationSnapshotStore.SnapshotWrite;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunMode;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunStatus;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.SnapshotKind;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.Surface;
import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CollectedRankingV3Result;
import com.jc.recommendation.model.ranking.TerminalCandidateAudit;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Executes one immutable recommendation run and persists all replay material atomically. */
@Service
public class RecommendationOrchestrationService {

    private static final String SNAPSHOT_SCHEMA_VERSION = "1.0.0";
    private static final String CANONICALIZATION_VERSION = "canonical-json-v1";

    private final RecommendationProperties properties;
    private final RecommendationCandidateSource candidateSource;
    private final RecommendationCoreInputMapper inputMapper;
    private final RecommendationCorePipeline corePipeline;
    private final RecommendationCanonicalPayload canonicalPayload;
    private final RecommendationSnapshotStore snapshotStore;
    private final RecommendationRunStore runStore;

    public RecommendationOrchestrationService(
            RecommendationProperties properties,
            RecommendationCandidateSource candidateSource,
            RecommendationCoreInputMapper inputMapper,
            RecommendationCorePipeline corePipeline,
            RecommendationCanonicalPayload canonicalPayload,
            RecommendationSnapshotStore snapshotStore,
            RecommendationRunStore runStore) {
        this.properties = properties;
        this.candidateSource = candidateSource;
        this.inputMapper = inputMapper;
        this.corePipeline = corePipeline;
        this.canonicalPayload = canonicalPayload;
        this.snapshotStore = snapshotStore;
        this.runStore = runStore;
    }

    @DatabaseTransactional(
            role = DatabaseRole.RECOMMENDATION,
            propagation = DatabasePropagation.REQUIRES_NEW)
    public RunResult runShadow(ShadowRunRequest request) {
        Objects.requireNonNull(request, "request");
        return run(new RunRequest(request.userId(), request.sessionId()), RunMode.SHADOW);
    }

    @DatabaseTransactional(
            role = DatabaseRole.RECOMMENDATION,
            propagation = DatabasePropagation.REQUIRES_NEW)
    public RunResult runCanary(CanaryRunRequest request) {
        Objects.requireNonNull(request, "request");
        return run(new RunRequest(request.userId(), request.sessionId()), RunMode.CANARY);
    }

    private RunResult run(RunRequest request, RunMode runMode) {
        long started = System.nanoTime();
        Instant referenceTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String requestId = identifier("request");
        String runId = identifier("run");
        String contextId = runId + ":home";
        String rankingSnapshotId = runId + ":ranking-input";
        String metadataSnapshotId = runId + ":diversity-metadata";
        String explorationSnapshotId = runId + ":exploration-metadata";
        String resultSnapshotId = runId + ":ranking-result";
        String explorationSeed = runId + ":seed";

        var candidates = inputMapper.mapAll(candidateSource.findEligible(
                request.userId(), properties.getCandidateLimit()));
        var pipeline = executePipeline(
                request,
                contextId,
                referenceTime,
                rankingSnapshotId,
                metadataSnapshotId,
                explorationSnapshotId,
                explorationSeed,
                candidates);

        RecommendationCanonicalPayload.Encoded metadataPayload = canonicalPayload.encode(
                pipeline.rankingInput().candidateMetadata());
        RecommendationCanonicalPayload.Encoded explorationPayload = canonicalPayload.encode(
                pipeline.rankingInput().explorationMetadata());
        String requestedMetadataSnapshotId = metadataSnapshotId;
        String requestedExplorationSnapshotId = explorationSnapshotId;
        metadataSnapshotId = storeSnapshot(
                metadataSnapshotId, SnapshotKind.DIVERSITY_METADATA_V1, metadataPayload);
        explorationSnapshotId = storeSnapshot(
                explorationSnapshotId, SnapshotKind.EXPLORATION_METADATA_V1, explorationPayload);

        if (!metadataSnapshotId.equals(requestedMetadataSnapshotId)
                || !explorationSnapshotId.equals(requestedExplorationSnapshotId)) {
            pipeline = executePipeline(
                    request,
                    contextId,
                    referenceTime,
                    rankingSnapshotId,
                    metadataSnapshotId,
                    explorationSnapshotId,
                    explorationSeed,
                    candidates);
        }

        CollectedRankingV3Result collected = pipeline.collected();
        RecommendationCanonicalPayload.Encoded rankingPayload = canonicalPayload.encode(Map.of(
                "referenceTime", referenceTime.toString(),
                "input", pipeline.rankingInput()));
        RecommendationCanonicalPayload.Encoded resultPayload = canonicalPayload.encode(collected);

        rankingSnapshotId = storeSnapshot(
                rankingSnapshotId, SnapshotKind.RANKING_INPUT_V1, rankingPayload);
        resultSnapshotId = storeSnapshot(
                resultSnapshotId, SnapshotKind.RANKING_RESULT_V1, resultPayload);

        var first = collected.firstPage();
        List<RankedCandidateWrite> ranked = collected.finalCandidates().stream()
                .map(this::rankedWrite)
                .toList();
        List<TerminalCandidateWrite> terminal = collected.terminalCandidates().stream()
                .map(this::terminalWrite)
                .toList();
        long durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        runStore.store(new RunWrite(
                runId,
                requestId,
                runMode,
                RunStatus.SUCCEEDED,
                request.userId(),
                request.sessionId(),
                contextId,
                Surface.HOME,
                referenceTime,
                rankingSnapshotId,
                metadataSnapshotId,
                explorationSnapshotId,
                resultSnapshotId,
                first.policyVersion(),
                first.baseIntegrationPolicyVersion(),
                first.baseRankingPolicyVersion(),
                first.scorePolicyVersion(),
                componentVersions(first.componentPolicyVersions()),
                first.diversityPolicyVersion(),
                first.explorationPolicyVersion(),
                first.explorationSeed(),
                first.status(),
                first.emptyReason(),
                first.requestedLimit(),
                first.effectiveLimit(),
                first.inputCount(),
                first.scoredCandidateCount(),
                RecommendationHashing.sha256(resultPayload.bytes()),
                properties.getCoreBuildId(),
                durationMs,
                null,
                ranked,
                terminal));

        return new RunResult(
                runId,
                requestId,
                first.inputCount(),
                ranked.size(),
                terminal.size(),
                durationMs);
    }


    private RecommendationCorePipeline.PipelineResult executePipeline(
            RunRequest request,
            String contextId,
            Instant referenceTime,
            String rankingSnapshotId,
            String metadataSnapshotId,
            String explorationSnapshotId,
            String explorationSeed,
            List<RecommendationCoreCandidate> candidates) {
        return corePipeline.execute(new RecommendationCorePipeline.PipelineRequest(
                request.userId(),
                contextId,
                referenceTime,
                rankingSnapshotId,
                metadataSnapshotId,
                explorationSnapshotId,
                explorationSeed,
                candidates));
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

    private RankedCandidateWrite rankedWrite(ExplorationFinalCandidate candidate) {
        if (candidate instanceof PersonalizedExplorationCandidate personalized) {
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("compositionMode", personalized.compositionMode());
            provenance.put("selectionReason", personalized.selectionReason());
            provenance.put("appliedRelaxations", personalized.appliedRelaxations());
            provenance.put("violatedDimensionsAtSelection", personalized.violatedDimensionsAtSelection());
            provenance.put("displacement", personalized.displacement());
            provenance.put("promotionDistance", personalized.promotionDistance());
            provenance.put("demotionDistance", personalized.demotionDistance());
            return new RankedCandidateWrite(
                    personalized.absoluteRank(),
                    parsePostId(personalized.entityId()),
                    personalized.origin(),
                    personalized.score(),
                    personalized.baseAbsoluteRank(),
                    personalized.diversifiedAbsoluteRank(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    personalized.scorePolicyVersion(),
                    provenance);
        }
        if (candidate instanceof InsertedExplorationCandidate inserted) {
            Map<String, Object> provenance = new LinkedHashMap<>();
            provenance.put("sourceStatus", inserted.sourceStatus());
            provenance.put("sourceNotApplicableReason", inserted.sourceNotApplicableReason());
            provenance.put("qualityEvidence", inserted.qualityEvidence());
            provenance.put("availableWeightTotal", inserted.availableWeightTotal());
            provenance.put("freshnessRawScore", inserted.freshnessRawScore());
            provenance.put("popularityRawScore", inserted.popularityRawScore());
            provenance.put("explorationPolicyVersion", inserted.explorationPolicyVersion());
            return new RankedCandidateWrite(
                    inserted.absoluteRank(),
                    parsePostId(inserted.entityId()),
                    inserted.origin(),
                    null,
                    null,
                    null,
                    inserted.explorationQualityScore(),
                    inserted.recentExposureCount(),
                    inserted.seededTieBreakKey(),
                    inserted.explorationPoolRank(),
                    inserted.targetInsertionRank(),
                    inserted.scorePolicyVersion(),
                    provenance);
        }
        throw new IllegalStateException(
                "Unknown final recommendation candidate " + candidate.getClass().getName());
    }

    private TerminalCandidateWrite terminalWrite(TerminalCandidateAudit terminal) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("entityType", terminal.entityType());
        audit.put("status", terminal.status());
        audit.put("notApplicableReason", terminal.notApplicableReason());
        audit.put("hardExclusionReason", terminal.hardExclusionReason());
        return new TerminalCandidateWrite(
                parsePostId(terminal.entityId()),
                terminal.status(),
                terminal.notApplicableReason(),
                terminal.hardExclusionReason(),
                terminal.scorePolicyVersion(),
                audit);
    }

    private static Map<String, String> componentVersions(
            com.jc.recommendation.model.score.ScoreComponentPolicyVersions versions) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("context", versions.contextMatch());
        result.put("interest", versions.interestMatch());
        result.put("freshness", versions.freshness());
        result.put("popularity", versions.popularity());
        return Map.copyOf(result);
    }

    private static long parsePostId(String entityId) {
        try {
            long value = Long.parseLong(entityId);
            if (value <= 0) {
                throw new NumberFormatException("nonpositive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "P0 recommendation supports numeric post IDs only: " + entityId,
                    exception);
        }
    }

    private static String identifier(String prefix) {
        return prefix + ":" + UUID.randomUUID().toString().replace("-", "");
    }

    public record ShadowRunRequest(long userId, String sessionId) {
        public ShadowRunRequest {
            validateRequest(userId, sessionId);
        }
    }

    public record CanaryRunRequest(long userId, String sessionId) {
        public CanaryRunRequest {
            validateRequest(userId, sessionId);
        }
    }

    private record RunRequest(long userId, String sessionId) {}

    private static void validateRequest(long userId, String sessionId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("sessionId format is invalid");
        }
    }

    public record RunResult(
            String runId,
            String requestId,
            int inputCount,
            int rankedCount,
            int terminalCount,
            long durationMs) {
    }
}
