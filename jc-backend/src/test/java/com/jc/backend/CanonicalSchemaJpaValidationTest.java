package com.jc.backend;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@CanonicalPostgresTest
class CanonicalSchemaJpaValidationTest {

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void hibernateValidatesAgainstCanonicalPostgresSchema() {
        assertThat(entityManagerFactory.isOpen()).isTrue();
        List<String> tables = jdbcTemplate.queryForList(
                """
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'app_users', 'regions', 'posts', 'places', 'comments',
                    'post_likes', 'bookmarks', 'refresh_tokens', 'crews', 'crew_members',
                    'recommendation_snapshot', 'recommendation_run'
                  )
                order by table_name
                """,
                String.class);
        assertThat(tables).containsExactly(
                "app_users",
                "bookmarks",
                "comments",
                "crew_members",
                "crews",
                "places",
                "post_likes",
                "posts",
                "recommendation_run",
                "recommendation_snapshot",
                "refresh_tokens",
                "regions");

        assertThat(jdbcTemplate.queryForObject(
                "select has_function_privilege('jc_app', "
                        + "'public.can_user_view_post(bigint,bigint)', 'EXECUTE')",
                Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select has_function_privilege('jc_recommendation', "
                        + "'public.can_user_view_post(bigint,bigint)', 'EXECUTE')",
                Boolean.class))
                .isTrue();
    }
}
