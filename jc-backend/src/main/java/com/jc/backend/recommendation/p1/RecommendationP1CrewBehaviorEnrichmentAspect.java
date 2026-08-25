package com.jc.backend.recommendation.p1;

import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.p1.profile.BehaviorProfileEvent;
import com.jc.recommendation.p1.profile.P1FeatureVocabulary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Enriches crew behavior with canonical region features without modifying the frozen P1 source. */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public final class RecommendationP1CrewBehaviorEnrichmentAspect {

    private static final Comparator<BehaviorProfileEvent> DESCENDING =
            Comparator.comparing(BehaviorProfileEvent::occurredAt)
                    .reversed()
                    .thenComparing(BehaviorProfileEvent::eventId, Comparator.reverseOrder());
    private static final Comparator<BehaviorProfileEvent> ASCENDING =
            Comparator.comparing(BehaviorProfileEvent::occurredAt)
                    .thenComparing(BehaviorProfileEvent::eventId);

    private final JdbcTemplate jdbcTemplate;

    public RecommendationP1CrewBehaviorEnrichmentAspect(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Around("execution(java.util.List com.jc.backend.recommendation.p1.RecommendationP1ProfileSource.findBehaviorEvents(..))")
    public Object enrichCrewBehavior(ProceedingJoinPoint joinPoint) throws Throwable {
        @SuppressWarnings("unchecked")
        List<BehaviorProfileEvent> base = (List<BehaviorProfileEvent>) joinPoint.proceed();
        Object[] args = joinPoint.getArgs();
        long userId = ((Number) args[0]).longValue();
        Instant fromInclusive = (Instant) args[1];
        Instant toInclusive = (Instant) args[2];
        int limit = ((Number) args[3]).intValue();
        if (limit <= 0) {
            return List.of();
        }

        List<BehaviorProfileEvent> crew = jdbcTemplate.query(
                """
                select b.event_id, b.event_type, b.occurred_at, r.slug region_slug
                from public.recommendation_behavior_event b
                join public.crews c
                  on c.id = b.source_entity_id
                 and b.entity_type = 'crew'
                left join public.regions r on r.id = c.region_id
                where b.user_id = ?
                  and b.event_type in ('crew_join', 'crew_leave')
                  and b.occurred_at >= ?
                  and b.occurred_at <= ?
                order by b.occurred_at desc, b.event_id desc
                limit ?
                """,
                (resultSet, rowNumber) -> new BehaviorProfileEvent(
                        resultSet.getString("event_id"),
                        EventType.valueOf(resultSet.getString("event_type").toUpperCase(Locale.ROOT)),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        regionFeatures(resultSet.getString("region_slug"))),
                userId,
                Timestamp.from(fromInclusive),
                Timestamp.from(toInclusive),
                limit);

        Map<String, BehaviorProfileEvent> byEventId = new LinkedHashMap<>();
        for (BehaviorProfileEvent event : base) {
            byEventId.put(event.eventId(), event);
        }
        for (BehaviorProfileEvent event : crew) {
            byEventId.put(event.eventId(), event);
        }

        List<BehaviorProfileEvent> selected = new ArrayList<>(byEventId.values());
        selected.sort(DESCENDING);
        if (selected.size() > limit) {
            selected = new ArrayList<>(selected.subList(0, limit));
        }
        selected.sort(ASCENDING);
        return List.copyOf(selected);
    }

    private static List<String> regionFeatures(String slug) {
        String feature = regionFeature(slug);
        if (feature == null || !P1FeatureVocabulary.isRegistered(feature)) {
            return List.of();
        }
        return List.of(feature);
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
}
