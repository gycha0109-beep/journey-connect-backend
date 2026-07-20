package com.jc.backend.recommendation.application;

import com.jc.backend.recommendation.config.RecommendationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Executes SHADOW work fail-open so the legacy feed response never changes. */
@Service
public class RecommendationShadowService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationShadowService.class);

    private final RecommendationProperties properties;
    private final RecommendationModeDecider modeDecider;
    private final RecommendationOrchestrationService orchestrationService;
    private final RecommendationReplayService replayService;
    private RecommendationP1RuntimeService p1RuntimeService;

    public RecommendationShadowService(
            RecommendationProperties properties,
            RecommendationModeDecider modeDecider,
            RecommendationOrchestrationService orchestrationService,
            RecommendationReplayService replayService) {
        this.properties = properties;
        this.modeDecider = modeDecider;
        this.orchestrationService = orchestrationService;
        this.replayService = replayService;
    }

    @Autowired(required = false)
    public void setP1RuntimeService(RecommendationP1RuntimeService p1RuntimeService) {
        this.p1RuntimeService = p1RuntimeService;
    }

    public ShadowOutcome observeHomeFeed(Long userId, String tokenId, boolean firstPage) {
        if (!modeDecider.shouldRunHomeShadow(userId, firstPage)) {
            return ShadowOutcome.skipped();
        }
        String sessionId = RecommendationSessionIds.fromJwt(userId.longValue(), tokenId);
        try {
            RecommendationOrchestrationService.RunResult result = orchestrationService.runShadow(
                    new RecommendationOrchestrationService.ShadowRunRequest(
                            userId.longValue(), sessionId));
            if (properties.isReplayAuditEnabled()) {
                RecommendationReplayService.ReplayAuditResult replay = replayService.audit(result.runId());
                if (!replay.exactMatch()) {
                    throw new IllegalStateException(
                            "Persisted recommendation replay is not exact: " + replay.status());
                }
            }
            if (p1RuntimeService != null) {
                p1RuntimeService.observeShadow(result.runId(), userId.longValue(), sessionId);
            }
            return ShadowOutcome.succeeded(result.runId());
        } catch (RuntimeException exception) {
            log.warn(
                    "Recommendation SHADOW run failed open for user {}: {}",
                    userId,
                    exception.getClass().getSimpleName(),
                    exception);
            return ShadowOutcome.failed();
        }
    }

    public record ShadowOutcome(Status status, String runId) {
        public enum Status {
            SKIPPED,
            SUCCEEDED,
            FAILED
        }

        static ShadowOutcome skipped() {
            return new ShadowOutcome(Status.SKIPPED, null);
        }

        static ShadowOutcome succeeded(String runId) {
            return new ShadowOutcome(Status.SUCCEEDED, runId);
        }

        static ShadowOutcome failed() {
            return new ShadowOutcome(Status.FAILED, null);
        }
    }
}
