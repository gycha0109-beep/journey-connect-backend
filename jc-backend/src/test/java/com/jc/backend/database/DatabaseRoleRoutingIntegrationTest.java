package com.jc.backend.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@Import(DatabaseRoleRoutingIntegrationTest.RoleTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseRoleRoutingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RoleProbe roleProbe;
    @Autowired private AppUserProbe appUserProbe;
    @Autowired private AppCallsAuthProbe appCallsAuthProbe;
    @Autowired private RequiresNewOuterProbe requiresNewOuterProbe;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate adminJdbcTemplate;
    @Autowired private DataSource dataSource;

    @Test
    void verifiedJwtSubjectIsBoundTransactionLocallyAfterAuthentication() throws Exception {
        mockMvc.perform(get("/__test/database-role")
                        .with(jwt().jwt(token -> token.subject("987654"))))
                .andExpect(status().isOk())
                .andExpect(content().string("jc_app:987654"));
    }

    @Test
    void appRoleCannotReadCredentialColumnsButAppEntityLoadsPublicProfile() {
        UserAccount user = users.saveAndFlush(
                new UserAccount("role-app@example.com", "hash", "role-app"));

        assertThat(appUserProbe.nickname(user.getId())).isEqualTo("role-app");
        assertThatThrownBy(() -> roleProbe.appPasswordHash(user.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("app_users");
    }

    @Test
    void authRoleReadsCredentialsButCannotReadPostData() {
        UserAccount user = users.saveAndFlush(
                new UserAccount("role-auth@example.com", "auth-hash", "role-auth"));

        assertThat(roleProbe.authPasswordHash(user.getId())).isEqualTo("auth-hash");
        assertThatThrownBy(roleProbe::authPostCount)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("posts");
    }

    @Test
    void recommendationRoleReadsCandidateDataButCannotReadRefreshTokens() {
        assertThat(roleProbe.recommendationPostCount()).isGreaterThanOrEqualTo(0L);
        assertThatThrownBy(roleProbe::recommendationRefreshTokenCount)
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("refresh_tokens");
    }


    @Test
    void anonymousTransactionClearsRequestIdentity() {
        assertThat(roleProbe.appContext()).isEqualTo("jc_app:");
    }

    @Test
    void sameRoleRequiresNewTransactionRestoresOuterRoleAndIdentity() {
        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(4242L)) {
            assertThat(requiresNewOuterProbe.invoke())
                    .isEqualTo("jc_app:4242|jc_app:4242|jc_app:4242");
        }
    }

    @Test
    void restrictedBackendLoginPassesStartupCapabilityVerification() throws Exception {
        String roleName = "jc_backend_role_routing_test";
        String password = "role-routing-test-password";
        dropRole(roleName);
        try {
            adminJdbcTemplate.execute(
                    "create role " + roleName
                            + " login noinherit nosuperuser nocreatedb nocreaterole"
                            + " noreplication nobypassrls password '" + password + "'");
            adminJdbcTemplate.execute(
                    "grant jc_app, jc_auth, jc_recommendation to " + roleName);

            String jdbcUrl;
            try (Connection connection = dataSource.getConnection()) {
                jdbcUrl = connection.getMetaData().getURL();
            }
            DriverManagerDataSource restrictedDataSource =
                    new DriverManagerDataSource(jdbcUrl, roleName, password);
            JdbcTemplate restrictedJdbc = new JdbcTemplate(restrictedDataSource);
            DatabaseRoleCapabilityVerifier verifier = new DatabaseRoleCapabilityVerifier(
                    new DataSourceTransactionManager(restrictedDataSource),
                    restrictedJdbc,
                    true);

            verifier.afterSingletonsInstantiated();
        } finally {
            dropRole(roleName);
        }
    }

    @Test
    void inheritingBackendLoginFailsStartupCapabilityVerification() throws Exception {
        String roleName = "jc_backend_insecure_test";
        String password = "role-routing-insecure-password";
        dropRole(roleName);
        try {
            adminJdbcTemplate.execute(
                    "create role " + roleName
                            + " login inherit nosuperuser nocreatedb nocreaterole"
                            + " noreplication nobypassrls password '" + password + "'");
            adminJdbcTemplate.execute(
                    "grant jc_app, jc_auth, jc_recommendation to " + roleName);

            String jdbcUrl;
            try (Connection connection = dataSource.getConnection()) {
                jdbcUrl = connection.getMetaData().getURL();
            }
            DriverManagerDataSource insecureDataSource =
                    new DriverManagerDataSource(jdbcUrl, roleName, password);
            DatabaseRoleCapabilityVerifier verifier = new DatabaseRoleCapabilityVerifier(
                    new DataSourceTransactionManager(insecureDataSource),
                    new JdbcTemplate(insecureDataSource),
                    true);

            assertThatThrownBy(verifier::afterSingletonsInstantiated)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NOINHERIT");
        } finally {
            dropRole(roleName);
        }
    }

    @Test
    void roleCannotChangeInsideOneTransaction() {
        assertThatThrownBy(appCallsAuthProbe::invoke)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database role switch inside one transaction is forbidden")
                .hasMessageContaining("jc_app -> jc_auth");
    }

    private void dropRole(String roleName) {
        adminJdbcTemplate.execute("drop role if exists " + roleName);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RoleTestConfiguration {

        @Bean
        RoleProbe roleProbe(JdbcTemplate jdbcTemplate) {
            return new RoleProbe(jdbcTemplate);
        }

        @Bean
        AppUserProbe appUserProbe(UserRepository users) {
            return new AppUserProbe(users);
        }

        @Bean
        AuthProbe authProbe(JdbcTemplate jdbcTemplate) {
            return new AuthProbe(jdbcTemplate);
        }

        @Bean
        AppCallsAuthProbe appCallsAuthProbe(AuthProbe authProbe) {
            return new AppCallsAuthProbe(authProbe);
        }

        @Bean
        RequiresNewInnerProbe requiresNewInnerProbe(JdbcTemplate jdbcTemplate) {
            return new RequiresNewInnerProbe(jdbcTemplate);
        }

        @Bean
        RequiresNewOuterProbe requiresNewOuterProbe(
                JdbcTemplate jdbcTemplate,
                RequiresNewInnerProbe innerProbe) {
            return new RequiresNewOuterProbe(jdbcTemplate, innerProbe);
        }

        @Bean
        RoleProbeController roleProbeController(RoleProbe roleProbe) {
            return new RoleProbeController(roleProbe);
        }
    }

    static class RoleProbe {
        private final JdbcTemplate jdbcTemplate;

        RoleProbe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
        public String appContext() {
            return jdbcTemplate.queryForObject(
                    "select current_role || ':' || current_setting('jc.current_user_id', true)",
                    String.class);
        }

        @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
        public String appPasswordHash(long userId) {
            return jdbcTemplate.queryForObject(
                    "select password_hash from public.app_users where id = ?",
                    String.class,
                    userId);
        }

        @DatabaseTransactional(role = DatabaseRole.AUTH, readOnly = true)
        public String authPasswordHash(long userId) {
            return jdbcTemplate.queryForObject(
                    "select password_hash from public.app_users where id = ?",
                    String.class,
                    userId);
        }

        @DatabaseTransactional(role = DatabaseRole.AUTH, readOnly = true)
        public long authPostCount() {
            Long value = jdbcTemplate.queryForObject("select count(*) from public.posts", Long.class);
            return value == null ? 0L : value;
        }

        @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
        public long recommendationPostCount() {
            Long value = jdbcTemplate.queryForObject("select count(*) from public.posts", Long.class);
            return value == null ? 0L : value;
        }

        @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION, readOnly = true)
        public long recommendationRefreshTokenCount() {
            Long value = jdbcTemplate.queryForObject("select count(*) from public.refresh_tokens", Long.class);
            return value == null ? 0L : value;
        }
    }

    static class AppUserProbe {
        private final UserRepository users;

        AppUserProbe(UserRepository users) {
            this.users = users;
        }

        @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
        public String nickname(long userId) {
            return users.findById(userId).orElseThrow().getNickname();
        }
    }

    static class AuthProbe {
        private final JdbcTemplate jdbcTemplate;

        AuthProbe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @DatabaseTransactional(role = DatabaseRole.AUTH, readOnly = true)
        public String currentRole() {
            return jdbcTemplate.queryForObject("select current_role", String.class);
        }
    }

    static class AppCallsAuthProbe {
        private final AuthProbe authProbe;

        AppCallsAuthProbe(AuthProbe authProbe) {
            this.authProbe = authProbe;
        }

        @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
        public String invoke() {
            return authProbe.currentRole();
        }
    }

    static class RequiresNewInnerProbe {
        private final JdbcTemplate jdbcTemplate;

        RequiresNewInnerProbe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @DatabaseTransactional(
                role = DatabaseRole.APP,
                readOnly = true,
                propagation = DatabasePropagation.REQUIRES_NEW)
        public String context() {
            return jdbcTemplate.queryForObject(
                    "select current_role || ':' || current_setting('jc.current_user_id', true)",
                    String.class);
        }
    }

    static class RequiresNewOuterProbe {
        private final JdbcTemplate jdbcTemplate;
        private final RequiresNewInnerProbe innerProbe;

        RequiresNewOuterProbe(JdbcTemplate jdbcTemplate, RequiresNewInnerProbe innerProbe) {
            this.jdbcTemplate = jdbcTemplate;
            this.innerProbe = innerProbe;
        }

        @DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
        public String invoke() {
            String before = context();
            String inner = innerProbe.context();
            String after = context();
            return before + "|" + inner + "|" + after;
        }

        private String context() {
            return jdbcTemplate.queryForObject(
                    "select current_role || ':' || current_setting('jc.current_user_id', true)",
                    String.class);
        }
    }

    @RestController
    static class RoleProbeController {
        private final RoleProbe roleProbe;

        RoleProbeController(RoleProbe roleProbe) {
            this.roleProbe = roleProbe;
        }

        @GetMapping("/__test/database-role")
        String databaseRole() {
            return roleProbe.appContext();
        }
    }
}
