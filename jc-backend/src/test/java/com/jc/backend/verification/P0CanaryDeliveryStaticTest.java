package com.jc.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p0-verification")
class P0CanaryDeliveryStaticTest {

    private static final Path MAIN = RepositoryLayout.resolve("jc-backend/src/main/java");

    @Test
    void canaryDeliveryIsSignedReadinessGatedExposureTrackedAndLiveRemainsBlocked() throws Exception {
        String properties = Files.readString(MAIN.resolve(
                "com/jc/backend/recommendation/config/RecommendationProperties.java"));
        String decider = Files.readString(MAIN.resolve(
                "com/jc/backend/recommendation/application/RecommendationModeDecider.java"));
        String canary = Files.readString(MAIN.resolve(
                "com/jc/backend/recommendation/application/RecommendationCanaryService.java"));
        String cursor = Files.readString(MAIN.resolve(
                "com/jc/backend/recommendation/application/RecommendationCursorCodec.java"));
        String controller = Files.readString(MAIN.resolve(
                "com/jc/backend/post/PostController.java"));

        assertThat(properties).contains(
                "canaryAllocationBasisPoints",
                "canaryCursorSecret",
                "canaryReleaseId",
                "mode == Mode.CANARY");
        assertThat(decider).contains("HmacSHA256", "shouldServeHomeCanary", "getCanaryReleaseId", "Mode.LIVE");
        assertThat(cursor).contains("HmacSHA256", "expectedUserId", "expectedSessionId", "getCanaryReleaseId");
        assertThat(canary).contains(
                "readinessService.evaluate()",
                "runCanary",
                "replayService.audit",
                "exposureStore.store",
                "summariesByOrderedIds");
        assertThat(controller).contains("recommendationFeedService.feed");
    }
}
