package com.jc.backend.post;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, PostUserId> {

    long countByPostId(Long postId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    void deleteByPostIdAndUserId(Long postId, Long userId);

    @Query("""
            select b.post.id as postId, count(b) as total
            from Bookmark b
            where b.post.id in :postIds
            group by b.post.id
            """)
    List<PostCountProjection> countByPostIds(@Param("postIds") List<Long> postIds);

    @Query(
            value = """
                    select b.*
                    from public.bookmarks b
                    join public.posts p on p.id = b.post_id
                    join public.app_users author on author.id = p.author_id
                    where b.user_id = :userId
                      and author.account_status = 'active'
                      and public.can_user_view_post(:userId, p.id)
                    order by b.created_at desc, b.post_id desc
                    """,
            countQuery = """
                    select count(*)
                    from public.bookmarks b
                    join public.posts p on p.id = b.post_id
                    join public.app_users author on author.id = p.author_id
                    where b.user_id = :userId
                      and author.account_status = 'active'
                      and public.can_user_view_post(:userId, p.id)
                    """,
            nativeQuery = true)
    Page<Bookmark> findVisibleByUserId(@Param("userId") Long userId, Pageable pageable);
}
