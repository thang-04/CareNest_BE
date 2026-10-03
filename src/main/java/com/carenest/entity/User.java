package com.carenest.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Teachers log in by email, parents log in by phone number.
 * Each user has at least one of the two.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 100)
    private String email;

    @Column(name = "phone_number", unique = true, length = 20)
    private String phoneNumber;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    // Storage object key of the avatar image (not a URL).
    private String avatar;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // Default in the DDL so that adding the column to a table with rows works under ddl-auto=update.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // Job title of staff, for display only (kế toán, y tế, bảo vệ...). Permissions come from roles.
    @Column(length = 100)
    private String position;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // Copied into every JWT ("ver" claim). Bumping it rejects all tokens issued before
    // (after a role, login identifier or password change).
    @Column(name = "token_version", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int tokenVersion = 0;

    // True for accounts created with a default/temporary password; cleared after the user changes it.
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private ForgotPassword forgotPassword;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getUsername() {
        return email != null ? email : phoneNumber;
    }

    /**
     * Signs the user out everywhere: tokens carrying the previous version are rejected.
     */
    public void revokeTokens() {
        tokenVersion++;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
