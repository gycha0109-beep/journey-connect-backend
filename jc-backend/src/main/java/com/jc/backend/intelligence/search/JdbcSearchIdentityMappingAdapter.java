package com.jc.backend.intelligence.search;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcSearchIdentityMappingAdapter implements SearchIdentityMappingReadPort {

    private static final String RESOLVE = """
            select subject_ref, identity_scheme, mapping_version
            from public.resolve_platform_subject_v1(?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchIdentityMappingAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)
    public ResolvedSubject resolve(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        String proposedSubjectRef = "subject:" + UUID.randomUUID();
        try {
            ResolvedSubject result = jdbcTemplate.queryForObject(
                    RESOLVE,
                    (resultSet, rowNumber) -> new ResolvedSubject(
                            resultSet.getString("subject_ref"),
                            resultSet.getString("identity_scheme"),
                            resultSet.getString("mapping_version")),
                    userId,
                    proposedSubjectRef,
                    SearchExposureContract.IDENTITY_PURPOSE,
                    SearchExposureContract.IDENTITY_REQUESTER);
            if (result == null) {
                throw new IllegalStateException("identity mapping function returned no row");
            }
            return result;
        } catch (DataAccessException exception) {
            throw new MappingUnavailableException(
                    "Search exposure identity mapping is unavailable", exception);
        }
    }
}
