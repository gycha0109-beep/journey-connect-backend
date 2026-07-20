package com.jc.backend.crew;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {

    Optional<CrewMember> findByCrewIdAndUserId(Long crewId, Long userId);

    boolean existsByCrewIdAndUserIdAndStatusIn(
            Long crewId,
            Long userId,
            Collection<CrewMemberStatus> statuses);

    long countByCrewIdAndStatusIn(Long crewId, Collection<CrewMemberStatus> statuses);

    @Query("""
            select m.crew.id as crewId, count(m.id) as total
            from CrewMember m
            where m.crew.id in :crewIds
              and m.status in :statuses
            group by m.crew.id
            """)
    List<CrewMemberCountProjection> countByCrewIdsAndStatuses(
            @Param("crewIds") List<Long> crewIds,
            @Param("statuses") Collection<CrewMemberStatus> statuses);

    @EntityGraph(attributePaths = {"crew", "user", "reviewedBy"})
    Page<CrewMember> findByCrewIdAndStatusOrderByCreatedAtAsc(
            Long crewId,
            CrewMemberStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"crew", "crew.owner", "user", "reviewedBy"})
    @Query("select m from CrewMember m where m.id = :memberId and m.crew.id = :crewId")
    Optional<CrewMember> findApplication(
            @Param("crewId") Long crewId,
            @Param("memberId") Long memberId);
}
