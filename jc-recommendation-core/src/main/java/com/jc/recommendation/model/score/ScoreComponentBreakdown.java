package com.jc.recommendation.model.score;

import java.util.Objects;

public record ScoreComponentBreakdown(
        ScoreComponentName component,
        String resultStatus,
        String resultNotApplicableReason,
        String resultPolicyVersion,
        double globalBaseWeight,
        double entityEffectiveWeight,
        ScoreComponentAvailability availability,
        Double rawScore,
        Double valueUsed,
        Double weightedContribution
) {
    public ScoreComponentBreakdown {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(resultStatus, "resultStatus");
        Objects.requireNonNull(resultPolicyVersion, "resultPolicyVersion");
        Objects.requireNonNull(availability, "availability");
    }
}
