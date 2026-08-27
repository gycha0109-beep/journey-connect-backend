package com.jc.backend.user;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 현재 공개 가능한 게시글만 대상으로 사용자의 좋아요 이력을 읽는 APP read model입니다. */
@Repository
public class UserLikedPostReadRepository {

    private final JdbcTemplate jdbc;

    public UserLikedPostReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<Long> findPublicPostIdsByUserId(long userId, Pageable pageable) {
        Long totalValue = jdbc.queryForObject(
                """
                select count(*)
                from public.post_likes l
                join public.posts p on p.id = l.post_id
                join public.app_users author on author.id = p.author_id
                where l.user_id = ?
                  and p.status = 'published'
                  and p.visibility = 'public'
                  and p.moderation_status = 'visible'
                  and author.account_status = 'active'
                """,
                Long.class,
                userId);
        long total = totalValue == null ? 0L : totalValue;
        if (total == 0L) {
            return new PageImpl<>(List.of(), pageable, 0L);
        }

        List<Long> postIds = jdbc.queryForList(
                """
                select l.post_id
                from public.post_likes l
                join public.posts p on p.id = l.post_id
                join public.app_users author on author.id = p.author_id
                where l.user_id = ?
                  and p.status = 'published'
                  and p.visibility = 'public'
                  and p.moderation_status = 'visible'
                  and author.account_status = 'active'
                order by l.created_at desc, l.post_id desc
                limit ? offset ?
                """,
                Long.class,
                userId,
                pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(postIds, pageable, total);
    }
}
