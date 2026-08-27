package com.jc.backend.crew;

import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.database.DatabaseRole;
import com.jc.backend.database.DatabaseTransactional;
import com.jc.backend.notification.NotificationService;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionService;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 크루 생성, 탐색, 참가, 승인/거절, 모집 관리 흐름을 담당합니다.
 *
 * <p>정원과 승인 상태는 동시에 변경될 수 있으므로 참가 신청과 승인 처리에는 크루 행 잠금을,
 * 사용자의 모집 중 크루 개수 제한에는 사용자 행 잠금을 사용합니다.
 */
@Service
@DatabaseTransactional(role = DatabaseRole.APP, readOnly = true)
public class CrewService {

    private static final Collection<CrewMemberStatus> ACTIVE_STATUSES =
            List.of(CrewMemberStatus.OWNER, CrewMemberStatus.APPROVED);
    private static final Collection<CrewMemberStatus> EXISTING_APPLICATION_STATUSES =
            List.of(CrewMemberStatus.OWNER, CrewMemberStatus.PENDING, CrewMemberStatus.APPROVED);
    private static final Collection<CrewMemberStatus> MY_CREW_STATUSES =
            List.of(CrewMemberStatus.OWNER, CrewMemberStatus.APPROVED, CrewMemberStatus.PENDING);
    private static final int MAX_RECRUITING_OWNED_CREWS = 3;
    private static final int MIN_CAPACITY = 2;
    private static final int MAX_CAPACITY = 20;
    private static final int MAX_OPEN_CHAT_URL_LENGTH = 500;

    private final CrewRepository crews;
    private final CrewMemberRepository members;
    private final UserRepository users;
    private final RegionService regionService;
    private final NotificationService notifications;

    public CrewService(
            CrewRepository crews,
            CrewMemberRepository members,
            UserRepository users,
            RegionService regionService,
            NotificationService notifications) {
        this.crews = crews;
        this.members = members;
        this.users = users;
        this.regionService = regionService;
        this.notifications = notifications;
    }

    public PageResponse<CrewDtos.View> list(Pageable pageable) {
        return list(null, null, null, pageable);
    }

    public PageResponse<CrewDtos.View> list(Long viewerId, Pageable pageable) {
        return list(viewerId, null, null, pageable);
    }

    public PageResponse<CrewDtos.View> list(
            Long viewerId,
            String keyword,
            String region,
            Pageable pageable) {
        Page<Crew> page = crews.searchRecruiting(
                normalizeSearch(keyword),
                normalizeSearch(region),
                pageable);
        List<Long> crewIds = page.getContent().stream().map(Crew::getId).toList();
        Map<Long, Long> activeCounts = countMap(crewIds, ACTIVE_STATUSES);
        Map<Long, Long> pendingCounts = countMap(crewIds, List.of(CrewMemberStatus.PENDING));
        Map<Long, CrewMemberStatus> viewerStatuses = viewerStatusMap(crewIds, viewerId);

        return PageResponse.from(page.map(crew -> {
            long memberCount = activeCounts.getOrDefault(crew.getId(), 0L);
            return view(
                    crew,
                    memberCount,
                    pendingCounts.getOrDefault(crew.getId(), 0L),
                    viewer(crew, viewerId, viewerStatuses.get(crew.getId()), memberCount));
        }));
    }

    public CrewDtos.View detail(Long crewId) {
        return detail(null, crewId);
    }

    public CrewDtos.View detail(Long viewerId, Long crewId) {
        Crew crew = findCrew(crewId);
        long memberCount = members.countByCrewIdAndStatusIn(crewId, ACTIVE_STATUSES);
        CrewMemberStatus viewerStatus = viewerId == null
                ? null
                : members.findByCrewIdAndUserId(crewId, viewerId)
                        .map(CrewMember::getStatus)
                        .orElse(null);
        return view(
                crew,
                memberCount,
                members.countByCrewIdAndStatusIn(crewId, List.of(CrewMemberStatus.PENDING)),
                viewer(crew, viewerId, viewerStatus, memberCount));
    }

    public PageResponse<CrewDtos.MemberView> members(Long crewId, Pageable pageable) {
        findCrew(crewId);
        return PageResponse.from(members
                .findByCrewIdAndStatusInOrderByCreatedAtAscIdAsc(
                        crewId,
                        ACTIVE_STATUSES,
                        pageable)
                .map(this::memberView));
    }

    public PageResponse<CrewDtos.MyCrewItem> myCrews(Long userId, Pageable pageable) {
        user(userId);
        Page<CrewMember> page = members.findByUserIdAndStatusInOrderByUpdatedAtDescIdDesc(
                userId,
                MY_CREW_STATUSES,
                pageable);
        List<Long> crewIds = page.getContent().stream()
                .map(member -> member.getCrew().getId())
                .toList();
        Map<Long, Long> activeCounts = countMap(crewIds, ACTIVE_STATUSES);
        Map<Long, Long> pendingCounts = countMap(crewIds, List.of(CrewMemberStatus.PENDING));

        return PageResponse.from(page.map(member -> {
            Crew crew = member.getCrew();
            long memberCount = activeCounts.getOrDefault(crew.getId(), 0L);
            CrewDtos.View crewView = view(
                    crew,
                    memberCount,
                    pendingCounts.getOrDefault(crew.getId(), 0L),
                    viewer(crew, userId, member.getStatus(), memberCount));
            return new CrewDtos.MyCrewItem(
                    crewView,
                    member.getStatus(),
                    joinedOrAppliedAt(member));
        }));
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public CrewDtos.View create(Long userId, CrewDtos.CreateRequest request) {
        UserAccount owner = lockedUser(userId);
        ensureOwnedRecruitingLimit(userId);
        validateCapacity(request.capacity());
        ensureTravelDateNotPassed(request.travelDate());
        Region region = regionService.require(request.regionCode(), request.regionName());
        boolean approvalRequired = request.approvalRequired() == null
                || request.approvalRequired();
        Crew crew = new Crew(
                owner,
                region,
                request.title().trim(),
                request.description().trim(),
                request.travelDate(),
                request.capacity(),
                approvalRequired);
        crew.updateOpenChatUrl(normalizeOpenChatUrl(request.openChatUrl()));
        crew = crews.save(crew);
        members.save(new CrewMember(crew, owner, CrewMemberStatus.OWNER));
        return view(
                crew,
                1L,
                0L,
                viewer(crew, userId, CrewMemberStatus.OWNER, 1L));
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public CrewDtos.View update(
            Long ownerId,
            Long crewId,
            CrewDtos.UpdateRequest request) {
        Crew crew = lockedCrew(crewId);
        ensureOwner(crew, ownerId);

        long memberCount = approvedMemberCount(crewId);
        int nextCapacity = request.capacity() == null ? crew.getCapacity() : request.capacity();
        validateCapacity(nextCapacity);
        if (nextCapacity < memberCount) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "CREW_CAPACITY_BELOW_ACTIVE_MEMBERS",
                    "현재 참가 인원보다 정원을 작게 설정할 수 없습니다.");
        }

        Region nextRegion = crew.getRegion();
        if (request.regionCode() != null || request.regionName() != null) {
            nextRegion = regionService.require(request.regionCode(), request.regionName());
        }

        String nextTitle = patchedRequired(
                request.title(),
                crew.getTitle(),
                "CREW_TITLE_REQUIRED",
                "크루 제목은 비워둘 수 없습니다.");
        String nextDescription = patchedRequired(
                request.description(),
                crew.getDescription(),
                "CREW_DESCRIPTION_REQUIRED",
                "크루 설명은 비워둘 수 없습니다.");
        LocalDate nextTravelDate = request.travelDate() == null
                ? crew.getTravelDate()
                : request.travelDate();
        if (crew.isRecruiting()) {
            ensureTravelDateNotPassed(nextTravelDate);
        }
        String nextOpenChatUrl = request.openChatUrl() == null
                ? crew.getOpenChatUrl()
                : normalizeOpenChatUrl(request.openChatUrl());

        crew.updateDetails(
                nextRegion,
                nextTitle,
                nextDescription,
                nextTravelDate,
                nextCapacity);
        crew.updateOpenChatUrl(nextOpenChatUrl);
        return managementView(crew, ownerId, memberCount);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public CrewDtos.View closeRecruitment(Long ownerId, Long crewId) {
        Crew crew = lockedCrew(crewId);
        ensureOwner(crew, ownerId);
        crew.closeRecruitment();
        return managementView(crew, ownerId, approvedMemberCount(crewId));
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public CrewDtos.View reopenRecruitment(Long ownerId, Long crewId) {
        Crew crew = lockedCrew(crewId);
        ensureOwner(crew, ownerId);
        ensureTravelDateNotPassed(crew.getTravelDate());

        long memberCount = approvedMemberCount(crewId);
        if (memberCount >= crew.getCapacity()) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "CREW_FULL",
                    "정원이 가득 찬 크루는 모집을 재개할 수 없습니다.");
        }
        if (!crew.isRecruiting()) {
            lockedUser(ownerId);
            ensureOwnedRecruitingLimit(ownerId);
        }

        crew.reopenRecruitment();
        return managementView(crew, ownerId, memberCount);
    }

    /** 참가 신청은 모집 중이고 여행일이 지나지 않은 크루에 대해서만 허용합니다. */
    @DatabaseTransactional(role = DatabaseRole.APP)
    public CrewDtos.ApplicationView join(Long userId, Long crewId) {
        Crew crew = lockedCrew(crewId);
        ensureRecruiting(crew);
        ensureTravelDateNotPassed(crew.getTravelDate());
        UserAccount applicant = user(userId);

        CrewMember existing = members.findByCrewIdAndUserId(crewId, userId).orElse(null);
        if (existing != null && EXISTING_APPLICATION_STATUSES.contains(existing.getStatus())) {
            return applicationView(existing);
        }

        if (approvedMemberCount(crewId) >= crew.getCapacity()) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "CREW_FULL",
                    "크루 정원이 가득 찼습니다.");
        }

        CrewMember application;
        if (existing == null) {
            application = new CrewMember(crew, applicant, CrewMemberStatus.PENDING);
        } else {
            existing.reapply(CrewMemberStatus.PENDING);
            application = existing;
        }
        if (!crew.isApprovalRequired()) {
            application.approve(crew.getOwner());
        }

        CrewMember persisted = members.save(application);
        if (persisted.getStatus() == CrewMemberStatus.PENDING) {
            notifications.crewApplication(
                    userId,
                    crew.getOwner().getId(),
                    crewId,
                    persisted.getId(),
                    Instant.now());
        }
        return applicationView(persisted);
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public void cancelJoin(Long userId, Long crewId) {
        user(userId);
        CrewMember application = members.findByCrewIdAndUserId(crewId, userId)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "CREW_APPLICATION_NOT_FOUND",
                        "크루 참가 내역을 찾을 수 없습니다."));
        if (application.getStatus() == CrewMemberStatus.OWNER) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "CREW_OWNER_CANNOT_CANCEL",
                    "크루장은 참가를 취소할 수 없습니다.");
        }
        application.cancel();
    }

    public PageResponse<CrewDtos.ApplicationView> applications(
            Long ownerId,
            Long crewId,
            Pageable pageable) {
        user(ownerId);
        Crew crew = findCrew(crewId);
        ensureOwner(crew, ownerId);
        return PageResponse.from(members
                .findByCrewIdAndStatusOrderByCreatedAtAsc(
                        crewId,
                        CrewMemberStatus.PENDING,
                        pageable)
                .map(this::applicationView));
    }

    @DatabaseTransactional(role = DatabaseRole.APP)
    public CrewDtos.ApplicationView review(
            Long ownerId,
            Long crewId,
            Long applicationId,
            CrewDtos.ReviewRequest request) {
        UserAccount owner = user(ownerId);
        Crew crew = lockedCrew(crewId);
        ensureOwner(crew, ownerId);
        if (request.status() != CrewMemberStatus.APPROVED
                && request.status() != CrewMemberStatus.REJECTED) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CREW_REVIEW_STATUS",
                    "승인 또는 거절 상태만 지정할 수 있습니다.");
        }

        CrewMember application = members.findApplication(crewId, applicationId)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "CREW_APPLICATION_NOT_FOUND",
                        "크루 참가 신청을 찾을 수 없습니다."));
        if (application.getStatus() != CrewMemberStatus.PENDING) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "CREW_APPLICATION_ALREADY_REVIEWED",
                    "이미 처리된 참가 신청입니다.");
        }

        if (request.status() == CrewMemberStatus.APPROVED) {
            if (approvedMemberCount(crewId) >= crew.getCapacity()) {
                throw new DomainException(
                        HttpStatus.CONFLICT,
                        "CREW_FULL",
                        "크루 정원이 가득 찼습니다.");
            }
            application.approve(owner);
            notifications.crewApproved(
                    ownerId,
                    application.getUser().getId(),
                    crewId,
                    applicationId,
                    application.getReviewedAt());
        } else {
            application.reject(owner);
            notifications.crewRejected(
                    ownerId,
                    application.getUser().getId(),
                    crewId,
                    applicationId,
                    application.getReviewedAt());
        }
        return applicationView(application);
    }

    private CrewDtos.View managementView(Crew crew, Long ownerId, long memberCount) {
        return view(
                crew,
                memberCount,
                members.countByCrewIdAndStatusIn(crew.getId(), List.of(CrewMemberStatus.PENDING)),
                viewer(crew, ownerId, CrewMemberStatus.OWNER, memberCount));
    }

    private String patchedRequired(
            String requested,
            String current,
            String code,
            String message) {
        if (requested == null) {
            return current;
        }
        String value = requested.trim();
        if (value.isBlank()) {
            throw new DomainException(HttpStatus.BAD_REQUEST, code, message);
        }
        return value;
    }

    private String normalizeOpenChatUrl(String requested) {
        if (requested == null) {
            return null;
        }
        String value = requested.trim();
        if (value.isBlank()) {
            return null;
        }
        if (value.length() > MAX_OPEN_CHAT_URL_LENGTH) {
            throw invalidOpenChatUrl();
        }
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw invalidOpenChatUrl();
            }
            return value;
        } catch (URISyntaxException exception) {
            throw invalidOpenChatUrl();
        }
    }

    private DomainException invalidOpenChatUrl() {
        return new DomainException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CREW_OPEN_CHAT_URL",
                "오픈채팅 URL은 사용자 정보가 없는 유효한 HTTPS 주소여야 합니다.");
    }

    private boolean travelDatePassed(LocalDate travelDate) {
        return travelDate != null && travelDate.isBefore(LocalDate.now());
    }

    private void ensureTravelDateNotPassed(LocalDate travelDate) {
        if (travelDatePassed(travelDate)) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "CREW_TRAVEL_DATE_PASSED",
                    "이미 지난 여행일의 크루는 모집할 수 없습니다.");
        }
    }

    private Map<Long, CrewMemberStatus> viewerStatusMap(List<Long> crewIds, Long viewerId) {
        if (viewerId == null || crewIds.isEmpty()) {
            return Map.of();
        }
        return members.findViewerMemberships(crewIds, viewerId).stream()
                .collect(Collectors.toUnmodifiableMap(
                        CrewViewerMembershipProjection::getCrewId,
                        CrewViewerMembershipProjection::getStatus));
    }

    private Map<Long, Long> countMap(
            List<Long> crewIds,
            Collection<CrewMemberStatus> statuses) {
        if (crewIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return members.countByCrewIdsAndStatuses(crewIds, statuses)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        CrewMemberCountProjection::getCrewId,
                        CrewMemberCountProjection::getTotal,
                        (existing, ignored) -> existing));
    }

    private long approvedMemberCount(Long crewId) {
        return members.countByCrewIdAndStatusIn(crewId, ACTIVE_STATUSES);
    }

    private void ensureOwnedRecruitingLimit(Long ownerId) {
        if (crews.countRecruitingByOwnerId(ownerId) >= MAX_RECRUITING_OWNED_CREWS) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "CREW_OWNER_ACTIVE_LIMIT_EXCEEDED",
                    "한 사용자는 모집 중인 크루를 최대 3개까지 개설할 수 있습니다.");
        }
    }

    private void validateCapacity(int capacity) {
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new DomainException(
                    HttpStatus.BAD_REQUEST,
                    "CREW_CAPACITY_INVALID",
                    "크루 정원은 2명 이상 20명 이하여야 합니다.");
        }
    }

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Crew findCrew(Long crewId) {
        return crews.findWithOwnerAndRegionById(crewId)
                .orElseThrow(this::crewNotFound);
    }

    private Crew lockedCrew(Long crewId) {
        return crews.findByIdForUpdate(crewId)
                .orElseThrow(this::crewNotFound);
    }

    private void ensureRecruiting(Crew crew) {
        if (!crew.isRecruiting()) {
            throw new DomainException(
                    HttpStatus.CONFLICT,
                    "CREW_CLOSED",
                    "모집이 종료된 크루입니다.");
        }
    }

    private void ensureOwner(Crew crew, Long userId) {
        if (!crew.getOwner().getId().equals(userId)) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "CREW_OWNER_REQUIRED",
                    "크루장만 크루를 관리할 수 있습니다.");
        }
    }

    private DomainException crewNotFound() {
        return new DomainException(
                HttpStatus.NOT_FOUND,
                "CREW_NOT_FOUND",
                "크루를 찾을 수 없습니다.");
    }

    private UserAccount lockedUser(Long userId) {
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        ensureActive(user);
        return user;
    }

    private UserAccount user(Long userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new DomainException(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        ensureActive(user);
        return user;
    }

    private void ensureActive(UserAccount user) {
        if (!user.isActive()) {
            throw new DomainException(
                    HttpStatus.FORBIDDEN,
                    "USER_INACTIVE",
                    "비활성 계정은 크루 작업을 수행할 수 없습니다.");
        }
    }

    private CrewDtos.Viewer viewer(
            Crew crew,
            Long viewerId,
            CrewMemberStatus membershipStatus,
            long memberCount) {
        if (viewerId == null) {
            return null;
        }

        boolean owner = crew.getOwner().getId().equals(viewerId);
        CrewMemberStatus effectiveStatus = owner ? CrewMemberStatus.OWNER : membershipStatus;
        boolean canJoin = !owner
                && (effectiveStatus == null
                        || effectiveStatus == CrewMemberStatus.REJECTED
                        || effectiveStatus == CrewMemberStatus.CANCELLED)
                && crew.isRecruiting()
                && !travelDatePassed(crew.getTravelDate())
                && memberCount < crew.getCapacity();
        boolean canCancel = effectiveStatus == CrewMemberStatus.PENDING
                || effectiveStatus == CrewMemberStatus.APPROVED;
        boolean canAccessOpenChat = crew.getOpenChatUrl() != null
                && (effectiveStatus == CrewMemberStatus.OWNER
                        || effectiveStatus == CrewMemberStatus.APPROVED);

        return new CrewDtos.Viewer(
                effectiveStatus,
                owner,
                canJoin,
                canCancel,
                owner,
                canAccessOpenChat);
    }

    private Instant joinedOrAppliedAt(CrewMember member) {
        if (member.getStatus() == CrewMemberStatus.PENDING) {
            return member.getUpdatedAt();
        }
        if (member.getStatus() == CrewMemberStatus.APPROVED
                && member.getReviewedAt() != null) {
            return member.getReviewedAt();
        }
        return member.getCreatedAt();
    }

    private CrewDtos.View view(
            Crew crew,
            long memberCount,
            long pendingCount,
            CrewDtos.Viewer viewer) {
        String openChatUrl = viewer != null && viewer.canAccessOpenChat()
                ? crew.getOpenChatUrl()
                : null;
        return new CrewDtos.View(
                crew.getId(),
                crew.getTitle(),
                crew.getRegion().getCode(),
                crew.getRegionName(),
                crew.getDescription(),
                crew.getTravelDate(),
                crew.getCapacity(),
                memberCount,
                pendingCount,
                crew.isRecruiting(),
                crew.isApprovalRequired(),
                crew.getOwner().getId(),
                crew.getOwner().getNickname(),
                crew.getCreatedAt(),
                openChatUrl,
                viewer);
    }

    private CrewDtos.MemberView memberView(CrewMember member) {
        return new CrewDtos.MemberView(
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getProfileImageUrl(),
                member.getStatus() == CrewMemberStatus.OWNER
                        ? CrewDtos.MemberRole.OWNER
                        : CrewDtos.MemberRole.MEMBER,
                joinedOrAppliedAt(member));
    }

    private CrewDtos.ApplicationView applicationView(CrewMember application) {
        return new CrewDtos.ApplicationView(
                application.getId(),
                application.getCrew().getId(),
                application.getUser().getId(),
                application.getUser().getNickname(),
                application.getStatus(),
                application.getReviewedBy() == null
                        ? null
                        : application.getReviewedBy().getId(),
                application.getReviewedAt(),
                application.getCreatedAt());
    }
}
