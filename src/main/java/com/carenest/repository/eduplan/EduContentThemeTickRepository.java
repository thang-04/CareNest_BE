package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduContentThemeTick;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EduContentThemeTickRepository extends JpaRepository<EduContentThemeTick, EduContentThemeTick.Key> {

    List<EduContentThemeTick> findAllByThemeIdIn(Collection<Long> themeIds);

    // Xóa thẳng bằng câu lệnh, không nạp entity: dấu tích ghi lại ngay sau trong cùng transaction
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from EduContentThemeTick t where t.themeId in :themeIds")
    void deleteByThemeIds(@Param("themeIds") Collection<Long> themeIds);
}
