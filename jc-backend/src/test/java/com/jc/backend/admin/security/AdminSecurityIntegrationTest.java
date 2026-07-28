package com.jc.backend.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.auth.AuthDtos;
import com.jc.backend.auth.AuthService;
import com.jc.backend.common.ApiResponse;
import com.jc.backend.common.DomainException;
import com.jc.backend.database.DatabaseRequestIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@Import(AdminSecurityIntegrationTest.AdminTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminSecurityIntegrationTest {

    private static final String PASSWORD = "password1234";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private JwtDecoder jwtDecoder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AdminAuthorizationGuard adminGuard;
    @Autowired private DatabaseRequestIdentity requestIdentity;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymous_admin_request_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/__test/access"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void normal_user_admin_request_returns_403() throws Exception {
        AccountToken user = signupUser("normal");

        mockMvc.perform(get("/api/admin/__test/access")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void active_admin_admin_request_is_allowed() throws Exception {
        AccountToken admin = activeAdmin("active");

        mockMvc.perform(get("/api/admin/__test/access")
                        .header("Authorization", bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminUserId").value(admin.userId()))
                .andExpect(jsonPath("$.data.role").value("admin"))
                .andExpect(jsonPath("$.data.accountStatus").value("active"));
    }

    @Test
    void suspended_admin_request_returns_403() throws Exception {
        AccountToken admin = activeAdmin("suspended");
        setStatus(admin.userId(), "suspended");

        assertDbAuthoritativeDenial(admin.accessToken());
    }

    @Test
    void inactive_admin_request_returns_403() throws Exception {
        AccountToken admin = activeAdmin("withdrawn");
        setStatus(admin.userId(), "withdrawn");

        assertDbAuthoritativeDenial(admin.accessToken());
    }

    @Test
    void missing_db_user_returns_403() throws Exception {
        AccountToken admin = activeAdmin("missing");
        jdbcTemplate.update("delete from public.refresh_tokens where user_id = ?", admin.userId());
        jdbcTemplate.update("delete from public.app_users where id = ?", admin.userId());

        assertDbAuthoritativeDenial(admin.accessToken());
    }

    @Test
    void jwt_admin_but_db_user_role_user_returns_403() throws Exception {
        AccountToken admin = activeAdmin("demoted");
        setRole(admin.userId(), "user");

        assertDbAuthoritativeDenial(admin.accessToken());
    }

    @Test
    void jwt_user_but_db_user_role_admin_does_not_bypass_contract() throws Exception {
        AccountToken userToken = signupUser("promoted-after-token");
        setRole(userToken.userId(), "admin");

        mockMvc.perform(get("/api/admin/__test/access")
                        .header("Authorization", bearer(userToken.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"));
    }

    @Test
    void all_admin_routes_require_authentication() throws Exception {
        mockMvc.perform(get("/api/admin"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void all_admin_routes_require_admin_authority() throws Exception {
        AccountToken user = signupUser("route-user");

        mockMvc.perform(get("/api/admin/unknown")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void non_admin_routes_are_not_accidentally_blocked() throws Exception {
        AccountToken user = signupUser("normal-route");

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearer(user.accessToken())))
                .andExpect(status().isOk());
    }

    @Test
    void public_routes_remain_public_where_intended() throws Exception {
        mockMvc.perform(get("/api/v1/test/welcome").param("lang", "en"))
                .andExpect(status().isOk());
    }

    @Test
    void admin_guard_returns_authoritative_actor() {
        AccountToken admin = activeAdmin("guard-ok");
        authenticate(admin.userId(), "admin");

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(admin.userId())) {
            AdminActor actor = adminGuard.requireActiveAdmin();
            assertThat(actor.adminUserId()).isEqualTo(admin.userId());
            assertThat(actor.role()).isEqualTo("admin");
            assertThat(actor.accountStatus()).isEqualTo("active");
        }
    }

    @Test
    void admin_guard_rejects_suspended_actor() {
        AccountToken admin = activeAdmin("guard-suspended");
        setStatus(admin.userId(), "suspended");
        authenticate(admin.userId(), "admin");

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(admin.userId())) {
            assertAdminGuardDenied();
        }
    }

    @Test
    void admin_guard_rejects_role_mismatch() {
        AccountToken admin = activeAdmin("guard-role");
        setRole(admin.userId(), "user");
        authenticate(admin.userId(), "admin");

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(admin.userId())) {
            assertAdminGuardDenied();
        }
    }

    @Test
    void admin_guard_rejects_missing_actor() {
        authenticate(999_999L, "admin");

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(999_999L)) {
            assertAdminGuardDenied();
        }
    }

    @Test
    void token_subject_mismatch_is_rejected() {
        AccountToken admin = activeAdmin("subject-mismatch");
        authenticate(admin.userId(), "admin");

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(admin.userId() + 1)) {
            assertAdminGuardDenied();
        }
    }

    @Test
    void issued_access_token_contains_current_role_but_db_remains_authoritative() {
        AccountToken admin = activeAdmin("claim");
        Jwt jwt = jwtDecoder.decode(admin.accessToken());

        assertThat(jwt.getClaimAsString("role")).isEqualTo("admin");
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(admin.userId()));
    }

    private void assertDbAuthoritativeDenial(String accessToken) throws Exception {
        mockMvc.perform(get("/api/admin/__test/access")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("관리자 접근 권한이 없습니다."));
    }

    private void assertAdminGuardDenied() {
        assertThatThrownBy(adminGuard::requireActiveAdmin)
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(403);
                    assertThat(exception.getCode()).isEqualTo("ADMIN_ACCESS_DENIED");
                });
    }

    private AccountToken signupUser(String prefix) {
        String suffix = prefix + "-" + SEQUENCE.incrementAndGet();
        AuthDtos.TokenResponse response = authService.signup(new AuthDtos.SignupRequest(
                suffix + "@example.test",
                PASSWORD,
                suffix));
        return new AccountToken(response.user().id(), response.accessToken());
    }

    private AccountToken activeAdmin(String prefix) {
        String suffix = prefix + "-" + SEQUENCE.incrementAndGet();
        String email = suffix + "@example.test";
        AuthDtos.TokenResponse signup = authService.signup(new AuthDtos.SignupRequest(
                email,
                PASSWORD,
                suffix));
        setRole(signup.user().id(), "admin");
        AuthDtos.TokenResponse login = authService.login(new AuthDtos.LoginRequest(email, PASSWORD));
        return new AccountToken(signup.user().id(), login.accessToken());
    }

    private void setRole(long userId, String role) {
        jdbcTemplate.update("update public.app_users set role = ? where id = ?", role, userId);
    }

    private void setStatus(long userId, String status) {
        jdbcTemplate.update(
                "update public.app_users set account_status = ? where id = ?",
                status,
                userId);
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    record AccountToken(long userId, String accessToken) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class AdminTestConfiguration {

        @Bean
        AdminProbeController adminProbeController(AdminAuthorizationGuard guard) {
            return new AdminProbeController(guard);
        }
    }

    @RestController
    static class AdminProbeController {

        private final AdminAuthorizationGuard guard;

        AdminProbeController(AdminAuthorizationGuard guard) {
            this.guard = guard;
        }

        @GetMapping("/api/admin/__test/access")
        ApiResponse<AdminActor> access() {
            return ApiResponse.ok(guard.requireActiveAdmin());
        }
    }
}
