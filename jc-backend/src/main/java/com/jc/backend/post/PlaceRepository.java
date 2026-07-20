package com.jc.backend.post;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Override
    @EntityGraph(attributePaths = "region")
    List<Place> findAllById(Iterable<Long> ids);
}
