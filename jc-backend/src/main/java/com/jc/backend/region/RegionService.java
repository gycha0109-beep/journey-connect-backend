package com.jc.backend.region;

import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.common.DomainException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
public class RegionService {

    private final RegionRepository regions;

    public RegionService(RegionRepository regions) {
        this.regions = regions;
    }

    public List<RegionDtos.View> list(String keyword) {
        List<Region> result = keyword == null || keyword.isBlank()
                ? regions.listActive(org.springframework.data.domain.PageRequest.of(0, 50))
                : regions.searchActive(keyword.trim(), org.springframework.data.domain.PageRequest.of(0, 50));
        return result.stream().map(this::view).toList();
    }

    public List<RegionDtos.View> nearby(
            double latitude,
            double longitude,
            double radiusKm,
            int limit) {
        validateCoordinates(latitude, longitude);
        if (radiusKm <= 0 || radiusKm > 500) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RADIUS",
                    "검색 반경은 0km 초과 500km 이하여야 합니다.");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return regions.findNearby(latitude, longitude, radiusKm, safeLimit)
                .stream()
                .map(this::view)
                .toList();
    }


    public Region require(String code, String legacyName) {
        if (code != null && !code.isBlank()) {
            return requireByCode(code);
        }
        if (legacyName != null && !legacyName.isBlank()) {
            return regions.findFirstByDisplayNameIgnoreCase(legacyName.trim())
                    .orElseThrow(() -> new DomainException(
                            HttpStatus.NOT_FOUND,
                            "REGION_NOT_FOUND",
                            "지역을 찾을 수 없습니다."));
        }
        throw new DomainException(
                HttpStatus.BAD_REQUEST,
                "REGION_REQUIRED",
                "지역 코드는 필수입니다.");
    }

    public Region requireByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "REGION_REQUIRED",
                    "지역 코드는 필수입니다.");
        }
        return regions.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "REGION_NOT_FOUND",
                        "지역을 찾을 수 없습니다."));
    }

    private RegionDtos.View view(Region region) {
        Double latitude = region.getCenter() == null ? null : region.getCenter().getY();
        Double longitude = region.getCenter() == null ? null : region.getCenter().getX();
        return new RegionDtos.View(
                region.getId(),
                region.getCode(),
                region.getCountryCode(),
                region.getDisplayName(),
                latitude,
                longitude);
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_COORDINATES",
                    "위도 또는 경도 범위가 올바르지 않습니다.");
        }
    }
}
