package com.jc.backend.recommendation;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** canonical 공개 정책을 통과한 posts만 사용자별 추천 후보로 읽습니다. */
@Repository
@DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
public class RecommendationCandidateSource {

    private static final String FIND_ELIGIBLE = """
            select p.id,
                   p.author_id,
                   r.slug as region_slug,
                   p.visibility,
                   p.created_at,
                   p.published_at,
                   p.view_count,
                   (select count(*) from public.post_likes pl where pl.post_id = p.id) as like_count,
                   (select count(*) from public.bookmarks b where b.post_id = p.id) as bookmark_count,
                   (select count(*)
                      from public.recommendation_exposure_candidate ec
                      join public.recommendation_exposure_event ee
                        on ee.event_id = ec.exposure_event_id
                     where ec.source_entity_id = p.id
                       and ee.user_id = ?
                       and ee.served_at >= current_timestamp - interval '30 days'
                   ) as recent_exposure_count,
                   coalesce((
                       select string_agg(t.slug, ',' order by t.sort_order, t.slug)
                       from public.post_tags pt
                       join public.tags t on t.id = pt.tag_id and t.is_active = true
                       where pt.post_id = p.id
                   ), '') as tag_slugs
            from public.posts p
            join public.app_users author on author.id = p.author_id
            join public.regions r on r.id = p.main_region_id and r.is_active = true
            where p.status = 'published'
              and p.visibility in ('public', 'followers')
              and p.moderation_status = 'visible'
              and p.deleted_at is null
              and author.account_status = 'active'
              and public.can_user_view_post(?, p.id)
              and exists (
                  select 1
                  from public.post_places pp
                  join public.places place on place.id = pp.place_id and place.is_active = true
                  where pp.post_id = p.id
              )
            order by p.published_at desc, p.id desc
            limit ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public RecommendationCandidateSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecommendationCandidateRow> findEligible(long viewerId, int limit) {
        if (viewerId <= 0) {
            throw new IllegalArgumentException("viewerId must be positive");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 5_000);
        return jdbcTemplate.query(FIND_ELIGIBLE, this::map, viewerId, viewerId, safeLimit);
    }

    private RecommendationCandidateRow map(ResultSet resultSet, int rowNumber) throws SQLException {
        String tags = resultSet.getString("tag_slugs");
        List<String> tagSlugs = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        long recentExposureCount = resultSet.getLong("recent_exposure_count");
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
                (int) Math.min(recentExposureCount, Integer.MAX_VALUE),
                tagSlugs);
    }
}
