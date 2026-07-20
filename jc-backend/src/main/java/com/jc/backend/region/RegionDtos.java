package com.jc.backend.region;

public final class RegionDtos {

    private RegionDtos() {}

    public record View(
            Long id,
            String code,
            String countryCode,
            String displayName,
            Double latitude,
            Double longitude) {}
}
