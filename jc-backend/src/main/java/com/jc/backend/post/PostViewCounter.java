package com.jc.backend.post;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL에서는 보안 함수를, H2 단위 테스트에서는 메모리 증가를 사용합니다. */
@Component
public class PostViewCounter {

    private final JdbcTemplate jdbcTemplate;

    public PostViewCounter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long increment(Long postId, long currentValue) {
        if (!isPostgreSql()) {
            return currentValue + 1;
        }

        Long updated = jdbcTemplate.queryForObject(
                "select public.increment_post_view(?)",
                Long.class,
                postId);
        if (updated == null) {
            throw new IllegalStateException("increment_post_view returned null");
        }
        return updated;
    }

    private boolean isPostgreSql() {
        Boolean result = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try {
                DatabaseMetaData metadata = connection.getMetaData();
                return metadata.getDatabaseProductName()
                        .toLowerCase(Locale.ROOT)
                        .contains("postgresql");
            } catch (SQLException exception) {
                throw new IllegalStateException("Database product detection failed", exception);
            }
        });
        return Boolean.TRUE.equals(result);
    }
}
