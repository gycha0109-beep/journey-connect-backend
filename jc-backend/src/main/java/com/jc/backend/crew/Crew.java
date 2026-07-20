package com.jc.backend.crew;

import com.jc.backend.common.BaseTimeEntity;
import com.jc.backend.region.Region;
import com.jc.backend.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 크루의 기본 정보와 모집 상태를 관리하는 엔티티입니다.
 *
 * <p>region_id는 실제 식별자로 사용하고, region_name은 기존 응답 호환과 표시용으로 함께 유지합니다.
 */
@Entity
@Table(name = "crews", indexes = @Index(name = "crews_region_feed_idx", columnList = "region_id, recruiting, travel_date"))
public class Crew extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    private LocalDate travelDate;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean recruiting = true;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired = true;

    protected Crew() {}

    public Crew(
            UserAccount owner,
            Region region,
            String title,
            String description,
            LocalDate travelDate,
            int capacity,
            boolean approvalRequired) {
        this.owner = owner;
        this.region = region;
        this.title = title;
        this.description = description;
        this.travelDate = travelDate;
        this.capacity = capacity;
        this.approvalRequired = approvalRequired;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public Region getRegion() {
        return region;
    }

    public String getTitle() {
        return title;
    }

    public String getRegionName() {
        return region.getDisplayName();
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isRecruiting() {
        return recruiting;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }
}
