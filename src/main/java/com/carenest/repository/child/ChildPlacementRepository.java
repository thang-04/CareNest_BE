package com.carenest.repository.child;

import com.carenest.entity.child.ChildPlacement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildPlacementRepository extends JpaRepository<ChildPlacement, Long> {

    /** Lịch sử xếp / chuyển lớp của trẻ, mới nhất trước (tab xếp lớp ở hồ sơ trẻ). */
    List<ChildPlacement> findAllByChildIdOrderByPlacedAtDescIdDesc(Long childId);
}
