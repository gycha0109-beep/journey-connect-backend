package com.jc.backend.intelligence.crew;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.intelligence.crew.CrewRecommendationRanker.RankedCrew;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CrewRecommendationService {

    private static final int MAX_RESULT_LIMIT = 100;
    private static final int CANDIDATE_RETRIEVAL_LIMIT = 500;
    private static final ZoneId PRODUCT_DATE_ZONE = ZoneId.of("Asia/Seoul");

    private final CrewRecommendationCandidateSource candidates;
    private final CrewRecommendationProfileService profiles;
    private final CrewRecommendationRanker ranker = new CrewRecommendationRanker();

    public CrewRecommendationService(
            CrewRecommendationCandidateSource candidates,
            CrewRecommendationProfileService profiles) {
        this.candidates = candidates;
        this.profiles = profiles;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public RecommendationResult recommend(long userId, int limit) {
        return recommend(userId, limit, Instant.now());
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public RecommendationResult recommend(long userId, int limit, Instant referenceTime) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (limit < 1 || limit > MAX_RESULT_LIMIT) {
            throw new IllegalArgumentException("limit must be within [1,100]");
        }
        BehaviorProfileSnapshot profile = profiles.build(userId, referenceTime);
        LocalDate referenceDate = LocalDate.ofInstant(referenceTime, PRODUCT_DATE_ZONE);
        List<RankedCrew> ranked = ranker.rank(
                candidates.findRecruiting(userId, CANDIDATE_RETRIEVAL_LIMIT),
                profile,
                referenceTime,
                referenceDate,
                limit);
        return new RecommendationResult(
                CrewRecommendationContract.CONTRACT_VERSION,
                CrewRecommendationContract.POLICY_VERSION,
                CrewRecommendationRanker.SCORE_POLICY_VERSION,
                profile.profilePolicyVersion(),
                profile.featureVocabularyVersion(),
                profile.fingerprint(),
                referenceTime,
                ranked);
    }

    public record RecommendationResult(
            String contractVersion,
            String rankingPolicyVersion,
            String scorePolicyVersion,
            String profilePolicyVersion,
            String featureVocabularyVersion,
            String profileFingerprint,
            Instant referenceTime,
            List<RankedCrew> crews) {
        public RecommendationResult {
            crews = List.copyOf(crews);
        }
    }
}
