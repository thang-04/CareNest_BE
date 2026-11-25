package com.carenest.repository.child;

import com.carenest.entity.child.ChildPlacement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildPlacementRepository extends JpaRepository<ChildPlacement, Long> {

    /** Lịch sử xếp / chuyển lớp của trẻ, mới nhất trước (tab xếp lớp ở hồ sơ trẻ). */
    List<ChildPlacement> findAllByChildIdOrderByPlacedAtDescIdDesc(Long childId);

    /** Dòng lịch sử của lớp chưa bắt đầu (lịch chuyển / ngày bắt đầu tương lai) còn hiệu lực (G94-180/CHD-14). */
    Optional<ChildPlacement> findFirstByChildIdAndToClassroomIdAndEffectiveFromAndCancelledAtIsNullOrderByIdDesc(
            Long childId, Long toClassroomId, LocalDate effectiveFrom);
}
