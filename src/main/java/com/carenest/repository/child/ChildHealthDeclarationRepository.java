package com.carenest.repository.child;

import com.carenest.entity.child.ChildHealthDeclaration;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChildHealthDeclarationRepository extends JpaRepository<ChildHealthDeclaration, Long> {

    /** Trẻ đã có khai báo sức khỏe trong các trẻ cho trước (danh sách trẻ, {@code hasDeclaration}). */
    @Query("select d.childId from ChildHealthDeclaration d where d.childId in :childIds")
    List<Long> findDeclaredChildIds(@Param("childIds") Collection<Long> childIds);
}
