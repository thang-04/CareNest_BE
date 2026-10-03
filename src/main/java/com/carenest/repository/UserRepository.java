package com.carenest.repository;

import com.carenest.entity.Role;
import com.carenest.entity.User;
import com.carenest.entity.UserStatus;
import com.carenest.utils.IdentifierUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT u.status AS status, u.tokenVersion AS tokenVersion FROM User u WHERE u.id = ?1")
    Optional<UserAuthState> findAuthStateById(Long id);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.lastLoginAt = ?2 WHERE u.id = ?1")
    void updateLastLoginAt(Long id, Instant at);

    // keyword is a lower-case LIKE pattern ("%lan%") or null.
    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR :role MEMBER OF u.roles)
              AND (:status IS NULL OR u.status = :status)
              AND (:keyword IS NULL OR LOWER(u.fullName) LIKE :keyword
                   OR LOWER(u.email) LIKE :keyword OR u.phoneNumber LIKE :keyword)
            """)
    Page<User> search(@Param("role") Role role, @Param("status") UserStatus status,
                      @Param("keyword") String keyword, Pageable pageable);

    // Also clears mustChangePassword: the user has just chosen a new password.
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.password = ?2, u.mustChangePassword = false WHERE u.id = ?1")
    void updatePassword(Long id, String password);

    /**
     * Finds a user by email (contains "@") or by phone number.
     */
    default Optional<User> findByIdentifier(String identifier) {
        return IdentifierUtils.isEmail(identifier)
                ? findByEmail(IdentifierUtils.normalizeEmail(identifier))
                : findByPhoneNumber(IdentifierUtils.normalizePhone(identifier));
    }
}
