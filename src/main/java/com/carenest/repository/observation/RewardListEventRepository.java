package com.carenest.repository.observation;

import com.carenest.entity.observation.RewardListEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardListEventRepository extends JpaRepository<RewardListEvent, Long> {

    List<RewardListEvent> findByListIdOrderByAtAscIdAsc(Long listId);
}
