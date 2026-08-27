package com.jc.backend.crew;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class CrewDtos {

    private CrewDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 50) String regionCode,
            @Size(max = 100) String regionName,
            @NotBlank String description,
            LocalDate travelDate,
            @Min(2) @Max(20) int capacity,
            Boolean approvalRequired,
            @Size(max = 500) String openChatUrl) {

        public CreateRequest(
                String title,
                String regionCode,
                String regionName,
                String description,
                LocalDate travelDate,
                int capacity,
                Boolean approvalRequired) {
            this(title, regionCode, regionName, description, travelDate, capacity, approvalRequired, null);
        }
    }

    public record UpdateRequest(
            @Size(max = 120) String title,
            @Size(max = 50) String regionCode,
            @Size(max = 100) String regionName,
            String description,
            LocalDate travelDate,
            @Min(2) @Max(20) Integer capacity,
            @Size(max = 500) String openChatUrl) {

        public UpdateRequest(
                String title,
                String regionCode,
                String regionName,
                String description,
                LocalDate travelDate,
                Integer capacity) {
            this(title, regionCode, regionName, description, travelDate, capacity, null);
        }
    }

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
            Instant createdAt,
            String openChatUrl,
            Viewer viewer) {

        public View(
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
                Instant createdAt,
                Viewer viewer) {
            this(
                    id,
                    title,
                    regionCode,
                    regionName,
                    description,
                    travelDate,
                    capacity,
                    memberCount,
                    pendingApplicationCount,
                    recruiting,
                    approvalRequired,
                    ownerId,
                    ownerNickname,
                    createdAt,
                    null,
                    viewer);
        }
    }

    public record Viewer(
            CrewMemberStatus membershipStatus,
            boolean owner,
            boolean canJoin,
            boolean canCancel,
            boolean canManageApplications,
            boolean canAccessOpenChat) {

        public Viewer(
                CrewMemberStatus membershipStatus,
                boolean owner,
                boolean canJoin,
                boolean canCancel,
                boolean canManageApplications) {
            this(membershipStatus, owner, canJoin, canCancel, canManageApplications, false);
        }
    }

    public record MyCrewItem(
            View crew,
            CrewMemberStatus membershipStatus,
            Instant joinedOrAppliedAt) {}

    public enum MemberRole {
        OWNER,
        MEMBER
    }

    public record MemberView(
            Long userId,
            String nickname,
            String profileImageUrl,
            MemberRole role,
            Instant joinedAt) {}

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
