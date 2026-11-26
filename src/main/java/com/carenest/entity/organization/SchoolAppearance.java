package com.carenest.entity.organization;

import com.carenest.utils.enums.AppearanceMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Cấu hình tranh nền chung của trường; bảng chỉ có một dòng (id = 1). */
@Getter
@Entity
@Table(name = "school_appearance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolAppearance {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppearanceMode mode;

    @Column(name = "pinned_scene_id", nullable = false, length = 8)
    private String pinnedSceneId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "enabled_scene_ids", nullable = false, columnDefinition = "text[]")
    private List<String> enabledSceneIds;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static SchoolAppearance create() {
        return new SchoolAppearance();
    }

    public void apply(
            AppearanceMode mode, String pinnedSceneId, List<String> enabledSceneIds, Long updatedBy, Instant at) {
        this.mode = mode;
        this.pinnedSceneId = pinnedSceneId;
        this.enabledSceneIds = List.copyOf(enabledSceneIds);
        this.updatedBy = updatedBy;
        this.updatedAt = at;
    }
}
