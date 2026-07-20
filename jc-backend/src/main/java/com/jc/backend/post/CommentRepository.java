package com.jc.backend.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    @Query("""
            select c from Comment c
            where c.post.id = :postId
              and c.deletedAt is null
              and c.moderationDeletedAt is null
              and c.author.accountStatus = 'active'
            order by c.createdAt asc, c.id asc
            """)
    Page<Comment> findVisibleByPostId(@Param("postId") Long postId, Pageable pageable);
}
