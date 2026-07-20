package com.jc.backend.post;

import com.jc.backend.common.BaseTimeEntity;
import com.jc.backend.region.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "places")
public class Place extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "name_local", nullable = false, length = 200)
    private String nameLocal;

    @Column(name = "name_ko", length = 200)
    private String nameKo;

    @Column(name = "name_en", length = 200)
    private String nameEn;

    @Column(name = "normalized_name", nullable = false, length = 200, insertable = false, updatable = false)
    private String normalizedName;

    @Column(columnDefinition = "text")
    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(length = 50)
    private String category;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Place() {}

    public Place(Region region, String nameLocal) {
        this.region = region;
        this.nameLocal = nameLocal.trim();
        this.nameKo = nameLocal.trim();
        this.nameEn = nameLocal.trim();
    }

    public Long getId() {
        return id;
    }

    public Region getRegion() {
        return region;
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

    public boolean isActive() {
        return active;
    }
}
