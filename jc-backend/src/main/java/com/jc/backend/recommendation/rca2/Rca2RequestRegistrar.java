package com.jc.backend.recommendation.rca2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.CursorPageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class Rca2RequestRegistrar {
    static final String ATTRIBUTE = Rca2RequestRegistrar.class.getName() + ".requests";
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Rca2IdentityPolicy identityPolicy;

    public Rca2RequestRegistrar(ObjectMapper objectMapper, Clock clock, Rca2IdentityPolicy identityPolicy) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.identityPolicy = identityPolicy;
    }

    public void registerFeed(CursorPageResponse<?> response, Long userId, String tokenId, long primaryLatencyMillis) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return;
        byte[] bytes;
        try { bytes = objectMapper.writeValueAsBytes(ApiResponse.ok(response)); }
        catch (JsonProcessingException exception) { return; }
        String digest = sha256(bytes);
        String cursorDigest = sha256(String.valueOf(response.nextCursor()).getBytes(StandardCharsets.UTF_8));
        String requestHash = sha256((tokenId == null ? "no-token" : tokenId).getBytes(StandardCharsets.UTF_8));
        String identityRef = identityPolicy.resolveRequestIdentity(userId);
        Instant captured = clock.instant();
        String checkpointRef = "rca2-response-checkpoint-v1";
        String lineage = sha256(("current-p1-p2|" + digest).getBytes(StandardCharsets.UTF_8));
        for (var lane : Rca2RuntimeContracts.Lane.values()) {
            String source = lane == Rca2RuntimeContracts.Lane.P1
                    ? "RecommendationP1ProfileSource" : "RecommendationP2ObservationSource";
            String result = lane == Rca2RuntimeContracts.Lane.P1
                    ? "recommendation_p1_profile_snapshot" : "recommendation-evaluation-dataset-v1";
            var checkpoint = new Rca2RuntimeContracts.Checkpoint(checkpointRef, response.size(), captured,
                    source, "rca2-primary-response-checkpoint-v1");
            var lineageMetadata = new Rca2RuntimeContracts.Lineage(lineage,
                    "recommendation-data-candidate-v1", "rca2-isolated-nonproduction-v1",
                    Rca2RuntimeContracts.WORK_START_SHA);
            var primary = new Rca2RuntimeContracts.PrimarySnapshot(lane, source, result, digest,
                    response.size(), cursorDigest, checkpoint, lineageMetadata, primaryLatencyMillis);
            add(servlet.getRequest(), new Rca2RuntimeContracts.ShadowRequest(requestHash, identityRef,
                    Rca2IdentityPolicy.PURPOSE, "rca2-post-response-hook", Rca2RuntimeContracts.ENVIRONMENT,
                    "rca2-flag-default-off-v1", captured, primary));
        }
    }

    @SuppressWarnings("unchecked")
    List<Rca2RuntimeContracts.ShadowRequest> drain(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        request.removeAttribute(ATTRIBUTE);
        if (value instanceof List<?> list) return List.copyOf((List<Rca2RuntimeContracts.ShadowRequest>) list);
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static void add(HttpServletRequest request, Rca2RuntimeContracts.ShadowRequest shadowRequest) {
        Object value = request.getAttribute(ATTRIBUTE);
        List<Rca2RuntimeContracts.ShadowRequest> requests;
        if (value instanceof List<?> list) requests = (List<Rca2RuntimeContracts.ShadowRequest>) list;
        else { requests = new ArrayList<>(); request.setAttribute(ATTRIBUTE, requests); }
        requests.add(shadowRequest);
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
