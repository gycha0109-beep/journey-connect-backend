package com.jc.backend.recommendation.p1;

import com.jc.backend.recommendation.RecommendationCandidateRow;
import com.jc.backend.recommendation.RecommendationCoreCandidate;
import com.jc.backend.recommendation.RecommendationCoreInputMapper;
import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.p1.profile.P1FeatureVocabulary;
import com.jc.recommendation.p1.ranking.P1CandidateInput;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public final class RecommendationP1CandidateMapper {
    private final RecommendationCoreInputMapper coreMapper;

    public RecommendationP1CandidateMapper(RecommendationCoreInputMapper coreMapper) {
        this.coreMapper = coreMapper;
    }

    public P1CandidateInput map(RecommendationCandidateRow row) {
        RecommendationCoreCandidate core = coreMapper.map(row);
        TreeSet<String> featureIds = new TreeSet<>();
        core.features().forEach(feature -> featureIds.add(feature.featureId()));
        row.tagSlugs().stream()
                .map(RecommendationP1CandidateMapper::p1Feature)
                .filter(feature -> feature != null && P1FeatureVocabulary.isRegistered(feature))
                .forEach(featureIds::add);
        String primaryTheme = featureIds.stream()
                .filter(feature -> feature.startsWith("theme:"))
                .findFirst()
                .orElse(core.diversity().primaryThemeFeatureId());
        DiversityCandidateMetadata diversity = new DiversityCandidateMetadata(
                core.diversity().entityId(),
                core.diversity().entityType(),
                core.diversity().authorId(),
                core.diversity().primaryRegionFeatureId(),
                primaryTheme,
                core.diversity().duplicateGroupId());
        return new P1CandidateInput(
                Long.toString(row.postId()),
                core.entity().entityType(),
                row.publishedAt(),
                List.copyOf(featureIds),
                row.viewCount(),
                row.likeCount(),
                row.bookmarkCount(),
                row.recentExposureCount(),
                1.0d,
                diversity);
    }

    public List<P1CandidateInput> mapAll(List<RecommendationCandidateRow> rows) {
        return rows.stream().map(this::map).toList();
    }

    private static String p1Feature(String tag) {
        return switch (tag) {
            case "history" -> "theme:history";
            case "adventure" -> "theme:adventure";
            case "wellness" -> "theme:wellness";
            case "running" -> "activity:running";
            case "plogging" -> "activity:plogging";
            case "pilgrimage" -> "activity:pilgrimage";
            case "cycling" -> "activity:cycling";
            default -> null;
        };
    }
}
