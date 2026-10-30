package com.carenest.repository.observation;

import com.carenest.entity.observation.RewardListItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardListItemRepository extends JpaRepository<RewardListItem, Long> {

    List<RewardListItem> findByListIdIn(Collection<Long> listIds);

    void deleteByListId(Long listId);
}
