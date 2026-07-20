package com.jc.recommendation.p1.policy;

import com.jc.recommendation.model.context.ContextSurface;
import com.jc.recommendation.p1.profile.UserProfileSegment;
import java.util.Objects;

public record P1PolicySelectorInput(
        UserProfileSegment segment,
        ContextSurface surface,
        P1SessionContext sessionContext,
        P1ExperimentAssignment experimentAssignment) {

    public P1PolicySelectorInput {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(sessionContext, "sessionContext");
        Objects.requireNonNull(experimentAssignment, "experimentAssignment");
    }
}
