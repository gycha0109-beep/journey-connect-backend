package com.jc.backend.recommendation.p1;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.p1.profile.BehaviorProfileEvent;
import com.jc.recommendation.p1.profile.ExplicitPreference;
import com.jc.recommendation.p1.profile.P1FeatureVocabulary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationP1ProfileSource {
    private final JdbcTemplate jdbcTemplate;

    public RecommendationP1ProfileSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<ExplicitPreference> findExplicitPreferences(long userId) {
        return jdbcTemplate.query(
                """
                select feature_id, preference_kind, strength
                from public.recommendation_user_preference
                where user_id = ? and active = true
                order by feature_id
                """,
                (resultSet, rowNumber) -> new ExplicitPreference(
                        resultSet.getString("feature_id"),
                        PreferenceKind.valueOf(resultSet.getString("preference_kind")
                                .toUpperCase(Locale.ROOT)),
                        resultSet.getDouble("strength")),
                userId);
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<BehaviorProfileEvent> findBehaviorEvents(
            long userId,
            Instant fromInclusive,
            Instant toInclusive,
            int limit) {
        return jdbcTemplate.query(
                """
                with events as (
                  select b.event_id, b.event_type, b.occurred_at,
                         r.slug region_slug,
                         coalesce(string_agg(t.slug, ',' order by t.sort_order, t.slug), '') tag_slugs
                  from public.recommendation_behavior_event b
                  left join public.posts p
                    on p.id = b.source_entity_id and b.entity_type = 'post'
                  left join public.regions r on r.id = p.main_region_id
                  left join public.post_tags pt on pt.post_id = p.id
                  left join public.tags t on t.id = pt.tag_id and t.is_active = true
                  where b.user_id = ?
                    and b.occurred_at >= ?
                    and b.occurred_at <= ?
                  group by b.event_id, b.event_type, b.occurred_at, r.slug
                  order by b.occurred_at desc, b.event_id desc
                  limit ?
                )
                select * from events order by occurred_at, event_id
                """,
                (resultSet, rowNumber) -> new BehaviorProfileEvent(
                        resultSet.getString("event_id"),
                        EventType.valueOf(resultSet.getString("event_type").toUpperCase(Locale.ROOT)),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        features(resultSet.getString("region_slug"), resultSet.getString("tag_slugs"))),
                userId,
                Timestamp.from(fromInclusive),
                Timestamp.from(toInclusive),
                limit);
    }

    private static List<String> features(String regionSlug, String tagSlugs) {
        Set<String> result = new LinkedHashSet<>();
        String region = regionFeature(regionSlug);
        if (region != null) {
            result.add(region);
        }
        if (tagSlugs != null) {
            for (String rawTag : tagSlugs.split(",")) {
                String feature = tagFeature(rawTag.trim());
                if (feature != null && P1FeatureVocabulary.isRegistered(feature)) {
                    result.add(feature);
                }
            }
        }
        return List.copyOf(result);
    }

    private static String regionFeature(String slug) {
        if (slug == null) {
            return null;
        }
        if (slug.equals("kr-seoul") || slug.startsWith("kr-seoul-")) {
            return "region:seoul";
        }
        if (slug.equals("kr-busan") || slug.startsWith("kr-busan-")) {
            return "region:busan";
        }
        if (slug.equals("kr-jeju") || slug.startsWith("kr-jeju-")) {
            return "region:jeju";
        }
        if (slug.equals("kr-gangwon") || slug.startsWith("kr-gangwon-")) {
            return "region:gangwon";
        }
        if (slug.equals("kr-gyeongju") || slug.startsWith("kr-gyeongju-")) {
            return "region:gyeongju";
        }
        return null;
    }

    private static String tagFeature(String tag) {
        return switch (tag) {
            case "food" -> "theme:food";
            case "cafe" -> "theme:cafe";
            case "nature" -> "theme:nature";
            case "history" -> "theme:history";
            case "adventure" -> "theme:adventure";
            case "wellness" -> "theme:wellness";
            case "running" -> "activity:running";
            case "plogging" -> "activity:plogging";
            case "pilgrimage" -> "activity:pilgrimage";
            case "cycling" -> "activity:cycling";
            case "solo-travel" -> "companion:solo";
            case "couple-trip" -> "companion:couple";
            case "family-trip" -> "companion:family";
            default -> null;
        };
    }
}
