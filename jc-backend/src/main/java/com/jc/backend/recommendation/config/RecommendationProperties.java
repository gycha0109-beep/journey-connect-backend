package com.jc.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime controls for recommendation execution. OFF is the safe default. */
@Component
@ConfigurationProperties(prefix = "app.recommendation")
public class RecommendationProperties {

    public enum Mode {
        OFF,
        SHADOW,
        CANARY,
        LIVE
    }

    private Mode mode = Mode.OFF;
    private int candidateLimit = 100;
    private String coreBuildId = "java-core-1.0.0";
    private boolean replayAuditEnabled = true;
    private String replayEvaluatorVersion = "p0-persistence-replay-v1";
    private int readinessMinimumShadowRuns = 20;
    private int readinessLookbackRuns = 100;
    private long readinessMaxP95DurationMs = 1000L;
    private int canaryAllocationBasisPoints = 0;
    private String canaryCursorSecret = "";
    private String canaryReleaseId = "";

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OFF : mode;
    }

    public int getCandidateLimit() {
        return candidateLimit;
    }

    public void setCandidateLimit(int candidateLimit) {
        this.candidateLimit = candidateLimit;
    }

    public String getCoreBuildId() {
        return coreBuildId;
    }

    public void setCoreBuildId(String coreBuildId) {
        this.coreBuildId = coreBuildId;
    }

    public boolean isReplayAuditEnabled() {
        return replayAuditEnabled;
    }

    public void setReplayAuditEnabled(boolean replayAuditEnabled) {
        this.replayAuditEnabled = replayAuditEnabled;
    }

    public String getReplayEvaluatorVersion() {
        return replayEvaluatorVersion;
    }

    public void setReplayEvaluatorVersion(String replayEvaluatorVersion) {
        this.replayEvaluatorVersion = replayEvaluatorVersion;
    }

    public int getReadinessMinimumShadowRuns() {
        return readinessMinimumShadowRuns;
    }

    public void setReadinessMinimumShadowRuns(int readinessMinimumShadowRuns) {
        this.readinessMinimumShadowRuns = readinessMinimumShadowRuns;
    }

    public int getReadinessLookbackRuns() {
        return readinessLookbackRuns;
    }

    public void setReadinessLookbackRuns(int readinessLookbackRuns) {
        this.readinessLookbackRuns = readinessLookbackRuns;
    }

    public long getReadinessMaxP95DurationMs() {
        return readinessMaxP95DurationMs;
    }

    public void setReadinessMaxP95DurationMs(long readinessMaxP95DurationMs) {
        this.readinessMaxP95DurationMs = readinessMaxP95DurationMs;
    }

    public int getCanaryAllocationBasisPoints() {
        return canaryAllocationBasisPoints;
    }

    public void setCanaryAllocationBasisPoints(int canaryAllocationBasisPoints) {
        this.canaryAllocationBasisPoints = canaryAllocationBasisPoints;
    }

    public String getCanaryCursorSecret() {
        return canaryCursorSecret;
    }

    public void setCanaryCursorSecret(String canaryCursorSecret) {
        this.canaryCursorSecret = canaryCursorSecret == null ? "" : canaryCursorSecret;
    }

    public String getCanaryReleaseId() {
        return canaryReleaseId;
    }

    public void setCanaryReleaseId(String canaryReleaseId) {
        this.canaryReleaseId = canaryReleaseId == null ? "" : canaryReleaseId;
    }

    public void validate() {
        if (candidateLimit < 1 || candidateLimit > 100) {
            throw new IllegalStateException("app.recommendation.candidate-limit must be between 1 and 100");
        }
        if (coreBuildId == null || coreBuildId.isBlank() || coreBuildId.length() > 128) {
            throw new IllegalStateException("app.recommendation.core-build-id must be 1..128 characters");
        }
        if (replayEvaluatorVersion == null || replayEvaluatorVersion.isBlank()
                || replayEvaluatorVersion.length() > 64) {
            throw new IllegalStateException(
                    "app.recommendation.replay-evaluator-version must be 1..64 characters");
        }
        if (readinessMinimumShadowRuns < 1) {
            throw new IllegalStateException(
                    "app.recommendation.readiness-minimum-shadow-runs must be positive");
        }
        if (readinessLookbackRuns < readinessMinimumShadowRuns || readinessLookbackRuns > 10000) {
            throw new IllegalStateException(
                    "app.recommendation.readiness-lookback-runs must cover the minimum and be <= 10000");
        }
        if (readinessMaxP95DurationMs < 1) {
            throw new IllegalStateException(
                    "app.recommendation.readiness-max-p95-duration-ms must be positive");
        }
        if (canaryAllocationBasisPoints < 0 || canaryAllocationBasisPoints > 10000) {
            throw new IllegalStateException(
                    "app.recommendation.canary-allocation-basis-points must be between 0 and 10000");
        }
        if (mode == Mode.CANARY) {
            if (canaryAllocationBasisPoints < 1) {
                throw new IllegalStateException(
                        "CANARY requires a positive allocation basis-point value");
            }
            if (canaryCursorSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException(
                        "CANARY requires app.recommendation.canary-cursor-secret of at least 32 bytes");
            }
            if (canaryReleaseId.isBlank() || canaryReleaseId.length() > 128
                    || !canaryReleaseId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
                throw new IllegalStateException(
                        "CANARY requires a valid app.recommendation.canary-release-id");
            }
        }
    }
}
