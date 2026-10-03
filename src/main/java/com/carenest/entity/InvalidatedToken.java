package com.carenest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

/**
 * Blacklist of revoked JWTs (logout, rotated refresh tokens). Keyed by the token's jti.
 */
@Entity
@Table(name = "invalidated_tokens")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class InvalidatedToken {

    @Id
    private String id;

    @Column(name = "expiration_time", nullable = false)
    private Instant expirationTime;
}
