package com.jc.backend.admin.security;

import com.jc.backend.common.DomainException;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/** Resolves an active Admin from the verified JWT subject and current app_users state. */
@Service
public final class AdminAuthorizationGuard {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACTIVE_STATUS = "active";

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseRequestIdentity requestIdentity;

    public AdminAuthorizationGuard(
            JdbcTemplate jdbcTemplate,
            DatabaseRequestIdentity requestIdentity) {
        this.jdbcTemplate = jdbcTemplate;
        this.requestIdentity = requestIdentity;
    }

    @DatabaseTransactional(role = DatabaseRole.ADMIN, readOnly = true)
    public AdminActor requireActiveAdmin() {
        JwtAuthenticationToken jwt = currentJwt();
        long subject = numericSubject(jwt);
        assertRequestIdentity(subject);
        assertAdminTokenRole(jwt);

        List<AdminActor> matches = jdbcTemplate.query(
                "select id, username, role, account_status "
                        + "from public.app_users where id = ?",
                (resultSet, rowNumber) -> new AdminActor(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("role"),
                        resultSet.getString("account_status")),
                subject);

        if (matches.size() != 1) {
            throw denied();
        }
        AdminActor actor = matches.getFirst();
        if (!ADMIN_ROLE.equals(actor.role()) || !ACTIVE_STATUS.equals(actor.accountStatus())) {
            throw denied();
        }
        return actor;
    }

    private JwtAuthenticationToken currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            throw denied();
        }
        return jwt;
    }

    private long numericSubject(JwtAuthenticationToken jwt) {
        String subject = jwt.getToken().getSubject();
        try {
            long value = Long.parseLong(subject);
            if (value <= 0) {
                throw denied();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw denied();
        }
    }

    private void assertRequestIdentity(long subject) {
        OptionalLong verifiedRequestUser = requestIdentity.currentUserId();
        if (verifiedRequestUser.isEmpty() || verifiedRequestUser.getAsLong() != subject) {
            throw denied();
        }
    }

    private void assertAdminTokenRole(JwtAuthenticationToken jwt) {
        String tokenRole = jwt.getToken().getClaimAsString("role");
        if (tokenRole == null || !ADMIN_ROLE.equals(tokenRole.toLowerCase(Locale.ROOT))) {
            throw denied();
        }
    }

    private DomainException denied() {
        return new DomainException(
                HttpStatus.FORBIDDEN,
                "ADMIN_ACCESS_DENIED",
                "관리자 접근 권한이 없습니다.");
    }
}
