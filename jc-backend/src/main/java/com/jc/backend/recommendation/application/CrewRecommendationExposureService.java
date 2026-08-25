package com.jc.backend.recommendation.application;

import com.jc.backend.intelligence.crew.CrewRecommendationContract;
import com.jc.backend.intelligence.crew.CrewRecommendationRanker;
import com.jc.backend.intelligence.crew.CrewRecommendationService;
import com.jc.backend.recommendation.api.CrewRecommendationDtos;
import com.jc.backend.recommendation.api.CrewRecommendationReasonMapper;
import com.jc.backend.recommendation.persistence.CrewRecommendationExposureStore;
import com.jc.backend.recommendation.persistence.CrewRecommendationExposureStore.CandidateWrite;
import com.jc.backend.recommendation.persistence.CrewRecommendationExposureStore.ExposureWrite;
import com.jc.backend.recommendation.persistence.CrewRecommendationExposureStore.StoreResult;
import com.jc.backend.recommendation.persistence.RecommendationHashing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Builds and commits canonical Crew server-delivery exposure evidence. */
@Service
public final class CrewRecommendationExposureService {

    public static final String SCHEMA_VERSION = "crew_recommendation_exposure_v1";
    public static final String EXPOSURE_SEMANTIC = "server_delivery_commit_v1";
    private static final String EXPOSURE_ID_PREFIX = "crew-exposure-v1:";

    private final CrewRecommendationExposureStore exposureStore;

    public CrewRecommendationExposureService(CrewRecommendationExposureStore exposureStore) {
        this.exposureStore = exposureStore;
    }

    public CommitResult commit(
            long userId,
            int requestedLimit,
            CrewRecommendationService.RecommendationResult ranking,
            CrewRecommendationDtos.Response response,
            Instant servedAt) {
        Objects.requireNonNull(ranking, "ranking");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(servedAt, "servedAt");
        validateBinding(userId, requestedLimit, ranking, response);

        List<CandidateWrite> candidates = new ArrayList<>(response.items().size());
        List<String> candidateFingerprints = new ArrayList<>(response.items().size());
        for (int index = 0; index < response.items().size(); index++) {
            CrewRecommendationDtos.Item item = response.items().get(index);
            byte[] canonicalCandidate = canonicalCandidate(item);
            String fingerprint = RecommendationHashing.sha256(canonicalCandidate);
            candidateFingerprints.add(fingerprint);
            candidates.add(new CandidateWrite(
                    item.rank(),
                    item.crew().id(),
                    item.score(),
                    item.coverageMode(),
                    canonicalCandidate));
        }

        byte[] canonicalPayload = canonicalEvent(
                userId,
                requestedLimit,
                ranking,
                servedAt,
                candidateFingerprints);
        String canonicalFingerprint = RecommendationHashing.sha256(canonicalPayload);
        String exposureId = EXPOSURE_ID_PREFIX + canonicalFingerprint;

        StoreResult result = exposureStore.store(new ExposureWrite(
                exposureId,
                SCHEMA_VERSION,
                EXPOSURE_SEMANTIC,
                userId,
                CrewRecommendationContract.SURFACE,
                servedAt,
                ranking.referenceTime(),
                ranking.contractVersion(),
                ranking.rankingPolicyVersion(),
                ranking.scorePolicyVersion(),
                ranking.profilePolicyVersion(),
                ranking.featureVocabularyVersion(),
                ranking.profileFingerprint(),
                requestedLimit,
                canonicalPayload,
                candidates));
        return new CommitResult(exposureId, canonicalFingerprint, result);
    }

    private static void validateBinding(
            long userId,
            int requestedLimit,
            CrewRecommendationService.RecommendationResult ranking,
            CrewRecommendationDtos.Response response) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Crew exposure user ID must be positive.");
        }
        if (requestedLimit < 1 || requestedLimit > CrewRecommendationApiService.MAX_LIMIT) {
            throw new IllegalArgumentException("Crew exposure requested limit is invalid.");
        }
        if (!response.contractVersion().equals(ranking.contractVersion())
                || !response.rankingPolicyVersion().equals(ranking.rankingPolicyVersion())
                || !response.scorePolicyVersion().equals(ranking.scorePolicyVersion())
                || !response.referenceTime().equals(ranking.referenceTime())
                || response.items().size() != ranking.crews().size()) {
            throw new IllegalStateException("Crew response is not bound to the ranked result.");
        }

        for (int index = 0; index < response.items().size(); index++) {
            CrewRecommendationDtos.Item item = response.items().get(index);
            CrewRecommendationRanker.RankedCrew ranked = ranking.crews().get(index);
            List<CrewRecommendationDtos.Reason> expectedReasons =
                    CrewRecommendationReasonMapper.reasons(ranked.breakdown());
            if (item.rank() != index + 1
                    || item.rank() != ranked.rank()
                    || item.crew() == null
                    || item.crew().id() == null
                    || item.crew().id() != ranked.facts().crewId()
                    || !sameDouble(item.score(), ranked.breakdown().totalScore())
                    || !item.coverageMode().equals(ranked.breakdown().coverageMode().wireValue())
                    || !item.reasons().equals(expectedReasons)) {
                throw new IllegalStateException(
                        "Crew response candidate evidence does not match ranking at index " + index);
            }
        }
    }

    private static byte[] canonicalCandidate(CrewRecommendationDtos.Item item) {
        StringBuilder builder = new StringBuilder(256);
        append(builder, "schema", "crew-recommendation-exposure-candidate-v1");
        append(builder, "rank", Integer.toString(item.rank()));
        append(builder, "crewId", Long.toString(item.crew().id()));
        append(builder, "score", Double.toHexString(item.score()));
        append(builder, "coverageMode", item.coverageMode());
        append(builder, "reasonCount", Integer.toString(item.reasons().size()));
        for (CrewRecommendationDtos.Reason reason : item.reasons()) {
            append(
                    builder,
                    "reason",
                    reason.code().name() + ":" + Double.toHexString(reason.contribution()));
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] canonicalEvent(
            long userId,
            int requestedLimit,
            CrewRecommendationService.RecommendationResult ranking,
            Instant servedAt,
            List<String> candidateFingerprints) {
        StringBuilder builder = new StringBuilder(1024);
        append(builder, "schemaVersion", SCHEMA_VERSION);
        append(builder, "exposureSemantic", EXPOSURE_SEMANTIC);
        append(builder, "userId", Long.toString(userId));
        append(builder, "surface", CrewRecommendationContract.SURFACE);
        append(builder, "servedAt", servedAt.toString());
        append(builder, "referenceTime", ranking.referenceTime().toString());
        append(builder, "contractVersion", ranking.contractVersion());
        append(builder, "rankingPolicyVersion", ranking.rankingPolicyVersion());
        append(builder, "scorePolicyVersion", ranking.scorePolicyVersion());
        append(builder, "profilePolicyVersion", ranking.profilePolicyVersion());
        append(builder, "featureVocabularyVersion", ranking.featureVocabularyVersion());
        append(builder, "profileFingerprint", ranking.profileFingerprint());
        append(builder, "requestedLimit", Integer.toString(requestedLimit));
        append(builder, "returnedCount", Integer.toString(candidateFingerprints.size()));
        for (int index = 0; index < candidateFingerprints.size(); index++) {
            append(builder, "candidate", (index + 1) + ":" + candidateFingerprints.get(index));
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void append(StringBuilder builder, String key, String value) {
        Objects.requireNonNull(value, key);
        builder.append(key)
                .append('=')
                .append(value.length())
                .append(':')
                .append(value)
                .append('\n');
    }

    private static boolean sameDouble(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    public record CommitResult(
            String exposureId,
            String canonicalFingerprint,
            StoreResult storeResult) {}
}
