package com.jc.backend.database;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fails startup when the configured login cannot enforce the runtime role boundary. */
@Component
@ConditionalOnProperty(
        prefix = "app.database.role-routing",
        name = "verify-on-startup",
        havingValue = "true",
        matchIfMissing = true)
public final class DatabaseRoleCapabilityVerifier implements SmartInitializingSingleton {

    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;
    private final boolean requireRestrictedLogin;

    public DatabaseRoleCapabilityVerifier(
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate,
            @Value("${app.database.role-routing.require-restricted-login:true}")
                    boolean requireRestrictedLogin) {
        this.transactionManager = transactionManager;
        this.jdbcTemplate = jdbcTemplate;
        this.requireRestrictedLogin = requireRestrictedLogin;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (requireRestrictedLogin) {
            verifySessionLoginIsRestricted();
            verifySessionLoginMemberships();
            verifySessionLoginHasNoDirectDataPrivilegesOrOwnership();
        }
        for (DatabaseRole role : DatabaseRole.values()) {
            verifyRoleAssumption(role);
        }
    }

    private void verifySessionLoginIsRestricted() {
        Map<String, Object> attributes = jdbcTemplate.queryForMap(
                "select rolname, rolsuper, rolbypassrls, rolinherit "
                        + "from pg_roles where rolname = session_user");
        String login = String.valueOf(attributes.get("rolname"));
        boolean superuser = Boolean.TRUE.equals(attributes.get("rolsuper"));
        boolean bypassRls = Boolean.TRUE.equals(attributes.get("rolbypassrls"));
        boolean inheritsPrivileges = Boolean.TRUE.equals(attributes.get("rolinherit"));
        if (superuser || bypassRls || inheritsPrivileges) {
            throw new IllegalStateException(
                    "Database login " + login
                            + " must be NOSUPERUSER, NOBYPASSRLS and NOINHERIT; "
                            + "direct or inherited privileges bypass role-scoped transactions");
        }
    }


    private void verifySessionLoginMemberships() {
        List<String> memberships = jdbcTemplate.queryForList(
                "with recursive inherited(roleid) as ("
                        + " select m.roleid from pg_auth_members m"
                        + " join pg_roles login_role on login_role.oid = m.member"
                        + " where login_role.rolname = session_user"
                        + " union"
                        + " select m.roleid from pg_auth_members m"
                        + " join inherited parent on parent.roleid = m.member"
                        + ")"
                        + " select granted_role.rolname from inherited"
                        + " join pg_roles granted_role on granted_role.oid = inherited.roleid",
                String.class);
        Set<String> allowed = Set.of(
                DatabaseRole.APP.sqlName(),
                DatabaseRole.AUTH.sqlName(),
                DatabaseRole.RECOMMENDATION.sqlName());
        List<String> unexpected = memberships.stream()
                .filter(role -> !allowed.contains(role))
                .sorted()
                .toList();
        if (!unexpected.isEmpty()) {
            throw new IllegalStateException(
                    "Database login has unexpected role memberships: " + unexpected);
        }
    }

    private void verifySessionLoginHasNoDirectDataPrivilegesOrOwnership() {
        Long directGrantCount = jdbcTemplate.queryForObject(
                "select count(*) from ("
                        + " select 1 from information_schema.role_table_grants"
                        + " where grantee = session_user and table_schema = 'public'"
                        + " union all"
                        + " select 1 from information_schema.role_column_grants"
                        + " where grantee = session_user and table_schema = 'public'"
                        + " union all"
                        + " select 1 from information_schema.role_routine_grants"
                        + " where grantee = session_user and routine_schema = 'public'"
                        + " union all"
                        + " select 1 from information_schema.role_usage_grants"
                        + " where grantee = session_user and object_schema = 'public'"
                        + ") direct_grants",
                Long.class);
        Long ownedObjectCount = jdbcTemplate.queryForObject(
                "select count(*) from ("
                        + " select 1 from pg_class owned_relation"
                        + " join pg_namespace owned_schema on owned_schema.oid = owned_relation.relnamespace"
                        + " join pg_roles login_role on login_role.oid = owned_relation.relowner"
                        + " where owned_schema.nspname = 'public' and login_role.rolname = session_user"
                        + " union all"
                        + " select 1 from pg_proc owned_routine"
                        + " join pg_namespace owned_schema on owned_schema.oid = owned_routine.pronamespace"
                        + " join pg_roles login_role on login_role.oid = owned_routine.proowner"
                        + " where owned_schema.nspname = 'public' and login_role.rolname = session_user"
                        + " union all"
                        + " select 1 from pg_database owned_database"
                        + " join pg_roles login_role on login_role.oid = owned_database.datdba"
                        + " where owned_database.datname = current_database()"
                        + " and login_role.rolname = session_user"
                        + " union all"
                        + " select 1 from pg_namespace owned_schema"
                        + " join pg_roles login_role on login_role.oid = owned_schema.nspowner"
                        + " where owned_schema.nspname = 'public' and login_role.rolname = session_user"
                        + ") owned_objects",
                Long.class);
        if ((directGrantCount != null && directGrantCount > 0)
                || (ownedObjectCount != null && ownedObjectCount > 0)) {
            throw new IllegalStateException(
                    "Database login must not own public/database objects or receive direct data privileges");
        }
    }

    private void verifyRoleAssumption(DatabaseRole role) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            jdbcTemplate.execute("set local role " + role.sqlName());
            String currentRole = jdbcTemplate.queryForObject("select current_role", String.class);
            if (!role.sqlName().equals(currentRole)) {
                throw new IllegalStateException(
                        "Configured database login cannot assume required role " + role.sqlName());
            }
            status.setRollbackOnly();
        });
    }
}
