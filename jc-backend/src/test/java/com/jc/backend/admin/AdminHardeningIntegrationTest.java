package com.jc.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.admin.security.AdminAuthorizationGuard;
import com.jc.backend.auth.AuthDtos;
import com.jc.backend.auth.AuthService;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@Import(AdminHardeningIntegrationTest.HardeningTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminHardeningIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final int CONTROL_LOCK_KEY_1 = 1_245_789;
    private static final int CONTROL_LOCK_KEY_2 = 3;
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private ForcedRollbackProbe forcedRollbackProbe;

    @AfterEach
    void cleanupTestState() {
        SecurityContextHolder.clearContext();
        dropFailureObjects();
    }

    @Test
    void admin_cannot_suspend_self() throws Exception {
        AccountToken admin = activeAdmin("self");
        command(admin, "/api/admin/users/" + admin.id() + "/suspend", "self")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_STATE_CONFLICT"));
        assertThat(userStatus(admin.id())).isEqualTo("active");
        assertThat(auditCount("user_suspend", admin.id())).isZero();
    }

    @Test
    void last_active_admin_cannot_be_suspended() throws Exception {
        AccountToken target = activeAdmin("last-admin");
        AccountToken moderator = signupUser("moderator");
        setRole(moderator.id(), "moderator");

        assertThatThrownBy(() -> callAdminSuspendAs(moderator.id(), target.id(), "protect last admin"))
                .isInstanceOf(SQLException.class);
        assertThat(userStatus(target.id())).isEqualTo("active");
        assertThat(auditCount("user_suspend", target.id())).isZero();
    }

    @Test
    void concurrent_cross_admin_suspend_does_not_lock_out_all_admins() throws Exception {
        AccountToken adminA = activeAdmin("cross-a");
        AccountToken adminB = activeAdmin("cross-b");

        try (Connection control = holdControlPlaneLock();
                ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CommandOutcome> a = executor.submit(commandTask(
                    start, adminA, "/api/admin/users/" + adminB.id() + "/suspend", "a suspends b"));
            Future<CommandOutcome> b = executor.submit(commandTask(
                    start, adminB, "/api/admin/users/" + adminA.id() + "/suspend", "b suspends a"));
            start.countDown();
            awaitAdvisoryWaiters(2);
            releaseControlPlaneLock(control);

            List<CommandOutcome> outcomes = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(CommandOutcome::status).containsExactlyInAnyOrder(200, 409);
        }

        long activeAdmins = count("select count(*) from public.app_users where role='admin' and account_status='active'");
        long suspendedAdmins = count("select count(*) from public.app_users where role='admin' and account_status='suspended'");
        assertThat(activeAdmins).isEqualTo(1);
        assertThat(suspendedAdmins).isEqualTo(1);
        assertThat(count("select count(*) from public.admin_actions where action_type='user_suspend'")).isEqualTo(1);
    }

    @Test
    void suspended_actor_cannot_commit_admin_command() throws Exception {
        assertActorRecheckAfterLock("suspended-actor");
    }

    @Test
    void actor_state_is_rechecked_after_lock() throws Exception {
        assertActorRecheckAfterLock("actor-recheck");
    }

    @Test
    void audit_failure_rolls_back_report_mutation() throws Exception {
        ReportFixture fixture = reportFixture("audit-report");
        installAuditFailureTrigger();
        command(fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "audit failure")
                .andExpect(status().isConflict());
        assertThat(reportStatus(fixture.reportId())).isEqualTo("pending");
        assertThat(auditCount("report_resolve", fixture.reportId())).isZero();
    }

    @Test
    void audit_failure_rolls_back_post_mutation() throws Exception {
        AccountToken admin = activeAdmin("audit-post-admin");
        long postId = createPost(admin.id(), "audit post", "body");
        installAuditFailureTrigger();
        command(admin, "/api/admin/posts/" + postId + "/hide", "audit failure")
                .andExpect(status().isConflict());
        assertThat(postModerationStatus(postId)).isEqualTo("visible");
        assertThat(auditCount("post_hide", postId)).isZero();
    }

    @Test
    void audit_failure_rolls_back_user_mutation() throws Exception {
        AccountToken admin = activeAdmin("audit-user-admin");
        AccountToken user = signupUser("audit-user-target");
        installAuditFailureTrigger();
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "audit failure")
                .andExpect(status().isConflict());
        assertThat(userStatus(user.id())).isEqualTo("active");
        assertThat(auditCount("user_suspend", user.id())).isZero();
    }

    @Test
    void mutation_failure_creates_no_audit() throws Exception {
        AccountToken admin = activeAdmin("mutation-admin");
        long postId = createPost(admin.id(), "mutation fail", "body");
        installPostMutationFailureTrigger(postId);
        command(admin, "/api/admin/posts/" + postId + "/hide", "mutation failure")
                .andExpect(status().isConflict());
        assertThat(postModerationStatus(postId)).isEqualTo("visible");
        assertThat(auditCount("post_hide", postId)).isZero();
    }

    @Test
    void forced_exception_rolls_back_transaction() {
        AccountToken admin = activeAdmin("forced-admin");
        long postId = createPost(admin.id(), "forced rollback", "body");
        authenticate(admin.id(), "admin");

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(admin.id())) {
            assertThatThrownBy(() -> forcedRollbackProbe.hideThenFail(postId, "forced rollback"))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(postModerationStatus(postId)).isEqualTo("visible");
        assertThat(auditCount("post_hide", postId)).isZero();
    }

    @Test
    void concurrent_same_report_command_is_idempotent() throws Exception {
        ReportFixture fixture = reportFixture("same-report");
        try (Connection lock = lockRow("reports", fixture.reportId());
                ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CommandOutcome> one = executor.submit(commandTask(
                    start, fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "same one"));
            Future<CommandOutcome> two = executor.submit(commandTask(
                    start, fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "same two"));
            start.countDown();
            awaitFunctionWaiters("admin_finish_report", 2);
            lock.commit();
            List<CommandOutcome> outcomes = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(CommandOutcome::status).containsOnly(200);
            assertThat(outcomes).extracting(CommandOutcome::changed).containsExactlyInAnyOrder(true, false);
        }
        assertThat(reportStatus(fixture.reportId())).isEqualTo("resolved");
        assertThat(auditCount("report_resolve", fixture.reportId())).isEqualTo(1);
    }

    @Test
    void concurrent_conflicting_report_commands_yield_one_conflict() throws Exception {
        ReportFixture fixture = reportFixture("conflicting-report");
        try (Connection lock = lockRow("reports", fixture.reportId());
                ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CommandOutcome> resolve = executor.submit(commandTask(
                    start, fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/resolve", "resolve"));
            Future<CommandOutcome> dismiss = executor.submit(commandTask(
                    start, fixture.admin(), "/api/admin/reports/" + fixture.reportId() + "/dismiss", "dismiss"));
            start.countDown();
            awaitFunctionWaiters("admin_finish_report", 2);
            lock.commit();
            List<CommandOutcome> outcomes = List.of(resolve.get(15, TimeUnit.SECONDS), dismiss.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(CommandOutcome::status).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(reportStatus(fixture.reportId())).isIn("resolved", "rejected");
        assertThat(auditCountForTarget("report", fixture.reportId())).isEqualTo(1);
    }

    @Test
    void concurrent_same_post_command_has_single_audit() throws Exception {
        AccountToken admin = activeAdmin("same-post-admin");
        long postId = createPost(admin.id(), "same post", "body");
        try (Connection lock = lockRow("posts", postId);
                ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CommandOutcome> one = executor.submit(commandTask(
                    start, admin, "/api/admin/posts/" + postId + "/hide", "hide one"));
            Future<CommandOutcome> two = executor.submit(commandTask(
                    start, admin, "/api/admin/posts/" + postId + "/hide", "hide two"));
            start.countDown();
            awaitFunctionWaiters("admin_hide_post", 2);
            lock.commit();
            List<CommandOutcome> outcomes = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(CommandOutcome::status).containsOnly(200);
            assertThat(outcomes).extracting(CommandOutcome::changed).containsExactlyInAnyOrder(true, false);
        }
        assertThat(postModerationStatus(postId)).isEqualTo("hidden");
        assertThat(auditCount("post_hide", postId)).isEqualTo(1);
    }

    @Test
    void concurrent_hide_restore_preserves_valid_state() throws Exception {
        AccountToken admin = activeAdmin("hide-restore-admin");
        long postId = createPost(admin.id(), "hide restore", "body");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CommandOutcome> hide = executor.submit(commandTask(
                    start, admin, "/api/admin/posts/" + postId + "/hide", "hide"));
            Future<CommandOutcome> restore = executor.submit(commandTask(
                    start, admin, "/api/admin/posts/" + postId + "/restore", "restore"));
            start.countDown();
            List<CommandOutcome> outcomes = List.of(hide.get(15, TimeUnit.SECONDS), restore.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(CommandOutcome::status).allMatch(status -> status == 200 || status == 409);
        }
        assertThat(postModerationStatus(postId)).isIn("visible", "hidden");
        assertThat(postExists(postId)).isTrue();
        assertThat(auditCount("post_hide", postId)).isLessThanOrEqualTo(1);
        assertThat(auditCount("post_restore", postId)).isLessThanOrEqualTo(1);
    }

    @Test
    void concurrent_same_user_command_has_single_audit() throws Exception {
        AccountToken admin = activeAdmin("same-user-admin");
        AccountToken user = signupUser("same-user-target");
        try (Connection lock = lockRow("app_users", user.id());
                ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CommandOutcome> one = executor.submit(commandTask(
                    start, admin, "/api/admin/users/" + user.id() + "/suspend", "suspend one"));
            Future<CommandOutcome> two = executor.submit(commandTask(
                    start, admin, "/api/admin/users/" + user.id() + "/suspend", "suspend two"));
            start.countDown();
            awaitFunctionWaiters("admin_suspend_user", 2);
            lock.commit();
            List<CommandOutcome> outcomes = List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(CommandOutcome::status).containsOnly(200);
            assertThat(outcomes).extracting(CommandOutcome::changed).containsExactlyInAnyOrder(true, false);
        }
        assertThat(userStatus(user.id())).isEqualTo("suspended");
        assertThat(auditCount("user_suspend", user.id())).isEqualTo(1);
    }

    @Test
    void concurrent_suspend_unsuspend_preserves_valid_state() throws Exception {
        AccountToken admin = activeAdmin("suspend-restore-admin");
        AccountToken user = signupUser("suspend-restore-target");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CommandOutcome> suspend = executor.submit(commandTask(
                    start, admin, "/api/admin/users/" + user.id() + "/suspend", "suspend"));
            Future<CommandOutcome> unsuspend = executor.submit(commandTask(
                    start, admin, "/api/admin/users/" + user.id() + "/unsuspend", "unsuspend"));
            start.countDown();
            List<CommandOutcome> outcomes = List.of(suspend.get(15, TimeUnit.SECONDS), unsuspend.get(15, TimeUnit.SECONDS));
            assertThat(outcomes).extracting(CommandOutcome::status).allMatch(status -> status == 200 || status == 409);
        }
        assertThat(userStatus(user.id())).isIn("active", "suspended");
        assertThat(auditCount("user_suspend", user.id())).isLessThanOrEqualTo(1);
        assertThat(auditCount("user_restore", user.id())).isLessThanOrEqualTo(1);
    }

    @Test
    void dashboard_response_contains_only_allowed_fields() throws Exception {
        AccountToken admin = activeAdmin("privacy-dashboard");
        JsonNode data = data(getJson(admin, "/api/admin/dashboard"));
        assertFields(data, "totalUsers", "activePostCount", "pendingReportCount", "suspendedUserCount", "recentReports", "recentAdminActions");
    }

    @Test
    void report_list_response_contains_only_allowed_fields() throws Exception {
        ReportFixture fixture = reportFixture("privacy-report-list");
        JsonNode item = data(getJson(fixture.admin(), "/api/admin/reports")).path("items").get(0);
        assertFields(item, "reportId", "reporterId", "reporterUsername", "targetType", "targetId", "reasonCategory", "reasonDetail", "status", "createdAt", "handledAt");
    }

    @Test
    void report_detail_response_contains_only_allowed_fields() throws Exception {
        ReportFixture fixture = reportFixture("privacy-report-detail");
        JsonNode item = data(getJson(fixture.admin(), "/api/admin/reports/" + fixture.reportId()));
        assertFields(item, "reportId", "reporterId", "reporterUsername", "reporterDisplayName", "targetType", "targetId", "reasonCategory", "reasonDetail", "status", "createdAt", "handledAt", "resolutionNote", "currentTargetState", "canResolve", "canDismiss");
    }

    @Test
    void post_list_response_contains_only_allowed_fields() throws Exception {
        AccountToken admin = activeAdmin("privacy-post-list");
        createPost(admin.id(), "privacy", "body");
        JsonNode item = data(getJson(admin, "/api/admin/posts")).path("items").get(0);
        assertFields(item, "postId", "authorId", "authorDisplayName", "title", "contentPreview", "contentTruncated", "visibility", "contentStatus", "moderationStatus", "createdAt", "updatedAt", "hiddenAt", "deletedAt", "purgeAfter");
    }

    @Test
    void post_detail_response_contains_only_allowed_fields() throws Exception {
        AccountToken admin = activeAdmin("privacy-post-detail");
        long postId = createPost(admin.id(), "privacy", "body");
        JsonNode item = data(getJson(admin, "/api/admin/posts/" + postId));
        assertFields(item, "postId", "authorId", "authorUsername", "authorDisplayName", "title", "contentPreview", "contentTruncated", "visibility", "contentStatus", "moderationStatus", "createdAt", "updatedAt", "hiddenAt", "deletedAt", "purgeAfter");
    }

    @Test
    void user_list_response_contains_only_allowed_fields() throws Exception {
        AccountToken admin = activeAdmin("privacy-user-list");
        JsonNode item = data(getJson(admin, "/api/admin/users")).path("items").get(0);
        assertFields(item, "userId", "email", "username", "displayName", "role", "accountStatus", "createdAt", "suspendedAt");
    }

    @Test
    void user_detail_response_contains_only_allowed_fields() throws Exception {
        AccountToken admin = activeAdmin("privacy-user-detail");
        JsonNode item = data(getJson(admin, "/api/admin/users/" + admin.id()));
        assertFields(item, "userId", "email", "username", "displayName", "role", "accountStatus", "createdAt", "updatedAt", "suspendedAt");
    }

    @Test
    void error_response_does_not_expose_internal_fields() throws Exception {
        AccountToken admin = activeAdmin("privacy-error");
        MvcResult result = mockMvc.perform(get("/api/admin/users/not-a-number")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_COMMAND"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body.toLowerCase(Locale.ROOT))
                .doesNotContain("sqlstate", "jc_admin", "admin_suspend_user", "relation", "column", "stacktrace", admin.id() + "");
        assertFields(objectMapper.readTree(body), "success", "code", "message", "errors");
    }

    @Test
    void audit_snapshot_does_not_contain_secrets() throws Exception {
        AccountToken admin = activeAdmin("privacy-audit-admin");
        AccountToken user = signupUser("privacy-audit-user");
        long postId = createPost(admin.id(), "privacy audit", "body");
        command(admin, "/api/admin/posts/" + postId + "/hide", "policy violation").andExpect(status().isOk());
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "policy violation").andExpect(status().isOk());
        List<String> rows = jdbc.query(
                "select target_snapshot::text || metadata::text from public.admin_actions",
                (rs, row) -> rs.getString(1));
        assertThat(rows).isNotEmpty();
        for (String row : rows) {
            assertThat(row.toLowerCase(Locale.ROOT)).doesNotContain(
                    "password_hash", "refresh_token", "access_token", "authorization", "cookie", "oauth_secret", "database_password", "jwt");
        }
    }

    @Test
    void negative_page_rejected() throws Exception {
        assertInvalidQuery("/api/admin/users?page=-1");
    }

    @Test
    void oversized_page_size_rejected() throws Exception {
        assertInvalidQuery("/api/admin/users?size=101");
    }

    @Test
    void oversized_search_rejected() throws Exception {
        assertInvalidQuery("/api/admin/users?search=" + "a".repeat(101));
    }

    @Test
    void blank_reason_rejected() throws Exception {
        AccountToken admin = activeAdmin("blank-reason-admin");
        AccountToken user = signupUser("blank-reason-user");
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_COMMAND"));
    }

    @Test
    void oversized_reason_rejected() throws Exception {
        AccountToken admin = activeAdmin("long-reason-admin");
        AccountToken user = signupUser("long-reason-user");
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "a".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_COMMAND"));
    }

    @Test
    void unsupported_filter_rejected() throws Exception {
        assertInvalidQuery("/api/admin/users?sort=password_hash");
    }

    @Test
    void duplicate_query_parameter_rejected() throws Exception {
        assertInvalidQuery("/api/admin/users?role=user&role=admin");
    }

    @Test
    void secret_material_in_reason_rejected() throws Exception {
        AccountToken admin = activeAdmin("secret-reason-admin");
        AccountToken user = signupUser("secret-reason-user");
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "Authorization: Bearer abcdefghijklmnopqrstuvwxyz")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_COMMAND"));
        assertThat(auditCount("user_suspend", user.id())).isZero();
    }

    @Test
    void moderator_returns_403() throws Exception {
        AccountToken moderator = signupUser("security-moderator");
        setRole(moderator.id(), "moderator");
        AccountToken token = login(moderator.email());
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(token.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void withdrawn_admin_returns_403() throws Exception {
        AccountToken admin = activeAdmin("security-withdrawn");
        setStatus(admin.id(), "withdrawn");
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void promoted_user_requires_new_token() throws Exception {
        AccountToken user = signupUser("security-promoted");
        setRole(user.id(), "admin");
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(user.token())))
                .andExpect(status().isForbidden());
        AccountToken refreshed = login(user.email());
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(refreshed.token())))
                .andExpect(status().isOk());
    }

    @Test
    void demoted_admin_loses_access_with_existing_token() throws Exception {
        AccountToken admin = activeAdmin("security-demoted");
        setRole(admin.id(), "user");
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspended_admin_loses_access_with_existing_token() throws Exception {
        AccountToken admin = activeAdmin("security-suspended");
        setStatus(admin.id(), "suspended");
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(admin.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_mvp_end_to_end_acceptance_passes() throws Exception {
        AccountToken admin = activeAdmin("accept-admin");
        AccountToken user = signupUser("accept-user");
        long postId = createPost(admin.id(), "accept post", "accept body");
        long reportId = createReport(user.id(), postId, "spam", "accept report");

        adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk());
        adminGet(admin, "/api/admin/reports?status=pending").andExpect(status().isOk());
        adminGet(admin, "/api/admin/reports/" + reportId).andExpect(status().isOk());
        adminGet(admin, "/api/admin/posts/" + postId).andExpect(status().isOk());
        command(admin, "/api/admin/posts/" + postId + "/hide", "accept hide")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        command(admin, "/api/admin/reports/" + reportId + "/resolve", "accept resolve")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        adminGet(admin, "/api/admin/users/" + user.id()).andExpect(status().isOk());
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "accept suspend")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(true));
        adminGet(admin, "/api/admin/dashboard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingReportCount").value(0))
                .andExpect(jsonPath("$.data.suspendedUserCount").value(1));

        assertThat(auditCount("post_hide", postId)).isEqualTo(1);
        assertThat(auditCount("report_resolve", reportId)).isEqualTo(1);
        assertThat(auditCount("user_suspend", user.id())).isEqualTo(1);

        command(admin, "/api/admin/posts/" + postId + "/hide", "accept retry")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        command(admin, "/api/admin/reports/" + reportId + "/resolve", "accept retry")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));
        command(admin, "/api/admin/users/" + user.id() + "/suspend", "accept retry")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.changed").value(false));

        command(admin, "/api/admin/posts/" + postId + "/restore", "accept restore").andExpect(status().isOk());
        command(admin, "/api/admin/users/" + user.id() + "/unsuspend", "accept unsuspend").andExpect(status().isOk());

        assertThat(postExists(postId)).isTrue();
        assertThat(postModerationStatus(postId)).isEqualTo("visible");
        assertThat(userStatus(user.id())).isEqualTo("active");
        assertThat(reportStatus(reportId)).isEqualTo("resolved");
        assertThat(count("select count(*) from public.admin_actions")).isEqualTo(5);
    }

    @Test
    void normal_user_cannot_execute_acceptance_flow() throws Exception {
        AccountToken user = signupUser("accept-normal");
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(user.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void suspended_admin_loses_access_mid_flow() throws Exception {
        AccountToken admin = activeAdmin("accept-mid-flow");
        adminGet(admin, "/api/admin/dashboard").andExpect(status().isOk());
        setStatus(admin.id(), "suspended");
        adminGet(admin, "/api/admin/reports")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void all_mutations_create_exactly_one_audit() throws Exception {
        admin_mvp_end_to_end_acceptance_passes();
    }

    @Test
    void physical_delete_never_occurs() throws Exception {
        AccountToken admin = activeAdmin("physical-delete-admin");
        long postId = createPost(admin.id(), "retained", "body");
        command(admin, "/api/admin/posts/" + postId + "/hide", "hide only").andExpect(status().isOk());
        assertThat(postExists(postId)).isTrue();
        assertThat(postModerationStatus(postId)).isEqualTo("hidden");
    }

    private void assertActorRecheckAfterLock(String prefix) throws Exception {
        AccountToken actor = activeAdmin(prefix + "-admin");
        AccountToken target = signupUser(prefix + "-target");
        try (Connection control = holdControlPlaneLock();
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Future<CommandOutcome> future = executor.submit(commandTask(
                    start, actor, "/api/admin/users/" + target.id() + "/suspend", "recheck"));
            start.countDown();
            awaitAdvisoryWaiters(1);
            setStatus(actor.id(), "suspended");
            releaseControlPlaneLock(control);
            CommandOutcome outcome = future.get(15, TimeUnit.SECONDS);
            assertThat(outcome.status()).isEqualTo(409);
        }
        assertThat(userStatus(target.id())).isEqualTo("active");
        assertThat(auditCount("user_suspend", target.id())).isZero();
    }

    private Callable<CommandOutcome> commandTask(
            CountDownLatch start,
            AccountToken admin,
            String path,
            String reason) {
        return () -> {
            start.await();
            MvcResult result = mockMvc.perform(post(path)
                            .header("Authorization", bearer(admin.token()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reason(reason)))
                    .andReturn();
            JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
            boolean changed = data.has("changed") && data.path("changed").asBoolean();
            return new CommandOutcome(result.getResponse().getStatus(), changed);
        };
    }

    private Connection holdControlPlaneLock() throws SQLException {
        Connection connection = dataSource.getConnection();
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_lock(?, ?)")) {
            statement.setInt(1, CONTROL_LOCK_KEY_1);
            statement.setInt(2, CONTROL_LOCK_KEY_2);
            statement.execute();
        }
        return connection;
    }

    private void releaseControlPlaneLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?, ?)")) {
            statement.setInt(1, CONTROL_LOCK_KEY_1);
            statement.setInt(2, CONTROL_LOCK_KEY_2);
            statement.execute();
        }
    }

    private void awaitAdvisoryWaiters(int expected) {
        awaitCondition(() -> count("select count(*) from pg_locks where locktype='advisory' "
                + "and classid=" + CONTROL_LOCK_KEY_1 + " and objid=" + CONTROL_LOCK_KEY_2 + " and not granted") >= expected,
                "advisory lock waiters=" + expected);
    }

    private void awaitFunctionWaiters(String function, int expected) {
        awaitCondition(() -> {
            Long value = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where state='active' and wait_event_type='Lock' "
                            + "and query ilike ?",
                    Long.class,
                    "%" + function + "%");
            return value != null && value >= expected;
        }, function + " lock waiters=" + expected);
    }

    private void awaitCondition(Callable<Boolean> condition, String description) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        try {
            while (Instant.now().isBefore(deadline)) {
                if (condition.call()) {
                    return;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed while waiting for " + description, exception);
        }
        throw new IllegalStateException("Timed out waiting for " + description);
    }

    private Connection lockRow(String table, long id) throws SQLException {
        if (!Set.of("reports", "posts", "app_users").contains(table)) {
            throw new IllegalArgumentException("Unsupported test table");
        }
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from public." + table + " where id = ? for update")) {
            statement.setLong(1, id);
            try (ResultSet ignored = statement.executeQuery()) {
                assertThat(ignored.next()).isTrue();
            }
        }
        return connection;
    }

    private void callAdminSuspendAs(long actorId, long targetId, String reason) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement role = connection.createStatement()) {
                role.execute("set local role jc_admin");
            }
            try (PreparedStatement identity = connection.prepareStatement("select set_config('jc.current_user_id', ?, true)")) {
                identity.setString(1, Long.toString(actorId));
                identity.execute();
            }
            try (PreparedStatement command = connection.prepareStatement("select public.admin_suspend_user(?, ?)")) {
                command.setLong(1, targetId);
                command.setString(2, reason);
                command.execute();
            } finally {
                connection.rollback();
            }
        }
    }

    private void installAuditFailureTrigger() {
        jdbc.execute("create or replace function public.adm3_fail_audit_insert() returns trigger language plpgsql as $$ "
                + "begin raise exception 'ADM3 controlled audit failure'; end; $$");
        jdbc.execute("create trigger adm3_fail_audit_insert before insert on public.admin_actions "
                + "for each row execute function public.adm3_fail_audit_insert()");
    }

    private void installPostMutationFailureTrigger(long postId) {
        jdbc.execute("create or replace function public.adm3_fail_post_update() returns trigger language plpgsql as $$ "
                + "begin if new.id = " + postId + " then raise exception 'ADM3 controlled mutation failure'; end if; return new; end; $$");
        jdbc.execute("create trigger adm3_fail_post_update before update on public.posts "
                + "for each row execute function public.adm3_fail_post_update()");
    }

    private void dropFailureObjects() {
        jdbc.execute("drop trigger if exists adm3_fail_audit_insert on public.admin_actions");
        jdbc.execute("drop function if exists public.adm3_fail_audit_insert()");
        jdbc.execute("drop trigger if exists adm3_fail_post_update on public.posts");
        jdbc.execute("drop function if exists public.adm3_fail_post_update()");
    }

    private void assertInvalidQuery(String path) throws Exception {
        AccountToken admin = activeAdmin("invalid-query");
        mockMvc.perform(get(path).header("Authorization", bearer(admin.token())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_COMMAND"));
    }

    private JsonNode getJson(AccountToken admin, String path) throws Exception {
        return objectMapper.readTree(adminGet(admin, path).andReturn().getResponse().getContentAsString());
    }

    private JsonNode data(JsonNode response) {
        return response.path("data");
    }

    private void assertFields(JsonNode node, String... fields) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        assertThat(actual).containsExactlyInAnyOrder(fields);
    }

    private org.springframework.test.web.servlet.ResultActions adminGet(AccountToken admin, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(admin.token())));
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

    private ReportFixture reportFixture(String prefix) {
        AccountToken admin = activeAdmin(prefix + "-admin");
        AccountToken reporter = signupUser(prefix + "-reporter");
        long post = createPost(admin.id(), prefix + "-post", "body");
        return new ReportFixture(admin, createReport(reporter.id(), post, "spam", prefix));
    }

    private AccountToken signupUser(String prefix) {
        String suffix = prefix + "-" + SEQUENCE.incrementAndGet();
        String email = suffix + "@example.test";
        AuthDtos.TokenResponse response = authService.signup(new AuthDtos.SignupRequest(email, PASSWORD, suffix));
        return new AccountToken(response.user().id(), email, response.accessToken());
    }

    private AccountToken activeAdmin(String prefix) {
        AccountToken signup = signupUser(prefix);
        setRole(signup.id(), "admin");
        return login(signup.email());
    }

    private AccountToken login(String email) {
        AuthDtos.TokenResponse response = authService.login(new AuthDtos.LoginRequest(email, PASSWORD));
        return new AccountToken(response.user().id(), email, response.accessToken());
    }

    private long createPost(long authorId, String title, String body) {
        Long id = jdbc.queryForObject(
                "insert into public.posts(author_id, title, content, visibility, status) "
                        + "values (?, ?, ?, 'public', 'draft') returning id",
                Long.class,
                authorId,
                title,
                body);
        return id == null ? 0 : id;
    }

    private long createReport(long reporterId, long postId, String category, String detail) {
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
        return id == null ? 0 : id;
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
        return jdbc.queryForObject("select moderation_status from public.posts where id = ?", String.class, postId);
    }

    private String userStatus(long userId) {
        return jdbc.queryForObject("select account_status from public.app_users where id = ?", String.class, userId);
    }

    private boolean postExists(long postId) {
        return count("select count(*) from public.posts where id=" + postId) == 1;
    }

    private long auditCount(String actionType, long targetId) {
        Long value = jdbc.queryForObject(
                "select count(*) from public.admin_actions where action_type = ? and target_entity_id = ?",
                Long.class,
                actionType,
                targetId);
        return value == null ? 0 : value;
    }

    private long auditCountForTarget(String targetType, long targetId) {
        Long value = jdbc.queryForObject(
                "select count(*) from public.admin_actions where target_type = ? and target_entity_id = ?",
                Long.class,
                targetType,
                targetId);
        return value == null ? 0 : value;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private void authenticate(long subject, String role) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(Long.toString(subject))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("role", role)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))));
    }

    private String reason(String value) {
        return "{\"reason\":\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record AccountToken(long id, String email, String token) {}

    private record ReportFixture(AccountToken admin, long reportId) {}

    private record CommandOutcome(int status, boolean changed) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class HardeningTestConfiguration {
        @Bean
        ForcedRollbackProbe forcedRollbackProbe(AdminAuthorizationGuard guard, JdbcTemplate jdbc) {
            return new ForcedRollbackProbe(guard, jdbc);
        }
    }

    static class ForcedRollbackProbe {
        private final AdminAuthorizationGuard guard;
        private final JdbcTemplate jdbc;

        ForcedRollbackProbe(AdminAuthorizationGuard guard, JdbcTemplate jdbc) {
            this.guard = guard;
            this.jdbc = jdbc;
        }

        @DatabaseTransactional(role = DatabaseRole.ADMIN)
        public void hideThenFail(long postId, String reason) {
            guard.requireActiveAdmin();
            jdbc.queryForObject("select public.admin_hide_post(?, ?)", Object.class, postId, reason);
            throw new IllegalStateException("ADM3 forced rollback");
        }
    }
}
