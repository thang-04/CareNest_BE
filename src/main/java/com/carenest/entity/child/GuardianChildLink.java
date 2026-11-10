package com.carenest.entity.child;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Entity
@Table(name = "guardian_child_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardianChildLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Column(nullable = false, length = 30)
    private String relationship;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static GuardianChildLink create(Long userId, Long childId, String relationship) {
        GuardianChildLink link = new GuardianChildLink();
        link.userId = userId;
        link.childId = childId;
        link.relationship = relationship;
        return link;
    }
}
