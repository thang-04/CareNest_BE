package com.carenest.entity.organization;

import com.carenest.utils.enums.ClassroomStatus;
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
@Table(name = "classrooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(name = "school_year_id", nullable = false)
    private Long schoolYearId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "age_group_id")
    private Long ageGroupId;

    @Column(name = "room_id")
    private Long roomId;

    private Short capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassroomStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Classroom create(Long schoolYearId, Long campusId) {
        Classroom classroom = new Classroom();
        classroom.schoolYearId = schoolYearId;
        classroom.campusId = campusId;
        classroom.status = ClassroomStatus.ACTIVE;
        return classroom;
    }

    public void changeInfo(String name, Long campusId, Long ageGroupId, Long roomId, short capacity) {
        this.name = name;
        this.campusId = campusId;
        this.ageGroupId = ageGroupId;
        this.roomId = roomId;
        this.capacity = capacity;
    }
}
