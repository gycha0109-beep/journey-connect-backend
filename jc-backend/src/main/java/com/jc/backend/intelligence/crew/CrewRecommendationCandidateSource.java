package com.jc.backend.intelligence.crew;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.CandidateFacts;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.TagFeatureState;
import com.jc.backend.intelligence.crew.CrewRecommendationContract.ViewerRelation;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CrewRecommendationCandidateSource {

    private final JdbcTemplate jdbc;

    public CrewRecommendationCandidateSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
    public List<Candidate> findRecruiting(long viewerId, int limit) {
        return jdbc.query(
                """
                select c.id crew_id,
                       c.owner_id,
                       upper(r.slug) region_code,
                       r.slug region_slug,
                       c.travel_date,
                       c.capacity,
                       c.recruiting,
                       c.created_at,
                       (select count(*)
                          from public.crew_members active_member
                         where active_member.crew_id = c.id
                           and active_member.status in ('OWNER', 'APPROVED')) active_member_count,
                       (select viewer_member.status
                          from public.crew_members viewer_member
                         where viewer_member.crew_id = c.id
                           and viewer_member.user_id = ?) viewer_status,
                       coalesce((
                         select string_agg(t.slug, ',' order by t.sort_order, t.slug)
                           from public.crew_tags ct
                           join public.tags t on t.id = ct.tag_id and t.is_active = true
                          where ct.crew_id = c.id
                       ), '') tag_slugs
                  from public.crews c
                  join public.regions r on r.id = c.region_id
                 where c.recruiting = true
                 order by c.created_at desc, c.id desc
                 limit ?
                """,
                (rs, rowNumber) -> {
                    String tagSlugs = rs.getString("tag_slugs");
                    List<String> tags = tagSlugs == null || tagSlugs.isBlank()
                            ? List.of()
                            : Arrays.stream(tagSlugs.split(","))
                                    .map(String::trim)
                                    .filter(value -> !value.isBlank())
                                    .toList();
                    Date travelDate = rs.getDate("travel_date");
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    CandidateFacts facts = new CandidateFacts(
                            rs.getLong("crew_id"),
                            rs.getLong("owner_id"),
                            rs.getString("region_code"),
                            rs.getString("region_slug"),
                            travelDate == null ? null : travelDate.toLocalDate(),
                            rs.getInt("capacity"),
                            rs.getLong("active_member_count"),
                            rs.getBoolean("recruiting"),
                            createdAt.toInstant(),
                            tags.isEmpty() ? TagFeatureState.EMPTY : TagFeatureState.PRESENT,
                            tags);
                    return new Candidate(facts, relation(viewerId, facts.ownerId(), rs.getString("viewer_status")));
                },
                viewerId,
                limit);
    }

    private static ViewerRelation relation(long viewerId, long ownerId, String status) {
        if (viewerId == ownerId || "OWNER".equals(status)) {
            return ViewerRelation.OWNER;
        }
        if ("PENDING".equals(status)) {
            return ViewerRelation.PENDING;
        }
        if ("APPROVED".equals(status)) {
            return ViewerRelation.APPROVED;
        }
        if (status != null) {
            return ViewerRelation.HISTORY_ONLY;
        }
        return ViewerRelation.NONE;
    }

    public record Candidate(CandidateFacts facts, ViewerRelation viewerRelation) {}
}
