package com.carenest.repository.child;

import com.carenest.entity.child.ChildHealthDeclarationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildHealthDeclarationHistoryRepository extends JpaRepository<ChildHealthDeclarationHistory, Long> {

    List<ChildHealthDeclarationHistory> findAllByChildIdOrderByCreatedAtAscIdAsc(Long childId);
}
