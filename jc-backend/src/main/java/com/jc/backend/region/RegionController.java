package com.jc.backend.region;

import com.jc.backend.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @GetMapping
    ApiResponse<List<RegionDtos.View>> list(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(regionService.list(keyword));
    }

    @GetMapping("/nearby")
    ApiResponse<List<RegionDtos.View>> nearby(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "20") double radiusKm,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(regionService.nearby(latitude, longitude, radiusKm, limit));
    }
}
