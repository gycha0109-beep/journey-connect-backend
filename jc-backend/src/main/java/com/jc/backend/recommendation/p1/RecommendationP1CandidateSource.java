package com.jc.backend.recommendation.p1;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.recommendation.RecommendationCandidateRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationP1CandidateSource {
    private final JdbcTemplate jdbcTemplate;

    public RecommendationP1CandidateSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<RecommendationCandidateRow> findEligible(
            long userId,
            Instant referenceTime,
            int retrievalLimit,
            int coreCandidateLimit) {
        int retrieval = Math.min(Math.max(retrievalLimit, coreCandidateLimit), 1_000);
        int core = Math.min(Math.max(coreCandidateLimit, 1), 100);
        return jdbcTemplate.query(
                """
                with eligible as (
                  select p.id, p.author_id, p.main_region_id, p.visibility,
                         p.created_at, p.published_at, p.view_count
                  from public.posts p
                  join public.app_users a on a.id = p.author_id
                  join public.regions r on r.id = p.main_region_id and r.is_active = true
                  where p.status = 'published'
                    and p.visibility in ('public', 'followers')
                    and p.moderation_status = 'visible'
                    and p.deleted_at is null
                    and p.published_at <= ?
                    and a.account_status = 'active'
                    and public.can_user_view_post(?, p.id)
                    and exists (
                      select 1
                      from public.post_places pp
                      join public.places pl on pl.id = pp.place_id and pl.is_active = true
                      where pp.post_id = p.id
                    )
                  order by p.published_at desc, p.id desc
                  limit ?
                ),
                likes as (
                  select post_id, count(*)::bigint count
                  from public.post_likes
                  where post_id in (select id from eligible)
                  group by post_id
                ),
                saves as (
                  select post_id, count(*)::bigint count
                  from public.bookmarks
                  where post_id in (select id from eligible)
                  group by post_id
                ),
                exposures as (
                  select ec.source_entity_id post_id, count(*)::bigint count
                  from public.recommendation_exposure_candidate ec
                  join public.recommendation_exposure_event ee
                    on ee.event_id = ec.exposure_event_id
                  where ee.user_id = ?
                    and ee.served_at >= cast(? as timestamptz) - interval '30 days'
                    and ee.served_at <= ?
                    and ec.source_entity_id in (select id from eligible)
                  group by ec.source_entity_id
                ),
                tags as (
                  select pt.post_id, string_agg(t.slug, ',' order by t.sort_order, t.slug) slugs
                  from public.post_tags pt
                  join public.tags t on t.id = pt.tag_id and t.is_active = true
                  where pt.post_id in (select id from eligible)
                  group by pt.post_id
                )
                select e.id, e.author_id, r.slug region_slug, e.visibility,
                       e.created_at, e.published_at, e.view_count,
                       coalesce(l.count, 0) like_count,
                       coalesce(s.count, 0) bookmark_count,
                       coalesce(x.count, 0) recent_exposure_count,
                       coalesce(t.slugs, '') tag_slugs
                from eligible e
                join public.regions r on r.id = e.main_region_id
                left join likes l on l.post_id = e.id
                left join saves s on s.post_id = e.id
                left join exposures x on x.post_id = e.id
                left join tags t on t.post_id = e.id
                order by (
                  ln(1 + e.view_count)
                  + 2 * ln(1 + coalesce(l.count, 0))
                  + 3 * ln(1 + coalesce(s.count, 0))
                ) desc, e.published_at desc, e.id desc
                limit ?
                """,
                (resultSet, rowNumber) -> map(resultSet),
                Timestamp.from(referenceTime),
                userId,
                retrieval,
                userId,
                Timestamp.from(referenceTime),
                Timestamp.from(referenceTime),
                core);
    }

    private static RecommendationCandidateRow map(ResultSet resultSet) throws SQLException {
        String tagSlugs = resultSet.getString("tag_slugs");
        List<String> tags = tagSlugs == null || tagSlugs.isBlank()
                ? List.of()
                : Arrays.stream(tagSlugs.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        return new RecommendationCandidateRow(
                resultSet.getLong("id"),
                resultSet.getLong("author_id"),
                resultSet.getString("region_slug"),
                resultSet.getString("visibility"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("published_at").toInstant(),
                resultSet.getLong("view_count"),
                resultSet.getLong("like_count"),
                resultSet.getLong("bookmark_count"),
                Math.toIntExact(Math.min(resultSet.getLong("recent_exposure_count"), Integer.MAX_VALUE)),
                tags);
    }
}
