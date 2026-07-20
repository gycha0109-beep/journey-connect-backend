package com.jc.recommendation.p1.profile;

import com.jc.recommendation.vocabulary.FeatureVocabularyV1;
import java.util.Set;

public final class P1FeatureVocabulary {
    public static final String VERSION = "feature-vocabulary-v2";

    private static final Set<String> P1_FEATURES = Set.of(
            "theme:adventure",
            "theme:history",
            "theme:wellness",
            "activity:running",
            "activity:plogging",
            "activity:pilgrimage",
            "activity:cycling");

    private P1FeatureVocabulary() {
    }

    public static boolean isRegistered(String featureId) {
        return FeatureVocabularyV1.isRegisteredFeature(featureId) || P1_FEATURES.contains(featureId);
    }
}
