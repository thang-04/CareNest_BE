package com.carenest.repository.eduplan;

import com.carenest.entity.eduplan.EduResourceItemTag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduResourceItemTagRepository extends JpaRepository<EduResourceItemTag, Long> {
    List<EduResourceItemTag> findAllByItemIdIn(Collection<Long> itemIds);

    void deleteAllByItemId(Long itemId);
}
