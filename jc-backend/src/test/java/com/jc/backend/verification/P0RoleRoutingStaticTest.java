package com.jc.backend.verification;

import static com.jc.backend.verification.StaticContractSupport.assertExactCopy;
import static com.jc.backend.verification.StaticContractSupport.failContract;
import static com.jc.backend.verification.StaticContractSupport.requireContains;
import static com.jc.backend.verification.StaticContractSupport.requireNotContains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p0-verification")
class P0RoleRoutingStaticTest {
    private static final Path V20 = RepositoryLayout.resolve("database/journey-connect-db-v2.0");
    private static final Path V21 = RepositoryLayout.resolve("database/journey-connect-db-v2.1");
    private static final Path V22 = RepositoryLayout.resolve("database/journey-connect-db-v2.2");
    private static final Path CANONICAL = RepositoryLayout.resolve(
            "jc-backend/src/test/resources/db/canonical");
    private static final Path MAIN_JAVA = RepositoryLayout.resolve("jc-backend/src/main/java");

    private static final List<String> BASE = List.of(
            "01_initial_schema.sql",
            "02_seed.sql",
            "03_smoke_test.sql",
            "04_admin_support.sql",
            "05_security_roles.sql",
            "06_security_smoke_test.sql",
            "07_recommendation_storage.sql",
            "08_recommendation_security_roles.sql",
            "09_recommendation_smoke_test.sql",
            "10_backend_runtime.sql",
            "11_backend_runtime_security_roles.sql",
            "12_backend_runtime_smoke_test.sql");
    private static final List<String> ROLE_ROUTING = List.of(
            "13_backend_role_routing.sql",
            "14_backend_role_routing_smoke_test.sql");
    private static final List<String> RUNTIME_FIX = List.of(
            "15_backend_role_runtime_fix.sql",
            "16_backend_role_runtime_fix_smoke_test.sql");

    @Test
    void roleRoutingContractsRemainClosedAndScoped() throws IOException {
        verifyCopies();
        verifySqlContracts();
        verifyJavaContracts();
        verifyConfiguration();
    }

    private static void verifyCopies() throws IOException {
        for (String name : BASE) {
            assertExactCopy(V20.resolve(name), V21.resolve(name), "v2.0 baseline drift in DB v2.1");
        }
        List<String> v21Files = java.util.stream.Stream.concat(BASE.stream(), ROLE_ROUTING.stream()).toList();
        for (String name : v21Files) {
            assertExactCopy(V21.resolve(name), V22.resolve(name), "v2.1 baseline drift in DB v2.2");
        }
        for (String name : java.util.stream.Stream.concat(v21Files.stream(), RUNTIME_FIX.stream()).toList()) {
            assertExactCopy(V22.resolve(name), CANONICAL.resolve(name), "canonical PostgreSQL SQL drift");
        }
    }

    private static void verifySqlContracts() throws IOException {
        String security = Files.readString(V21.resolve(ROLE_ROUTING.get(0)));
        String smoke = Files.readString(V21.resolve(ROLE_ROUTING.get(1)));
        String runtimeFix = Files.readString(V22.resolve(RUNTIME_FIX.get(0)));
        String runtimeFixSmoke = Files.readString(V22.resolve(RUNTIME_FIX.get(1)));
        requireContains(security, "GRANT SELECT (display_name, profile_image_url, bio", "auth profile read grant");
        requireContains(security, "GRANT UPDATE (display_name, profile_image_url, bio)", "auth profile update grant");
        requireContains(security, "REVOKE SELECT (email, password_hash, role)", "app credential denial");
        requireContains(security, "REVOKE ALL ON public.refresh_tokens", "refresh-token role isolation");
        requireContains(smoke, "has_column_privilege('jc_app'", "app column privilege smoke");
        requireContains(smoke, "has_column_privilege('jc_auth'", "auth column privilege smoke");
        requireContains(smoke, "ROLLBACK;", "role-routing smoke rollback");
        requireContains(runtimeFix, "SECURITY DEFINER", "crew trigger security boundary");
        requireContains(runtimeFix, "SET search_path = pg_catalog, pg_temp", "crew trigger safe search path");
        requireContains(runtimeFix, "GRANT SELECT ON public.crews, public.crew_members TO jc_security_owner", "crew trigger owner privileges");
        requireContains(runtimeFixSmoke, "SET CONSTRAINTS ALL IMMEDIATE", "restricted-role deferred trigger smoke");
        requireContains(runtimeFixSmoke, "must not be callable by jc_app", "crew internal function denial smoke");
        requireContains(runtimeFixSmoke, "ROLLBACK;", "runtime-fix smoke rollback");
    }

    private static void verifyJavaContracts() throws IOException {
        String role = readMain("com/jc/backend/database/DatabaseRole.java");
        String annotation = readMain("com/jc/backend/database/DatabaseTransactional.java");
        String propagation = readMain("com/jc/backend/database/DatabasePropagation.java");
        String boundary = readMain("com/jc/backend/database/DatabaseRoleBoundary.java");
        String aspect = readMain("com/jc/backend/database/DatabaseTransactionalAspect.java");
        String verifier = readMain("com/jc/backend/database/DatabaseRoleCapabilityVerifier.java");
        String requestFilter = readMain("com/jc/backend/database/DatabaseRequestIdentityFilter.java");
        String authAccount = readMain("com/jc/backend/auth/AuthAccount.java");
        String appAccount = readMain("com/jc/backend/user/UserAccount.java");

        for (String sqlRole : List.of("jc_app", "jc_auth", "jc_recommendation")) {
            requireContains(role, "\"" + sqlRole + "\"", "allowlisted role " + sqlRole);
        }
        requireContains(annotation, "DatabaseRole role();", "typed transaction role");
        requireContains(annotation, "DatabasePropagation propagation()", "restricted propagation type");
        requireContains(propagation, "REQUIRED", "required propagation");
        requireContains(propagation, "REQUIRES_NEW", "requires-new propagation");
        requireNotContains(propagation, "NOT_SUPPORTED", "transactionless propagation");
        requireNotContains(propagation, "SUPPORTS", "transactionless propagation");
        requireNotContains(propagation, "NEVER", "transactionless propagation");
        requireContains(boundary, "set local role ", "transaction-local role switch");
        requireContains(boundary, "set_config('jc.current_user_id', ?, true)", "transaction-local request identity");
        requireContains(boundary, "role switch inside one transaction is forbidden", "cross-role rejection");
        requireContains(aspect, "TransactionTemplate", "programmatic transaction boundary");
        requireContains(aspect, "@Order", "explicit aspect order");
        requireContains(verifier, "NOSUPERUSER, NOBYPASSRLS and NOINHERIT", "restricted login startup gate");
        requireContains(verifier, "unexpected role memberships", "runtime membership allowlist");
        requireContains(verifier, "must not own public/database objects", "runtime ownership denial");
        requireContains(verifier, "role_table_grants", "runtime direct privilege denial");
        requireContains(requestFilter, "JwtAuthenticationToken", "verified JWT subject source");
        requireContains(authAccount, "private String passwordHash;", "auth credential mapping");
        requireContains(appAccount, "@ColumnTransformer(read = \"NULL\")", "app credential suppression");

        List<String> transactionalOffenders = new ArrayList<>();
        List<String> propagationOffenders = new ArrayList<>();
        List<String> identitySetterOffenders = new ArrayList<>();
        try (var files = Files.walk(MAIN_JAVA)) {
            for (Path path : files.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String text = Files.readString(path);
                String relative = RepositoryLayout.relative(path);
                if (text.contains("org.springframework.transaction.annotation.Transactional")) {
                    transactionalOffenders.add(relative);
                }
                if (text.contains("org.springframework.transaction.annotation.Propagation")) {
                    propagationOffenders.add(relative);
                }
                if (text.contains("set_config('jc.current_user_id'")
                        && !path.getFileName().toString().equals("DatabaseRoleBoundary.java")) {
                    identitySetterOffenders.add(relative);
                }
            }
        }
        if (!transactionalOffenders.isEmpty()) {
            failContract("unscoped @Transactional remains: " + transactionalOffenders);
        }
        if (!propagationOffenders.isEmpty()) {
            failContract("unrestricted Spring propagation remains: " + propagationOffenders);
        }
        if (!identitySetterOffenders.isEmpty()) {
            failContract("request identity setter exists outside role boundary: " + identitySetterOffenders);
        }

        Map<String, String> requiredScopes = new LinkedHashMap<>();
        requiredScopes.put("com/jc/backend/auth/AuthService.java", "DatabaseRole.AUTH");
        requiredScopes.put("com/jc/backend/crew/CrewService.java", "DatabaseRole.APP");
        requiredScopes.put("com/jc/backend/post/PostService.java", "DatabaseRole.APP");
        requiredScopes.put("com/jc/backend/recommendation/RecommendationCandidateSource.java", "DatabaseRole.RECOMMENDATION");
        requiredScopes.put("com/jc/backend/recommendation/persistence/RecommendationRunStore.java", "DatabaseRole.RECOMMENDATION");
        for (Map.Entry<String, String> entry : requiredScopes.entrySet()) {
            requireContains(readMain(entry.getKey()), entry.getValue(), "role scope for " + entry.getKey());
        }
    }

    private static void verifyConfiguration() throws IOException {
        String security = RepositoryLayout.read("jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt");
        String initializer = RepositoryLayout.read("jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java");
        String application = RepositoryLayout.read("jc-backend/src/main/resources/application.yml.sample");
        requireContains(security, "BearerTokenAuthenticationFilter", "post-authentication filter placement");
        requireContains(security, "isEnabled = false", "servlet auto-registration disable");
        requireContains(application, "username: ${DB_USERNAME:jc_backend}", "restricted runtime login default");
        requireContains(application, "require-restricted-login:", "restricted login property");
        for (String name : java.util.stream.Stream.concat(ROLE_ROUTING.stream(), RUNTIME_FIX.stream()).toList()) {
            requireContains(initializer, "\"" + name + "\"", "PostgreSQL bootstrap migration " + name);
        }
    }

    private static String readMain(String relative) throws IOException {
        return Files.readString(MAIN_JAVA.resolve(relative));
    }
}
