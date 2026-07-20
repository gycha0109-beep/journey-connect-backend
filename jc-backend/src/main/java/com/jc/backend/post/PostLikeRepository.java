package com.jc.backend.post;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, PostUserId> {

    long countByPostId(Long postId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    void deleteByPostIdAndUserId(Long postId, Long userId);

    @Query("""
            select l.post.id as postId, count(l) as total
            from PostLike l
            where l.post.id in :postIds
            group by l.post.id
            """)
    List<PostCountProjection> countByPostIds(@Param("postIds") List<Long> postIds);
}
