package com.jc.recommendation.interest;

import com.jc.recommendation.model.feature.ExplicitPreference;
import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.FeatureValidationStatus;
import com.jc.recommendation.model.interest.UserInterestSignal;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExplicitInterestSignalBuilder {
    private ExplicitInterestSignalBuilder() {
    }

    public static List<UserInterestSignal> build(List<ExplicitPreference> explicitPreferences) {
        Set<String> seen = new HashSet<>();
        List<UserInterestSignal> signals = new ArrayList<>(explicitPreferences.size());

        for (ExplicitPreference preference : explicitPreferences) {
            if (preference.userId().isEmpty()) {
                throw new IllegalArgumentException("Explicit preference userId must not be empty");
            }
            if (!FeatureVocabularyV1.isRegisteredFeature(preference.featureId())) {
                throw new IllegalArgumentException("Unknown feature ID: " + preference.featureId());
            }
            if (!Double.isFinite(preference.strength())
                    || preference.strength() < 0
                    || preference.strength() > 1) {
                throw new IllegalArgumentException(
                        "Explicit preference strength must be within 0..1: " + preference.featureId()
                );
            }
            String key = preference.userId() + ":" + preference.featureId();
            if (!seen.add(key)) {
                throw new IllegalArgumentException("Duplicate explicit preference for " + key);
            }
            signals.add(new UserInterestSignal(
                    "explicit:" + preference.userId() + ":" + preference.featureId(),
                    preference.userId(),
                    preference.featureId(),
                    preference.preference(),
                    preference.strength(),
                    FeatureSource.EXPLICIT,
                    FeatureValidationStatus.ACCEPTED,
                    preference.updatedAt()
            ));
        }

        signals.sort(Comparator
                .comparing(UserInterestSignal::userId)
                .thenComparing(UserInterestSignal::featureId));
        return List.copyOf(signals);
    }
}
