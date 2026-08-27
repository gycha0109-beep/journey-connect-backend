package com.jc.backend.post;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JourneyPostRepository extends JpaRepository<JourneyPost, Long> {

    @EntityGraph(attributePaths = {"author", "region"})
    @Query("""
            select p from JourneyPost p
            where p.status = com.jc.backend.post.PostStatus.PUBLISHED
              and p.visibility = com.jc.backend.post.PostVisibility.PUBLIC
              and p.moderationStatus = com.jc.backend.post.PostModerationStatus.VISIBLE
              and p.author.accountStatus = 'active'
            order by p.publishedAt desc, p.id desc
            """)
    Page<JourneyPost> findByPublishedTrueOrderByCreatedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "region"})
    @Query("""
            select p from JourneyPost p
            where p.author.id = :authorId
              and p.status = com.jc.backend.post.PostStatus.PUBLISHED
              and p.visibility = com.jc.backend.post.PostVisibility.PUBLIC
              and p.moderationStatus = com.jc.backend.post.PostModerationStatus.VISIBLE
              and p.author.accountStatus = 'active'
            order by p.publishedAt desc, p.id desc
            """)
    Page<JourneyPost> findByAuthorIdAndPublishedTrueOrderByCreatedAtDescIdDesc(
            @Param("authorId") Long authorId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"author", "region"})
    @Query("""
            select p from JourneyPost p
            where p.author.id = :authorId
              and p.status <> com.jc.backend.post.PostStatus.DELETED
              and p.moderationStatus = com.jc.backend.post.PostModerationStatus.VISIBLE
            order by p.createdAt desc, p.id desc
            """)
    Page<JourneyPost> findByAuthorIdOrderByCreatedAtDescIdDesc(
            @Param("authorId") Long authorId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"author", "region"})
    @Query("""
            select p
            from JourneyPost p
            where p.status = com.jc.backend.post.PostStatus.PUBLISHED
              and p.visibility = com.jc.backend.post.PostVisibility.PUBLIC
              and p.moderationStatus = com.jc.backend.post.PostModerationStatus.VISIBLE
              and p.author.accountStatus = 'active'
              and (
                    :keyword is null
                    or lower(p.title) like lower(concat('%', :keyword, '%'))
                    or lower(p.content) like lower(concat('%', :keyword, '%'))
                    or lower(p.region.nameLocal) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(p.region.nameKo, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(p.region.nameEn, '')) like lower(concat('%', :keyword, '%'))
              )
              and (
                    :region is null
                    or lower(p.region.slug) = lower(:region)
                    or lower(p.region.nameLocal) = lower(:region)
                    or lower(coalesce(p.region.nameKo, '')) = lower(:region)
                    or lower(coalesce(p.region.nameEn, '')) = lower(:region)
              )
            order by p.publishedAt desc, p.id desc
            """)
    Page<JourneyPost> explore(
            @Param("keyword") String keyword,
            @Param("region") String region,
            Pageable pageable);

    @EntityGraph(attributePaths = {"author", "region"})
    @Query("""
            select p
            from JourneyPost p
            where p.status = com.jc.backend.post.PostStatus.PUBLISHED
              and p.visibility = com.jc.backend.post.PostVisibility.PUBLIC
              and p.moderationStatus = com.jc.backend.post.PostModerationStatus.VISIBLE
              and p.author.accountStatus = 'active'
            order by p.publishedAt desc, p.id desc
            """)
    List<JourneyPost> findFirstFeed(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "region"})
    @Query("""
            select p
            from JourneyPost p
            where p.status = com.jc.backend.post.PostStatus.PUBLISHED
              and p.visibility = com.jc.backend.post.PostVisibility.PUBLIC
              and p.moderationStatus = com.jc.backend.post.PostModerationStatus.VISIBLE
              and p.author.accountStatus = 'active'
              and (
                    p.publishedAt < :cursorPublishedAt
                    or (p.publishedAt = :cursorPublishedAt and p.id < :cursorId)
              )
            order by p.publishedAt desc, p.id desc
            """)
    List<JourneyPost> findFeedAfter(
            @Param("cursorPublishedAt") Instant cursorPublishedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"author", "region"})
    @Query("""
            select p
            from JourneyPost p
            where p.id in :postIds
              and p.status = com.jc.backend.post.PostStatus.PUBLISHED
              and p.visibility = com.jc.backend.post.PostVisibility.PUBLIC
              and p.moderationStatus = com.jc.backend.post.PostModerationStatus.VISIBLE
              and p.author.accountStatus = 'active'
            """)
    List<JourneyPost> findVisibleByIdIn(@Param("postIds") List<Long> postIds);

    @EntityGraph(attributePaths = {"author", "region", "images"})
    @Query("select p from JourneyPost p where p.id = :postId")
    Optional<JourneyPost> findWithDetailById(@Param("postId") Long postId);
}
