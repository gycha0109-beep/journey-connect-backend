package com.jc.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.auth.AuthDtos;
import com.jc.backend.auth.AuthService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminBasicApisIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void resetAppendOnlyAuditForIsolatedAssertions() {
        jdbc.execute("truncate table public.admin_actions restart identity");
    }

    @Test
    void anonymous_returns_401_for_all_admin_endpoints() throws Exception {
        for (String path : getPaths()) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String path : postPaths()) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(reason("x")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void normal_user_returns_403_for_all_admin_endpoints() throws Exception {
        AccountToken user = signupUser("normal");
        for (String path : getPaths()) {
            mockMvc.perform(get(path).header("Authorization", bearer(user.token())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
        }
        for (String path : postPaths()) {
            mockMvc.perform(post(path)
                            .header("Authorization", bearer(user.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reason("x")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
        }
    }

    @Test
    void suspended_admin_returns_403() throws Exception {
        AccountToken admin = activeAdmin("suspended-admin");
        setStatus(admin.id(), "suspended");
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void jwt_db_role_mismatch_returns_403() throws Exception {
        AccountToken admin = activeAdmin("mismatch");
        setRole(admin.id(), "user");
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void active_admin_is_allowed() throws Exception {
        AccountToken admin = activeAdmin("allowed");
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk());
    }

    @Test
    void dashboard_returns_minimal_aggregates() throws Exception {
        AccountToken admin = activeAdmin("dashboard");
        AccountToken user = signupUser("dashboard-user");
        createPublishedPost(user.id(), "visible", "content");
        setStatus(user.id(), "suspended");
        long post = createPost(admin.id(), "reported", "content", "public", "draft");
        createReport(user.id(), post, "spam", "dashboard report");

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(2))
                .andExpect(jsonPath("$.data.activePostCount").value(1))
                .andExpect(jsonPath("$.data.pendingReportCount").value(1))
                .andExpect(jsonPath("$.data.suspendedUserCount").value(1));
    }

    @Test
    void dashboard_recent_reports_are_limited() throws Exception {
        AccountToken admin = activeAdmin("recent-report-admin");
        AccountToken user = signupUser("recent-report-user");
        for (int i = 0; i < 7; i++) {
            long post = createPost(admin.id(), "reported-" + i, "content", "public", "draft");
            createReport(user.id(), post, "spam", "report-" + i);
        }
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentReports", hasSize(5)));
    }

    @Test
    void dashboard_recent_actions_are_limited() throws Exception {
        AccountToken admin = activeAdmin("recent-action-admin");
        for (int i = 0; i < 7; i++) {
            long post = createPost(admin.id(), "post-" + i, "content", "public", "draft");
            command(admin, "/api/admin/posts/" + post + "/hide", "hide-" + i).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentAdminActions", hasSize(5)));
    }

    @Test
    void dashboard_does_not_expose_internal_security_fields() throws Exception {
        AccountToken admin = activeAdmin("minimal");
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("password_hash"))))
                .andExpect(content().string(not(containsString("requestId"))))
                .andExpect(content().string(not(containsString("jc_admin"))));
    }

    @Test
    void admin_can_list_reports_and_filter_by_status() throws Exception {
        AccountToken admin = activeAdmin("report-list-admin");
        AccountToken reporter = signupUser("report-list-user");
        long post = createPost(admin.id(), "reported", "content", "public", "draft");
        createReport(reporter.id(), post, "spam", "filter-me");

        mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", bearer(admin.token()))
                        .param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("pending"));
    }

    @Test
    void admin_can_get_report_detail() throws Exception {
        AccountToken admin = activeAdmin("report-detail-admin");
        AccountToken reporter = signupUser("report-detail-user");
        long post = createPost(admin.id(), "reported", "content", "public", "draft");
        long report = createReport(reporter.id(), post, "privacy", "detail");

        mockMvc.perform(get("/api/admin/reports/" + report).header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(report))
                .andExpect(jsonPath("$.data.currentTargetState").value("visible"))
                .andExpect(jsonPath("$.data.canResolve").value(true));
    }

    @Test
    void missing_report_returns_404() throws Exception {
        AccountToken admin = activeAdmin("report-missing");
        mockMvc.perform(get("/api/admin/reports/999999").header("Authorization", bearer(admin.token())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN_TARGET_NOT_FOUND"));
    }

    @Test
    void report_list_is_paginated_and_stably_sorted() throws Exception {
        AccountToken admin = activeAdmin("report-page-admin");
        AccountToken reporter = signupUser("report-page-user");
        List<Long> reports = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long post = createPost(admin.id(), "post-" + i, "content", "public", "draft");
            reports.add(createReport(reporter.id(), post, "spam", "r-" + i));
        }
        mockMvc.perform(get("/api/admin/reports")
                        .header("Authorization", bearer(admin.token()))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].reportId").value(reports.get(2)))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void admin_can_resolve_pending_report_and_write_audit() throws Exception {
        ReportFixture fixture = reportFixture("resolve");
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "valid report")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("resolved"))
                .andExpect(jsonPath("$.data.changed").value(true));
        assertThat(reportStatus(fixture.reportId())).isEqualTo("resolved");
        assertThat(auditCount("report_resolve", fixture.reportId())).isEqualTo(1);
    }

    @Test
    void admin_can_dismiss_pending_report() throws Exception {
        ReportFixture fixture = reportFixture("dismiss");
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/dismiss", "not a violation")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("rejected"));
        assertThat(reportStatus(fixture.reportId())).isEqualTo("rejected");
    }

    @Test
    void resolved_report_cannot_be_dismissed() throws Exception {
        ReportFixture fixture = reportFixture("resolved-conflict");
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "resolve")
                .andExpect(status().isOk());
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/dismiss", "dismiss")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_STATE_CONFLICT"));
    }

    @Test
    void dismissed_report_cannot_be_resolved() throws Exception {
        ReportFixture fixture = reportFixture("dismissed-conflict");
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/dismiss", "dismiss")
                .andExpect(status().isOk());
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "resolve")
                .andExpect(status().isConflict());
    }

    @Test
    void same_report_terminal_command_is_idempotent_without_duplicate_audit() throws Exception {
        ReportFixture fixture = reportFixture("report-idempotent");
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "resolve")
                .andExpect(status().isOk());
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "resolve again")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false));
        assertThat(auditCount("report_resolve", fixture.reportId())).isEqualTo(1);
    }

    @Test
    void report_command_requires_reason_and_is_atomic() throws Exception {
        ReportFixture fixture = reportFixture("report-reason");
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_COMMAND"));
        assertThat(reportStatus(fixture.reportId())).isEqualTo("pending");
        assertThat(auditCount("report_resolve", fixture.reportId())).isZero();
    }

    @Test
    void concurrent_report_commands_do_not_corrupt_state() throws Exception {
        ReportFixture fixture = reportFixture("report-race");
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> resolve = () -> concurrentCommand(
                    start, fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "resolve");
            Callable<Integer> dismiss = () -> concurrentCommand(
                    start, fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/dismiss", "dismiss");
            Future<Integer> first = executor.submit(resolve);
            Future<Integer> second = executor.submit(dismiss);
            start.countDown();
            List<Integer> statuses = List.of(first.get(), second.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(reportStatus(fixture.reportId())).isIn("resolved", "rejected");
        assertThat(auditCountForTarget("report", fixture.reportId())).isEqualTo(1);
    }

    @Test
    void admin_can_list_filter_and_get_posts() throws Exception {
        AccountToken admin = activeAdmin("post-query-admin");
        long visible = createPost(admin.id(), "visible post", "body", "public", "draft");
        long hidden = createPost(admin.id(), "hidden post", "body", "private", "draft");
        command(admin, "/api/admin/posts/" + hidden + "/hide", "hide").andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/posts")
                        .header("Authorization", bearer(admin.token()))
                        .param("moderationStatus", "hidden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].postId").value(hidden));
        mockMvc.perform(get("/api/admin/posts/" + visible).header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(visible));
    }

    @Test
    void missing_post_returns_404() throws Exception {
        AccountToken admin = activeAdmin("post-missing");
        mockMvc.perform(get("/api/admin/posts/999999").header("Authorization", bearer(admin.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void post_list_is_paginated_and_stably_sorted() throws Exception {
        AccountToken admin = activeAdmin("post-page");
        long first = createPost(admin.id(), "first", "body", "public", "draft");
        long second = createPost(admin.id(), "second", "body", "public", "draft");
        mockMvc.perform(get("/api/admin/posts")
                        .header("Authorization", bearer(admin.token()))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value(second))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void admin_can_hide_and_restore_post_without_physical_delete() throws Exception {
        AccountToken admin = activeAdmin("post-command");
        long post = createPost(admin.id(), "post", "body", "public", "draft");
        command(admin, "/api/admin/posts/" + post + "/hide", "hide reason")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("hidden"));
        assertThat(postExists(post)).isTrue();
        assertThat(auditCount("post_hide", post)).isEqualTo(1);
        command(admin, "/api/admin/posts/" + post + "/restore", "restore reason")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("visible"));
        assertThat(auditCount("post_restore", post)).isEqualTo(1);
    }

    @Test
    void post_commands_require_reason_and_are_atomic() throws Exception {
        AccountToken admin = activeAdmin("post-reason");
        long post = createPost(admin.id(), "post", "body", "public", "draft");
        command(admin, "/api/admin/posts/" + post + "/hide", "")
                .andExpect(status().isBadRequest());
        assertThat(postModerationStatus(post)).isEqualTo("visible");
        assertThat(auditCount("post_hide", post)).isZero();
    }

    @Test
    void repeated_post_command_is_idempotent_without_duplicate_audit() throws Exception {
        AccountToken admin = activeAdmin("post-idempotent");
        long post = createPost(admin.id(), "post", "body", "public", "draft");
        command(admin, "/api/admin/posts/" + post + "/hide", "hide").andExpect(status().isOk());
        command(admin, "/api/admin/posts/" + post + "/hide", "hide again")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false));
        assertThat(auditCount("post_hide", post)).isEqualTo(1);
    }

    @Test
    void admin_can_list_filter_and_get_users_without_sensitive_fields() throws Exception {
        AccountToken admin = activeAdmin("user-query-admin");
        AccountToken user = signupUser("user-query-target");
        setStatus(user.id(), "suspended");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(admin.token()))
                        .param("accountStatus", "suspended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(user.id()))
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("refreshToken"))));
        mockMvc.perform(get("/api/admin/users/" + user.id()).header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("suspended"));
    }

    @Test
    void missing_user_returns_404() throws Exception {
        AccountToken admin = activeAdmin("user-missing");
        mockMvc.perform(get("/api/admin/users/999999").header("Authorization", bearer(admin.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void user_list_is_paginated_and_stably_sorted() throws Exception {
        AccountToken admin = activeAdmin("user-page-admin");
        AccountToken first = signupUser("user-page-first");
        AccountToken second = signupUser("user-page-second");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(admin.token()))
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(second.id()))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        assertThat(second.id()).isGreaterThan(first.id());
    }

    @Test
    void admin_can_suspend_and_unsuspend_user_with_audit() throws Exception {
        AccountToken admin = activeAdmin("user-command-admin");
        AccountToken user = signupUser("user-command-target");
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "suspend")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("suspended"));
        assertThat(userStatus(user.id())).isEqualTo("suspended");
        assertThat(auditCount("user_suspend", user.id())).isEqualTo(1);
        command(admin, "/api/admin/users/" + user.id() + "/unsuspend", "unsuspend")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("active"));
        assertThat(auditCount("user_restore", user.id())).isEqualTo(1);
    }

    @Test
    void admin_cannot_suspend_self() throws Exception {
        AccountToken admin = activeAdmin("self-suspend");
        command(admin, "/api/admin/users/" + admin.id() + "/suspend", "self")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_STATE_CONFLICT"));
        assertThat(userStatus(admin.id())).isEqualTo("active");
    }

    @Test
    void withdrawn_user_cannot_be_unsuspended() throws Exception {
        AccountToken admin = activeAdmin("withdrawn-admin");
        AccountToken user = signupUser("withdrawn-target");
        setStatus(user.id(), "withdrawn");
        command(admin, "/api/admin/users/" + user.id() + "/unsuspend", "restore")
                .andExpect(status().isConflict());
        assertThat(userStatus(user.id())).isEqualTo("withdrawn");
    }

    @Test
    void user_commands_require_reason_and_are_atomic() throws Exception {
        AccountToken admin = activeAdmin("user-reason-admin");
        AccountToken user = signupUser("user-reason-target");
        command(admin, "/api/admin/users/" + user.id() + "/suspend", " ")
                .andExpect(status().isBadRequest());
        assertThat(userStatus(user.id())).isEqualTo("active");
        assertThat(auditCount("user_suspend", user.id())).isZero();
    }

    @Test
    void suspend_invalidates_db_authoritative_admin_access() throws Exception {
        AccountToken actor = activeAdmin("suspending-admin");
        AccountToken target = activeAdmin("suspended-target-admin");
        command(actor, "/api/admin/users/" + target.id() + "/suspend", "security")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(target.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void repeated_user_command_is_idempotent_without_duplicate_audit() throws Exception {
        AccountToken admin = activeAdmin("user-idempotent-admin");
        AccountToken user = signupUser("user-idempotent-target");
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "suspend").andExpect(status().isOk());
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "again")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed").value(false));
        assertThat(auditCount("user_suspend", user.id())).isEqualTo(1);
    }

    @Test
    void unbounded_admin_query_is_rejected() throws Exception {
        AccountToken admin = activeAdmin("bounded");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(admin.token()))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_COMMAND"));
    }

    private List<String> getPaths() {
        return List.of(
                "/api/admin/dashboard",
                "/api/admin/reports",
                "/api/admin/reports/1",
                "/api/admin/posts",
                "/api/admin/posts/1",
                "/api/admin/users",
                "/api/admin/users/1");
    }

    private List<String> postPaths() {
        return List.of(
                "/api/admin/reports/1/resolve",
                "/api/admin/reports/1/dismiss",
                "/api/admin/posts/1/hide",
                "/api/admin/posts/1/restore",
                "/api/admin/users/1/suspend",
                "/api/admin/users/1/unsuspend");
    }

    private org.springframework.test.web.servlet.ResultActions command(
            AccountToken admin,
            String path,
            String reason) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", bearer(admin.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason(reason)));
    }

    private int concurrentCommand(
            CountDownLatch start,
            AccountToken admin,
            String path,
            String reason) throws Exception {
        start.await();
        MvcResult result = mockMvc.perform(post(path)
                        .header("Authorization", bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason(reason)))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private ReportFixture reportFixture(String prefix) {
        AccountToken admin = activeAdmin(prefix + "-admin");
        AccountToken reporter = signupUser(prefix + "-reporter");
        long post = createPost(admin.id(), prefix + "-post", "body", "public", "draft");
        return new ReportFixture(admin, createReport(reporter.id(), post, "spam", prefix));
    }

    private AccountToken signupUser(String prefix) {
        String suffix = prefix + "-" + SEQUENCE.incrementAndGet();
        AuthDtos.TokenResponse response = authService.signup(new AuthDtos.SignupRequest(
                suffix + "@example.test", PASSWORD, suffix));
        return new AccountToken(response.user().id(), response.accessToken());
    }

    private AccountToken activeAdmin(String prefix) {
        String suffix = prefix + "-" + SEQUENCE.incrementAndGet();
        String email = suffix + "@example.test";
        AuthDtos.TokenResponse signup = authService.signup(new AuthDtos.SignupRequest(email, PASSWORD, suffix));
        setRole(signup.user().id(), "admin");
        AuthDtos.TokenResponse login = authService.login(new AuthDtos.LoginRequest(email, PASSWORD));
        return new AccountToken(signup.user().id(), login.accessToken());
    }


    private long createPublishedPost(long authorId, String title, String body) {
        Long regionId = jdbc.queryForObject(
                "select id from public.regions where is_active = true order by id limit 1",
                Long.class);
        Long placeId = jdbc.queryForObject(
                "insert into public.places(region_id, name_local, created_by_user_id) values (?, ?, ?) returning id",
                Long.class,
                regionId,
                "place-" + SEQUENCE.incrementAndGet(),
                authorId);
        Long postId = jdbc.queryForObject(
                "insert into public.posts(author_id, main_region_id, title, content, visibility, status) "
                        + "values (?, ?, ?, ?, 'public', 'draft') returning id",
                Long.class,
                authorId,
                regionId,
                title,
                body);
        jdbc.update(
                "insert into public.post_places(post_id, place_id, sort_order) values (?, ?, 0)",
                postId,
                placeId);
        jdbc.update(
                "update public.posts set status = 'published', published_at = current_timestamp where id = ?",
                postId);
        return postId;
    }

    private long createPost(
            long authorId,
            String title,
            String body,
            String visibility,
            String status) {
        Long id = jdbc.queryForObject(
                "insert into public.posts(author_id, title, content, visibility, status) "
                        + "values (?, ?, ?, ?, ?) returning id",
                Long.class,
                authorId,
                title,
                body,
                visibility,
                status);
        return id;
    }

    private long createReport(
            long reporterId,
            long postId,
            String category,
            String detail) {
        String snapshot = "{\"type\":\"post\",\"id\":" + postId + "}";
        Long id = jdbc.queryForObject(
                "insert into public.reports(reporter_id, target_type, target_entity_id, target_post_id, "
                        + "target_snapshot, reason_category, reason_detail) "
                        + "values (?, 'post', ?, ?, cast(? as jsonb), ?, ?) returning id",
                Long.class,
                reporterId,
                postId,
                postId,
                snapshot,
                category,
                detail);
        return id;
    }

    private void setRole(long userId, String role) {
        jdbc.update("update public.app_users set role = ? where id = ?", role, userId);
    }

    private void setStatus(long userId, String status) {
        jdbc.update("update public.app_users set account_status = ? where id = ?", status, userId);
    }

    private String reportStatus(long reportId) {
        return jdbc.queryForObject("select status from public.reports where id = ?", String.class, reportId);
    }

    private String postModerationStatus(long postId) {
        return jdbc.queryForObject(
                "select moderation_status from public.posts where id = ?", String.class, postId);
    }

    private String userStatus(long userId) {
        return jdbc.queryForObject(
                "select account_status from public.app_users where id = ?", String.class, userId);
    }

    private boolean postExists(long postId) {
        Long count = jdbc.queryForObject("select count(*) from public.posts where id = ?", Long.class, postId);
        return count != null && count == 1;
    }

    private long auditCount(String actionType, long targetId) {
        Long count = jdbc.queryForObject(
                "select count(*) from public.admin_actions where action_type = ? and target_entity_id = ?",
                Long.class,
                actionType,
                targetId);
        return count == null ? 0 : count;
    }

    private long auditCountForTarget(String targetType, long targetId) {
        Long count = jdbc.queryForObject(
                "select count(*) from public.admin_actions where target_type = ? and target_entity_id = ?",
                Long.class,
                targetType,
                targetId);
        return count == null ? 0 : count;
    }

    private String reason(String value) {
        return "{\"reason\":\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AccountToken(long id, String token) {}

    private record ReportFixture(AccountToken admin, long reportId) {}
}
