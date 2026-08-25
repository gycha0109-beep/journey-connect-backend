package com.jc.backend.intelligence.crew;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.p1.RecommendationP1ProfileSource;
import com.jc.recommendation.p1.profile.BehaviorProfileBuilder;
import com.jc.recommendation.p1.profile.BehaviorProfilePolicies;
import com.jc.recommendation.p1.profile.BehaviorProfilePolicy;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import com.jc.recommendation.p1.profile.BuildBehaviorProfileInput;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class CrewRecommendationProfileService {

    private final RecommendationP1ProfileSource profileSource;
    private final BehaviorProfileBuilder profileBuilder = new BehaviorProfileBuilder();

    public CrewRecommendationProfileService(RecommendationP1ProfileSource profileSource) {
        this.profileSource = profileSource;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public BehaviorProfileSnapshot build(long userId, Instant referenceTime) {
        BehaviorProfilePolicy policy = BehaviorProfilePolicies.V1;
        return profileBuilder.build(new BuildBehaviorProfileInput(
                Long.toString(userId),
                referenceTime,
                profileSource.findExplicitPreferences(userId),
                profileSource.findBehaviorEvents(
                        userId,
                        referenceTime.minusSeconds((long) policy.lookbackDays() * 86_400L),
                        referenceTime,
                        policy.maximumEvents()),
                policy));
    }
}
