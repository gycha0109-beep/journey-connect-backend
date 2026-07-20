package com.jc.backend.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, Long> {

    @Query("select r from Region r where lower(r.slug) = lower(:code) and r.active = true")
    Optional<Region> findByCodeIgnoreCase(@Param("code") String code);

    @Query("""
            select r from Region r
            where r.active = true
              and (lower(r.nameLocal) = lower(:name)
                   or lower(coalesce(r.nameKo, '')) = lower(:name)
                   or lower(coalesce(r.nameEn, '')) = lower(:name))
            order by r.sortOrder, r.id
            """)
    List<Region> findByDisplayName(@Param("name") String name);

    default Optional<Region> findFirstByDisplayNameIgnoreCase(String displayName) {
        return findByDisplayName(displayName).stream().findFirst();
    }

    @Query("""
            select r from Region r
            where r.active = true
              and (lower(r.nameLocal) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(r.nameKo, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(r.nameEn, '')) like lower(concat('%', :keyword, '%')))
            order by r.countryCode, r.sortOrder, r.nameLocal
            """)
    List<Region> searchActive(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select r from Region r
            where r.active = true
            order by r.countryCode, r.sortOrder, r.nameLocal
            """)
    List<Region> listActive(org.springframework.data.domain.Pageable pageable);

    /** PostGIS 없이 Haversine 거리(km)를 계산합니다. */
    @Query(value = """
            select *
            from public.regions r
            where r.is_active = true
              and r.center_latitude is not null
              and r.center_longitude is not null
              and 6371.0088 * 2 * asin(sqrt(
                    power(sin(radians((r.center_latitude - :latitude) / 2)), 2)
                    + cos(radians(:latitude)) * cos(radians(r.center_latitude))
                    * power(sin(radians((r.center_longitude - :longitude) / 2)), 2)
                  )) <= :radiusKm
            order by 6371.0088 * 2 * asin(sqrt(
                    power(sin(radians((r.center_latitude - :latitude) / 2)), 2)
                    + cos(radians(:latitude)) * cos(radians(r.center_latitude))
                    * power(sin(radians((r.center_longitude - :longitude) / 2)), 2)
                  )), r.id
            limit :limit
            """, nativeQuery = true)
    List<Region> findNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusKm") double radiusKm,
            @Param("limit") int limit);
}
