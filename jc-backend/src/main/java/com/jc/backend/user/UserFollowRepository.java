package com.jc.backend.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Canonical public.follows relation을 변경하는 APP persistence adapter입니다. */
@Repository
public class UserFollowRepository {

    private final JdbcTemplate jdbc;

    public UserFollowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void follow(long followerId, long followingId) {
        jdbc.update(
                """
                insert into public.follows(follower_id, following_id)
                values (?, ?)
                on conflict (follower_id, following_id) do nothing
                """,
                followerId,
                followingId);
    }

    public void unfollow(long followerId, long followingId) {
        jdbc.update(
                """
                delete from public.follows
                where follower_id = ?
                  and following_id = ?
                """,
                followerId,
                followingId);
    }
}
