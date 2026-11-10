package com.carenest.entity.organization;

import com.carenest.utils.enums.RoomStatus;
import com.carenest.utils.enums.RoomType;
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
@Table(name = "rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_id", nullable = false)
    private Long campusId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private RoomType roomType;

    private Short capacity;

    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(nullable = false, length = 500)
    private String note;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Room create(
            Long campusId,
            String code,
            String name,
            RoomType roomType,
            Short capacity,
            Long managerUserId,
            String note) {
        Room room = new Room();
        room.status = RoomStatus.ACTIVE;
        room.changeInfo(campusId, code, name, roomType, capacity, managerUserId, note);
        return room;
    }

    public void changeInfo(
            Long campusId,
            String code,
            String name,
            RoomType roomType,
            Short capacity,
            Long managerUserId,
            String note) {
        this.campusId = campusId;
        this.code = code;
        this.name = name;
        this.roomType = roomType;
        this.capacity = capacity;
        this.managerUserId = managerUserId;
        this.note = note;
    }

    public void changeStatus(RoomStatus status) {
        this.status = status;
    }

    public void markDeleted(Instant at) {
        this.status = RoomStatus.DELETED;
        this.deletedAt = at;
    }
}
