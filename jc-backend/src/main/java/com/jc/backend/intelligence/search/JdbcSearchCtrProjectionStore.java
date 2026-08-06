package com.jc.backend.intelligence.search;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcSearchCtrProjectionStore implements SearchCtrProjectionPort {

    private static final String WRITE = """
            select *
            from public.write_search_ctr_projection_v1(
              ?::timestamptz,
              ?::timestamptz,
              ?::varchar,
              ?::varchar,
              ?::varchar,
              ?::varchar
            )
            """;

    private static final String REQUESTER = "reliability-search-ctr";

    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchCtrProjectionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @DatabaseTransactional(role = DatabaseRole.RELIABILITY)
    public WriteResult write(WriteCommand command) {
        Objects.requireNonNull(command, "command");
        return jdbcTemplate.queryForObject(
                WRITE,
                JdbcSearchCtrProjectionStore::map,
                Timestamp.from(command.windowStart()),
                Timestamp.from(command.windowEnd()),
                REQUESTER,
                command.expectedPredecessorProjectionId(),
                command.idempotencyKey(),
                command.producerBuildId());
    }

    private static WriteResult map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WriteResult(
                WriteStatus.valueOf(resultSet.getString("write_status")),
                resultSet.getString("projection_id"),
                resultSet.getString("projection_fingerprint"),
                resultSet.getString("predecessor_projection_id"),
                resultSet.getString("metric_id"),
                resultSet.getString("metric_version"),
                instant(resultSet, "window_start"),
                instant(resultSet, "window_end"),
                resultSet.getString("status"),
                resultSet.getLong("eligible_exposure_count"),
                resultSet.getLong("attributed_exposure_count"),
                resultSet.getObject("ctr_basis_points", Integer.class),
                instant(resultSet, "computed_at"),
                instant(resultSet, "source_max_received_at"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
