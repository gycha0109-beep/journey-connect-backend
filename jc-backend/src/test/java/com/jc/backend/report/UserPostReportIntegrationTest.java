package com.jc.backend.report;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserPostReportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;

    private UserAccount owner;
    private UserAccount reporter;
    private Region seoul;
    private JourneyPost published;

    @BeforeEach
    void setUp() {
        owner = users.save(new UserAccount("pf9-owner@example.com", "hash", "pf9-owner"));
        reporter = users.save(new UserAccount("pf9-reporter@example.com", "hash", "pf9-reporter"));
        seoul = region(regions, "KR-SEOUL");
        published = posts.save(publishedPost(places, owner, seoul, "PF9 reportable", "reportable body"));
    }

    @Test
    void unauthenticatedRequestIsRejectedBeforeReportCommand() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/reports", published.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCategory\":\"spam\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        assertThat(reportCount()).isZero();
    }

    @Test
    void successfulReportPersistsCanonicalEvidenceSnapshotAndNormalizedReason() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/reports", published.getId())
                        .with(jwt().jwt(token -> token.subject(reporter.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonCategory": " Spam ",
                                  "reasonDetail": "  suspicious links  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").isNumber())
                .andExpect(jsonPath("$.data.status").value("pending"));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                select reporter_id,
                       target_type,
                       target_entity_id,
                       reason_category,
                       reason_detail,
                       status,
                       target_snapshot ->> 'type' as snapshot_type,
                       target_snapshot ->> 'id' as snapshot_id,
                       target_snapshot ->> 'title' as snapshot_title,
                       target_snapshot ->> 'content' as snapshot_content
                from public.reports
                where reporter_id = ? and target_type = 'post' and target_entity_id = ?
                """, reporter.getId(), published.getId());

        assertThat(((Number) row.get("reporter_id")).longValue()).isEqualTo(reporter.getId());
        assertThat(row.get("target_type")).isEqualTo("post");
        assertThat(((Number) row.get("target_entity_id")).longValue()).isEqualTo(published.getId());
        assertThat(row.get("reason_category")).isEqualTo("spam");
        assertThat(row.get("reason_detail")).isEqualTo("suspicious links");
        assertThat(row.get("status")).isEqualTo("pending");
        assertThat(row.get("snapshot_type")).isEqualTo("post");
        assertThat(row.get("snapshot_id")).isEqualTo(published.getId().toString());
        assertThat(row.get("snapshot_title")).isEqualTo("PF9 reportable");
        assertThat(row.get("snapshot_content")).isEqualTo("reportable body");
    }

    @Test
    void duplicateOpenReportReturnsStableConflictWithoutSecondRow() throws Exception {
        reportExpectingCreated(published.getId(), reporter.getId(), "privacy");

        mockMvc.perform(reportRequest(published.getId(), reporter.getId(), "privacy"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_EXISTS"));

        assertThat(reportCount()).isEqualTo(1L);
    }

    @Test
    void inaccessibleMissingSelfDraftAndHiddenTargetsCollapseToStableNotFound() throws Exception {
        JourneyPost selfPost = posts.save(publishedPost(
                places, reporter, seoul, "PF9 self", "self body"));

        JourneyPost draft = new JourneyPost(owner, seoul, "PF9 draft", "draft body");
        draft.update(null, null, null, false);
        draft = posts.save(draft);

        JourneyPost hidden = posts.save(publishedPost(
                places, owner, seoul, "PF9 hidden", "hidden body"));
        jdbcTemplate.update(
                "update public.posts set moderation_status = 'hidden' where id = ?",
                hidden.getId());

        try {
            assertReportTargetNotFound(Long.MAX_VALUE, reporter.getId());
            assertReportTargetNotFound(selfPost.getId(), reporter.getId());
            assertReportTargetNotFound(draft.getId(), reporter.getId());
            assertReportTargetNotFound(hidden.getId(), reporter.getId());

            assertThat(reportCount()).isZero();
        } finally {
            jdbcTemplate.update(
                    "update public.posts set moderation_status = 'visible' where id = ?",
                    hidden.getId());
        }
    }

    @Test
    void unsupportedReasonReturnsStableBadRequestWithoutDatabaseWrite() throws Exception {
        mockMvc.perform(reportRequest(published.getId(), reporter.getId(), "inappropriate_content"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_REASON"));

        assertThat(reportCount()).isZero();
    }

    @Test
    void inactiveReporterIsRejectedByCanonicalDatabaseCommand() throws Exception {
        jdbcTemplate.update(
                "update public.app_users set account_status = 'suspended' where id = ?",
                reporter.getId());

        mockMvc.perform(reportRequest(published.getId(), reporter.getId(), "harassment"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_INACTIVE"));

        assertThat(reportCount()).isZero();
    }

    private void reportExpectingCreated(long postId, long userId, String reason) throws Exception {
        mockMvc.perform(reportRequest(postId, userId, reason))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("pending"));
    }

    private void assertReportTargetNotFound(long postId, long userId) throws Exception {
        mockMvc.perform(reportRequest(postId, userId, "spam"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder reportRequest(
            long postId,
            long userId,
            String reason) {
        return post("/api/v1/posts/{postId}/reports", postId)
                .with(jwt().jwt(token -> token.subject(Long.toString(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCategory\":\"" + reason + "\"}");
    }

    private long reportCount() {
        Long count = jdbcTemplate.queryForObject("select count(*) from public.reports", Long.class);
        return count == null ? 0L : count;
    }
}
