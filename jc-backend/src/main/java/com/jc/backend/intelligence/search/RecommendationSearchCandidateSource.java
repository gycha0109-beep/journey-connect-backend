package com.jc.backend.intelligence.search;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationSearchCandidateSource {

    private static final String FIND_MATCHING = """
            with input as (
              select lower(cast(? as text)) as keyword,
                     lower(cast(? as text)) as region,
                     cast(? as timestamptz) as reference_time
            ),
            eligible as (
              select p.id,
                     p.author_id,
                     r.slug as region_slug,
                     upper(r.slug) as region_code,
                     r.name_local,
                     r.name_ko,
                     r.name_en,
                     p.title,
                     p.content,
                     p.created_at,
                     p.published_at,
                     p.view_count
              from public.posts p
              join public.app_users author on author.id = p.author_id
              join public.regions r on r.id = p.main_region_id and r.is_active = true
              cross join input i
              where p.status = 'published'
                and p.visibility = 'public'
                and p.moderation_status = 'visible'
                and p.deleted_at is null
                and author.account_status = 'active'
                and p.published_at <= i.reference_time
                and (
                  i.region = ''
                  or lower(r.slug) = i.region
                  or lower(r.name_local) = i.region
                  or lower(coalesce(r.name_ko, '')) = i.region
                  or lower(coalesce(r.name_en, '')) = i.region
                )
            ),
            tag_data as (
              select e.id,
                     coalesce(string_agg(t.slug, ',' order by t.sort_order, t.slug)
                       filter (where t.id is not null), '') as tag_slugs,
                     coalesce(bool_or(
                       lower(t.slug) = i.keyword
                       or lower(t.name_ko) = i.keyword
                       or lower(coalesce(t.name_en, '')) = i.keyword
                     ) filter (where t.id is not null and i.keyword <> ''), false) as tag_exact_match,
                     coalesce(bool_or(
                       lower(t.slug) like '%' || i.keyword || '%'
                       or lower(t.name_ko) like '%' || i.keyword || '%'
                       or lower(coalesce(t.name_en, '')) like '%' || i.keyword || '%'
                     ) filter (where t.id is not null and i.keyword <> ''), false) as tag_contains_match
              from eligible e
              cross join input i
              left join public.post_tags pt on pt.post_id = e.id
              left join public.tags t on t.id = pt.tag_id and t.is_active = true
              group by e.id
            ),
            like_counts as (
              select pl.post_id, count(*) as like_count
              from public.post_likes pl
              join eligible e on e.id = pl.post_id
              group by pl.post_id
            ),
            bookmark_counts as (
              select b.post_id, count(*) as bookmark_count
              from public.bookmarks b
              join eligible e on e.id = b.post_id
              group by b.post_id
            ),
            matching as (
              select e.id,
                     e.author_id,
                     e.region_slug,
                     e.region_code,
                     e.name_local,
                     e.name_ko,
                     e.name_en,
                     e.title,
                     e.created_at,
                     e.published_at,
                     e.view_count,
                     coalesce(l.like_count, 0) as like_count,
                     coalesce(b.bookmark_count, 0) as bookmark_count,
                     0::integer as recent_exposure_count,
                     td.tag_slugs,
                     (i.keyword <> '' and lower(btrim(e.title)) = i.keyword) as title_exact_match,
                     (i.keyword <> '' and lower(e.title) like i.keyword || '%') as title_prefix_match,
                     (i.keyword <> '' and lower(e.title) like '%' || i.keyword || '%') as title_contains_match,
                     td.tag_exact_match,
                     td.tag_contains_match,
                     (i.keyword <> '' and (
                       lower(e.region_slug) = i.keyword
                       or lower(e.name_local) = i.keyword
                       or lower(coalesce(e.name_ko, '')) = i.keyword
                       or lower(coalesce(e.name_en, '')) = i.keyword
                     )) as region_exact_match,
                     (i.keyword <> '' and (
                       lower(e.region_slug) like '%' || i.keyword || '%'
                       or lower(e.name_local) like '%' || i.keyword || '%'
                       or lower(coalesce(e.name_ko, '')) like '%' || i.keyword || '%'
                       or lower(coalesce(e.name_en, '')) like '%' || i.keyword || '%'
                     )) as region_contains_match,
                     (i.keyword <> '' and lower(e.content) like '%' || i.keyword || '%') as content_match
              from eligible e
              cross join input i
              join tag_data td on td.id = e.id
              left join like_counts l on l.post_id = e.id
              left join bookmark_counts b on b.post_id = e.id
              where i.keyword = ''
                 or lower(e.title) like '%' || i.keyword || '%'
                 or lower(e.content) like '%' || i.keyword || '%'
                 or lower(e.name_local) like '%' || i.keyword || '%'
                 or lower(coalesce(e.name_ko, '')) like '%' || i.keyword || '%'
                 or lower(coalesce(e.name_en, '')) like '%' || i.keyword || '%'
                 or td.tag_contains_match
            )
            select matching.*, count(*) over() as total_count
            from matching
            order by published_at desc, id desc
            limit ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public RecommendationSearchCandidateSource(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<RecommendationSearchCandidateRow> findEligible(
            long userId,
            String keyword,
            String region,
            int candidateLimit,
            Instant referenceTime) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (referenceTime == null) {
            throw new IllegalArgumentException("referenceTime is required");
        }
        int safeLimit = Math.min(Math.max(candidateLimit, 1), 5_000);
        return jdbcTemplate.query(
                FIND_MATCHING,
                this::map,
                normalize(keyword),
                normalize(region),
                Timestamp.from(referenceTime),
                safeLimit);
    }

    private RecommendationSearchCandidateRow map(ResultSet resultSet, int rowNumber)
            throws SQLException {
        long recentExposureCount = resultSet.getLong("recent_exposure_count");
        return new RecommendationSearchCandidateRow(
                resultSet.getLong("id"),
                resultSet.getLong("author_id"),
                resultSet.getString("region_code"),
                resultSet.getString("region_slug"),
                names(
                        resultSet.getString("name_local"),
                        resultSet.getString("name_ko"),
                        resultSet.getString("name_en")),
                resultSet.getString("title"),
                csv(resultSet.getString("tag_slugs")),
                resultSet.getBoolean("title_exact_match"),
                resultSet.getBoolean("title_prefix_match"),
                resultSet.getBoolean("title_contains_match"),
                resultSet.getBoolean("tag_exact_match"),
                resultSet.getBoolean("tag_contains_match"),
                resultSet.getBoolean("region_exact_match"),
                resultSet.getBoolean("region_contains_match"),
                resultSet.getBoolean("content_match"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("published_at").toInstant(),
                resultSet.getLong("view_count"),
                resultSet.getLong("like_count"),
                resultSet.getLong("bookmark_count"),
                (int) Math.min(recentExposureCount, Integer.MAX_VALUE),
                resultSet.getLong("total_count"));
    }

    private static List<String> names(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
