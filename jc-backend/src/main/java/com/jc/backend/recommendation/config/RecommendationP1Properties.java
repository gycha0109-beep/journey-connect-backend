package com.jc.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recommendation.p1")
public class RecommendationP1Properties {
    public enum Mode {
        OFF,
        SHADOW,
        CANARY
    }

    private Mode mode = Mode.OFF;
    private int comparisonCutoff = 20;
    private int profileLookbackDays = 90;
    private int profileEventLimit = 5_000;
    private int retrievalLimit = 300;
    private int coreCandidateLimit = 100;
    private int canaryAllocationBasisPoints = 0;
    private String coreBuildId = "java-core-1.1.0-p1";
    private String releaseId = "";

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OFF : mode;
    }

    public int getComparisonCutoff() {
        return comparisonCutoff;
    }

    public void setComparisonCutoff(int comparisonCutoff) {
        this.comparisonCutoff = comparisonCutoff;
    }

    public int getProfileLookbackDays() {
        return profileLookbackDays;
    }

    public void setProfileLookbackDays(int profileLookbackDays) {
        this.profileLookbackDays = profileLookbackDays;
    }

    public int getProfileEventLimit() {
        return profileEventLimit;
    }

    public void setProfileEventLimit(int profileEventLimit) {
        this.profileEventLimit = profileEventLimit;
    }

    public int getRetrievalLimit() {
        return retrievalLimit;
    }

    public void setRetrievalLimit(int retrievalLimit) {
        this.retrievalLimit = retrievalLimit;
    }

    public int getCoreCandidateLimit() {
        return coreCandidateLimit;
    }

    public void setCoreCandidateLimit(int coreCandidateLimit) {
        this.coreCandidateLimit = coreCandidateLimit;
    }

    public int getCanaryAllocationBasisPoints() {
        return canaryAllocationBasisPoints;
    }

    public void setCanaryAllocationBasisPoints(int canaryAllocationBasisPoints) {
        this.canaryAllocationBasisPoints = canaryAllocationBasisPoints;
    }

    public String getCoreBuildId() {
        return coreBuildId;
    }

    public void setCoreBuildId(String coreBuildId) {
        this.coreBuildId = coreBuildId;
    }

    public String getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(String releaseId) {
        this.releaseId = releaseId == null ? "" : releaseId;
    }

    public void validate() {
        if (comparisonCutoff < 1 || comparisonCutoff > 100) {
            throw new IllegalStateException("app.recommendation.p1.comparison-cutoff must be 1..100");
        }
        if (profileLookbackDays < 1 || profileLookbackDays > 365) {
            throw new IllegalStateException("app.recommendation.p1.profile-lookback-days must be 1..365");
        }
        if (profileEventLimit < 1 || profileEventLimit > 10_000) {
            throw new IllegalStateException("app.recommendation.p1.profile-event-limit must be 1..10000");
        }
        if (coreCandidateLimit < 1 || coreCandidateLimit > 100) {
            throw new IllegalStateException("app.recommendation.p1.core-candidate-limit must be 1..100");
        }
        if (retrievalLimit < coreCandidateLimit || retrievalLimit > 1_000) {
            throw new IllegalStateException("app.recommendation.p1.retrieval-limit must be core limit..1000");
        }
        if (canaryAllocationBasisPoints < 0 || canaryAllocationBasisPoints > 10_000) {
            throw new IllegalStateException("app.recommendation.p1.canary-allocation-basis-points must be 0..10000");
        }
        if (coreBuildId == null || coreBuildId.isBlank() || coreBuildId.length() > 128) {
            throw new IllegalStateException("app.recommendation.p1.core-build-id must be 1..128 characters");
        }
        if (mode != Mode.OFF && !releaseId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalStateException("P1 SHADOW/CANARY requires a valid release-id");
        }
        if (mode == Mode.CANARY && canaryAllocationBasisPoints == 0) {
            throw new IllegalStateException("P1 CANARY requires a nonzero allocation");
        }
    }
}
