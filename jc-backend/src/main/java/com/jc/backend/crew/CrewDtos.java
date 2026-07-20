package com.jc.backend.crew;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.Instant;

public final class CrewDtos {

    private CrewDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 50) String regionCode,
            @Size(max = 100) String regionName,
            @NotBlank String description,
            LocalDate travelDate,
            @Min(2) @Max(100) int capacity,
            Boolean approvalRequired) {}

    public record ReviewRequest(CrewMemberStatus status) {}

    public record View(
            Long id,
            String title,
            String regionCode,
            String regionName,
            String description,
            LocalDate travelDate,
            int capacity,
            long memberCount,
            long pendingApplicationCount,
            boolean recruiting,
            boolean approvalRequired,
            Long ownerId,
            String ownerNickname,
            Instant createdAt) {}

    public record ApplicationView(
            Long id,
            Long crewId,
            Long userId,
            String userNickname,
            CrewMemberStatus status,
            Long reviewedBy,
            Instant reviewedAt,
            Instant createdAt) {}
}
