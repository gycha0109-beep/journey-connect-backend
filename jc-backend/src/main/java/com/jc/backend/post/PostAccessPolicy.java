package com.jc.backend.post;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** canonical DB의 게시글 공개 판정을 단일 함수로 재사용합니다. */
@Component
public class PostAccessPolicy {

    private final JdbcTemplate jdbcTemplate;

    public PostAccessPolicy(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean canView(Long viewerId, Long postId) {
        Boolean allowed = jdbcTemplate.queryForObject(
                "select public.can_user_view_post(?, ?)",
                Boolean.class,
                viewerId,
                postId);
        return Boolean.TRUE.equals(allowed);
    }
}
