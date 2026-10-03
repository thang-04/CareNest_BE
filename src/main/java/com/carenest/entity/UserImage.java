package com.carenest.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Extra images of a user (for example ID card or certificates). The avatar is kept on User.
 */
@Entity
@Table(name = "user_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(length = 100)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
