package com.jc.backend.region;

import com.jc.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.util.Locale;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.PrecisionModel;

/** Canonical regions 계층을 매핑하며 기존 API의 code/displayName 계약을 유지합니다. */
@Entity
@Table(name = "regions")
public class Region extends BaseTimeEntity {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Region parent;

    @Column(name = "name_local", nullable = false, length = 100)
    private String nameLocal;

    @Column(name = "name_ko", length = 100)
    private String nameKo;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(nullable = false, length = 160)
    private String slug;

    @Column(name = "region_type", nullable = false, length = 20)
    private String regionType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @Column(length = 64)
    private String timezone;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "center_latitude", precision = 9, scale = 6)
    private BigDecimal centerLatitude;

    @Column(name = "center_longitude", precision = 9, scale = 6)
    private BigDecimal centerLongitude;

    protected Region() {}

    /** H2 기반 기존 테스트와 초기 데이터 작성 호환 생성자입니다. */
    public Region(String code, String countryCode, String displayName, Point center) {
        this.slug = normalizeSlug(code);
        this.countryCode = countryCode.trim().toUpperCase(Locale.ROOT);
        this.nameLocal = displayName.trim();
        this.nameKo = displayName.trim();
        this.nameEn = displayName.trim();
        this.regionType = "city";
        this.timezone = "KR".equals(this.countryCode) ? "Asia/Seoul" : null;
        if (center != null) {
            this.centerLongitude = BigDecimal.valueOf(center.getX());
            this.centerLatitude = BigDecimal.valueOf(center.getY());
        }
    }

    public Long getId() {
        return id;
    }

    /** 기존 대문자 코드 계약은 유지하되 DB에는 lowercase slug를 저장합니다. */
    public String getCode() {
        return slug.toUpperCase(Locale.ROOT);
    }

    public String getSlug() {
        return slug;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getDisplayName() {
        if (nameKo != null && !nameKo.isBlank()) {
            return nameKo;
        }
        if (nameEn != null && !nameEn.isBlank()) {
            return nameEn;
        }
        return nameLocal;
    }

    public String getNameLocal() {
        return nameLocal;
    }

    public Region getParent() {
        return parent;
    }

    public boolean isActive() {
        return active;
    }

    public Double getCenterLatitude() {
        return centerLatitude == null ? null : centerLatitude.doubleValue();
    }

    public Double getCenterLongitude() {
        return centerLongitude == null ? null : centerLongitude.doubleValue();
    }

    @Transient
    public Point getCenter() {
        if (centerLatitude == null || centerLongitude == null) {
            return null;
        }
        return GEOMETRY_FACTORY.createPoint(new Coordinate(
                centerLongitude.doubleValue(), centerLatitude.doubleValue()));
    }

    private static String normalizeSlug(String code) {
        return code.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
