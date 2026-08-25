package com.jc.backend.intelligence.crew;

import com.jc.recommendation.p1.profile.P1FeatureVocabulary;
import java.util.Locale;

final class CrewRecommendationFeatureMapper {

    private CrewRecommendationFeatureMapper() {}

    static String regionFeature(String regionSlug) {
        if (regionSlug == null) {
            return null;
        }
        String slug = regionSlug.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        String feature = null;
        if (slug.equals("kr-seoul") || slug.startsWith("kr-seoul-")) {
            feature = "region:seoul";
        } else if (slug.equals("kr-busan") || slug.startsWith("kr-busan-")) {
            feature = "region:busan";
        } else if (slug.equals("kr-jeju") || slug.startsWith("kr-jeju-")) {
            feature = "region:jeju";
        } else if (slug.equals("kr-gangwon") || slug.startsWith("kr-gangwon-")) {
            feature = "region:gangwon";
        } else if (slug.equals("kr-gyeongju") || slug.startsWith("kr-gyeongju-")) {
            feature = "region:gyeongju";
        }
        return registered(feature);
    }

    static String tagFeature(String tagSlug) {
        if (tagSlug == null) {
            return null;
        }
        String slug = tagSlug.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        String feature = switch (slug) {
            case "food" -> "theme:food";
            case "cafe" -> "theme:cafe";
            case "nature" -> "theme:nature";
            case "night", "night-view" -> "theme:night_view";
            case "history" -> "theme:history";
            case "adventure" -> "theme:adventure";
            case "wellness" -> "theme:wellness";
            case "running" -> "activity:running";
            case "plogging" -> "activity:plogging";
            case "pilgrimage" -> "activity:pilgrimage";
            case "cycling" -> "activity:cycling";
            case "photo", "photography", "photo-walk" -> "activity:photography";
            case "solo-travel" -> "companion:solo";
            case "couple-trip" -> "companion:couple";
            case "family-trip" -> "companion:family";
            default -> null;
        };
        return registered(feature);
    }

    private static String registered(String feature) {
        return feature != null && P1FeatureVocabulary.isRegistered(feature) ? feature : null;
    }
}
