package com.jc.backend.recommendation.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.common.CursorPageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.backend.post.PostService;
import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.persistence.RecommendationExposureStore;
import com.jc.backend.recommendation.persistence.RecommendationExposureStore.ExposureCandidateWrite;
import com.jc.backend.recommendation.persistence.RecommendationExposureStore.ExposureWrite;
import com.jc.backend.recommendation.persistence.RecommendationHashing;
import com.jc.backend.recommendation.persistence.RecommendationRunStore;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.DeliveryContext;
import com.jc.backend.recommendation.persistence.RecommendationRunStore.PersistedRankedCandidate;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.Surface;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Serves a gated immutable CANARY run, persists exact exposure pages, and never reranks pages. */
@Service
public class RecommendationCanaryService {

    private static final String EXPOSURE_SCHEMA_VERSION = "1.0.0";

    private final RecommendationProperties properties;
    private final RecommendationModeDecider modeDecider;
    private final RecommendationCanaryReadinessService readinessService;
    private final RecommendationOrchestrationService orchestrationService;
    private final RecommendationReplayService replayService;
    private final RecommendationRunStore runStore;
    private final RecommendationExposureStore exposureStore;
    private final RecommendationCanonicalPayload canonicalPayload;
    private final RecommendationCursorCodec cursorCodec;
    private final PostService postService;
    private final ObjectMapper objectMapper;
    private RecommendationP1RuntimeService p1RuntimeService;

    public RecommendationCanaryService(
            RecommendationProperties properties,
            RecommendationModeDecider modeDecider,
            RecommendationCanaryReadinessService readinessService,
            RecommendationOrchestrationService orchestrationService,
            RecommendationReplayService replayService,
            RecommendationRunStore runStore,
            RecommendationExposureStore exposureStore,
            RecommendationCanonicalPayload canonicalPayload,
            RecommendationCursorCodec cursorCodec,
            PostService postService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.modeDecider = modeDecider;
        this.readinessService = readinessService;
        this.orchestrationService = orchestrationService;
        this.replayService = replayService;
        this.runStore = runStore;
        this.exposureStore = exposureStore;
        this.canonicalPayload = canonicalPayload;
        this.cursorCodec = cursorCodec;
        this.postService = postService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    public void setP1RuntimeService(RecommendationP1RuntimeService p1RuntimeService) {
        this.p1RuntimeService = p1RuntimeService;
    }

    public Optional<CursorPageResponse<PostDtos.Summary>> firstPage(
            long userId, String tokenId, int size) {
        if (!modeDecider.shouldServeHomeCanary(userId)) {
            return Optional.empty();
        }
        RecommendationCanaryReadinessService.ReadinessResult readiness = readinessService.evaluate();
        if (!readiness.ready()) {
            return Optional.empty();
        }
        String sessionId = RecommendationSessionIds.fromJwt(userId, tokenId);
        RecommendationOrchestrationService.RunResult run = orchestrationService.runCanary(
                new RecommendationOrchestrationService.CanaryRunRequest(userId, sessionId));
        RecommendationReplayService.ReplayAuditResult replay = replayService.audit(run.runId());
        if (!replay.exactMatch()) {
            throw new IllegalStateException("CANARY exact replay gate failed: " + replay.status());
        }
        String deliveryRunId = run.runId();
        if (p1RuntimeService != null) {
            try {
                deliveryRunId = p1RuntimeService.selectCanaryRun(run.runId(), userId, sessionId);
            } catch (RuntimeException exception) {
                // The P1 transaction has already rolled back; serve the exact-replay P0 baseline.
                deliveryRunId = run.runId();
            }
        }
        return Optional.of(page(deliveryRunId, 0, userId, sessionId, size));
    }

    public CursorPageResponse<PostDtos.Summary> nextPage(
            String cursor, long userId, String tokenId, int size) {
        String sessionId = RecommendationSessionIds.fromJwt(userId, tokenId);
        RecommendationCursorCodec.Cursor decoded = cursorCodec.decode(cursor, userId, sessionId);
        return page(decoded.runId(), decoded.offset(), userId, sessionId, size);
    }

    public boolean isRecommendationCursor(String cursor) {
        return cursorCodec.isRecommendationCursor(cursor);
    }

    private CursorPageResponse<PostDtos.Summary> page(
            String runId, int offset, long userId, String sessionId, int requestedSize) {
        DeliveryContext context = runStore.requireDeliveryContext(runId);
        requireCanaryBinding(context, userId, sessionId);
        List<PersistedRankedCandidate> ranked = runStore.findRanked(runId);
        if (ranked.size() != context.rankedCandidateCount() || offset > ranked.size()) {
            throw new IllegalStateException("Persisted CANARY run partition is invalid");
        }
        int size = Math.min(Math.max(requestedSize, 1), 100);
        int end = Math.min(ranked.size(), offset + size);
        List<PersistedRankedCandidate> candidates = ranked.subList(offset, end);
        List<Long> postIds = candidates.stream()
                .map(PersistedRankedCandidate::sourceEntityId)
                .toList();
        List<PostDtos.Summary> items = postService.summariesByOrderedIds(postIds);
        if (items.size() != postIds.size()
                || !items.stream().map(PostDtos.Summary::id).toList().equals(postIds)) {
            throw new IllegalStateException("CANARY page contains content that is no longer public");
        }
        boolean hasNext = end < ranked.size();
        String nextCursor = hasNext
                ? cursorCodec.encode(runId, end, userId, sessionId)
                : null;
        persistExposure(context, candidates, offset, hasNext, nextCursor);
        return CursorPageResponse.recommendation(items, nextCursor, hasNext, runId);
    }

    private void persistExposure(
            DeliveryContext context,
            List<PersistedRankedCandidate> candidates,
            int offset,
            boolean hasNext,
            String nextCursor) {
        int pageStart = candidates.isEmpty() ? 0 : offset + 1;
        int pageEnd = candidates.isEmpty() ? 0 : offset + candidates.size();
        String replayKey = context.runId() + ":" + pageStart + ":" + pageEnd;
        String material = context.runId() + "|" + offset + "|" + candidates.size()
                + "|" + context.userId() + "|" + context.sessionId();
        String digest = RecommendationHashing.sha256(material.getBytes(StandardCharsets.UTF_8));
        String eventId = "exp:" + digest.substring(0, 48);
        String idempotencyKey = "home-canary:" + digest;
        Instant existingServedAt = exposureStore.findServedAtByIdempotencyKey(idempotencyKey);
        Instant servedAt = existingServedAt == null
                ? Instant.now().truncatedTo(ChronoUnit.MICROS)
                : existingServedAt;

        List<Map<String, Object>> pagePayload = candidates.stream().map(candidate -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("absoluteRank", candidate.absoluteRank());
            row.put("postId", candidate.sourceEntityId());
            row.put("origin", candidate.origin());
            row.put("score", candidate.score());
            row.put("provenance", provenance(candidate.provenanceJson()));
            return row;
        }).toList();
        RecommendationCanonicalPayload.Encoded page = canonicalPayload.encode(pagePayload);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", context.runId());
        payload.put("releaseId", properties.getCanaryReleaseId());
        payload.put("servedAt", servedAt.toString());
        payload.put("pageStartRank", candidates.isEmpty() ? null : pageStart);
        payload.put("pageEndRank", candidates.isEmpty() ? null : pageEnd);
        payload.put("hasNext", hasNext);
        payload.put("nextCursor", nextCursor);
        payload.put("candidates", pagePayload);
        RecommendationCanonicalPayload.Encoded encoded = canonicalPayload.encode(payload);

        List<ExposureCandidateWrite> writes = candidates.stream()
                .map(candidate -> new ExposureCandidateWrite(
                        candidate.absoluteRank(),
                        candidate.sourceEntityId(),
                        origin(candidate.origin()),
                        candidate.score(),
                        provenance(candidate.provenanceJson())))
                .toList();
        exposureStore.store(new ExposureWrite(
                eventId,
                idempotencyKey,
                EXPOSURE_SCHEMA_VERSION,
                encoded.bytes(),
                context.runId(),
                context.userId(),
                context.sessionId(),
                context.contextId(),
                Surface.HOME,
                servedAt,
                replayKey,
                RecommendationHashing.sha256(page.bytes()),
                RecommendationCursorCodec.VERSION,
                candidates.isEmpty() ? null : pageStart,
                candidates.isEmpty() ? null : pageEnd,
                hasNext,
                writes));
    }

    private void requireCanaryBinding(DeliveryContext context, long userId, String sessionId) {
        if (!context.runMode().equals("canary")
                || !context.runStatus().equals("succeeded")
                || !context.surface().equals("home")
                || context.userId() != userId
                || !context.sessionId().equals(sessionId)) {
            throw new IllegalStateException("CANARY run binding is invalid");
        }
    }

    private Map<String, Object> provenance(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Persisted CANARY provenance is invalid", exception);
        }
    }

    private static ExplorationCandidateOrigin origin(String value) {
        return switch (value) {
            case "personalized" -> ExplorationCandidateOrigin.PERSONALIZED;
            case "exploration" -> ExplorationCandidateOrigin.EXPLORATION;
            default -> throw new IllegalStateException("Unknown recommendation origin: " + value);
        };
    }
}
