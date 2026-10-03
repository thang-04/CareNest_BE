package com.carenest.repository;

import com.carenest.entity.Child;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChildRepository extends JpaRepository<Child, Long> {

    boolean existsByParentIdAndCreatedById(Long parentId, Long createdById);

    List<Child> findByParentIdOrderByIdAsc(Long parentId);

    Optional<Child> findByIdAndParentId(Long id, Long parentId);

    boolean existsByParentId(Long parentId);

    boolean existsByCreatedById(Long createdById);

    // keyword is a lower-case LIKE pattern ("%na%") or null; matches the child's or the parent's name or phone.
    @EntityGraph(attributePaths = "parent")
    @Query("""
            SELECT c FROM Child c
            WHERE :keyword IS NULL OR LOWER(c.fullName) LIKE :keyword
               OR LOWER(c.parent.fullName) LIKE :keyword OR c.parent.phoneNumber LIKE :keyword
            """)
    Page<Child> search(@Param("keyword") String keyword, Pageable pageable);
}
