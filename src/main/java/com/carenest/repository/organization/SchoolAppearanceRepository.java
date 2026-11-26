package com.carenest.repository.organization;

import com.carenest.entity.organization.SchoolAppearance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolAppearanceRepository extends JpaRepository<SchoolAppearance, Short> {

    @Query(value = "select u.full_name from user_accounts u where u.id = :id", nativeQuery = true)
    Optional<String> findUserFullName(@Param("id") Long id);
}
