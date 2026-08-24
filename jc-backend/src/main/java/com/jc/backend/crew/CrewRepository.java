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
    @Query(
            value = """
                    select c
                    from Crew c
                    where c.recruiting = true
                      and (
                          cast(:region as string) is null
                          or lower(c.region.slug) = cast(:region as string)
                          or lower(c.region.nameLocal) = cast(:region as string)
                          or lower(c.region.nameKo) = cast(:region as string)
                          or lower(c.region.nameEn) = cast(:region as string)
                      )
                      and (
                          cast(:keyword as string) is null
                          or lower(c.title) like concat('%', cast(:keyword as string), '%')
                          or lower(c.description) like concat('%', cast(:keyword as string), '%')
                          or lower(c.owner.displayName) like concat('%', cast(:keyword as string), '%')
                          or lower(c.region.slug) like concat('%', cast(:keyword as string), '%')
                          or lower(c.region.nameLocal) like concat('%', cast(:keyword as string), '%')
                          or lower(c.region.nameKo) like concat('%', cast(:keyword as string), '%')
                          or lower(c.region.nameEn) like concat('%', cast(:keyword as string), '%')
                      )
                    order by c.createdAt desc, c.id desc
                    """,
            countQuery = """
                    select count(c)
                    from Crew c
                    where c.recruiting = true
                      and (
                          cast(:region as string) is null
                          or lower(c.region.slug) = cast(:region as string)
                          or lower(c.region.nameLocal) = cast(:region as string)
                          or lower(c.region.nameKo) = cast(:region as string)
                          or lower(c.region.nameEn) = cast(:region as string)
                      )
                      and (
                          cast(:keyword as string) is null
                          or lower(c.title) like concat('%', cast(:keyword as string), '%')
                          or lower(c.description) like concat('%', cast(:keyword as string), '%')
                          or lower(c.owner.displayName) like concat('%', cast(:keyword as string), '%')
                          or lower(c.region.slug) like concat('%', cast(:keyword as string), '%')
                          or lower(c.region.nameLocal) like concat('%', cast(:keyword as string), '%')
                          or lower(c.region.nameKo) like concat('%', cast(:keyword as string), '%')
                          or lower(c.region.nameEn) like concat('%', cast(:keyword as string), '%')
                      )
                    """)
    Page<Crew> searchRecruiting(
            @Param("keyword") String keyword,
            @Param("region") String region,
            Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "region"})
    @Query("select c from Crew c where c.id = :crewId")
    Optional<Crew> findWithOwnerAndRegionById(@Param("crewId") Long crewId);

    @Query("""
            select count(c) from Crew c
            where c.owner.id = :ownerId and c.recruiting = true
            """)
    long countRecruitingByOwnerId(@Param("ownerId") Long ownerId);

    /** 동일 크루의 정원 판정과 승인 처리를 직렬화합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Crew c where c.id = :crewId")
    Optional<Crew> findByIdForUpdate(@Param("crewId") Long crewId);
}
