package com.carenest.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A person allowed to pick up a child. The parent adds it; the principal or a vice principal approves it.
 */
@Entity
@Table(name = "pickup_persons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    // For example: ông, bà, cô, chú.
    @Column(nullable = false, length = 50)
    private String relationship;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "photo_key", nullable = false)
    private String photoKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PickupStatus status = PickupStatus.PENDING;

    @Column(name = "reject_reason")
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
