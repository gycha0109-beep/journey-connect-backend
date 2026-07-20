package com.jc.backend.crew;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrewRepository extends JpaRepository<Crew, Long> {

    @EntityGraph(attributePaths = {"owner", "region"})
    Page<Crew> findByRecruitingTrueOrderByCreatedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "region"})
    @Query("select c from Crew c where c.id = :crewId")
    Optional<Crew> findWithOwnerAndRegionById(@Param("crewId") Long crewId);

    /** 동일 크루의 정원 판정과 승인 처리를 직렬화합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Crew c where c.id = :crewId")
    Optional<Crew> findByIdForUpdate(@Param("crewId") Long crewId);
}
