package com.carenest.entity.child;

import com.carenest.utils.enums.PlacementAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Entity
@Table(name = "class_placements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(name = "from_classroom_id", nullable = false)
    private Long fromClassroomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlacementAction action;

    @Column(name = "to_classroom_id")
    private Long toClassroomId;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ClassPlacement create(Long schoolYearId, Long childId) {
        ClassPlacement placement = new ClassPlacement();
        placement.schoolYearId = schoolYearId;
        placement.childId = childId;
        return placement;
    }

    public void decide(Long fromClassroomId, PlacementAction action, Long toClassroomId, Long updatedBy) {
        this.fromClassroomId = fromClassroomId;
        this.action = action;
        this.toClassroomId = action.keepsChildInClass() ? toClassroomId : null;
        this.updatedBy = updatedBy;
    }
}
