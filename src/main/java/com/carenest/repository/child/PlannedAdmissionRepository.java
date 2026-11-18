package com.carenest.repository.child;

import com.carenest.entity.child.PlannedAdmission;
import com.carenest.utils.enums.PlannedAdmissionStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedAdmissionRepository extends JpaRepository<PlannedAdmission, Long> {
    List<PlannedAdmission> findBySchoolYearIdAndStatusInOrderById(
            Long schoolYearId, Collection<PlannedAdmissionStatus> statuses);

    long countByClassroomIdAndStatusIn(Long classroomId, Collection<PlannedAdmissionStatus> statuses);

    List<PlannedAdmission> findByChildIdInAndStatusIn(
            Collection<Long> childIds, Collection<PlannedAdmissionStatus> statuses);

    java.util.Optional<PlannedAdmission> findFirstByChildIdAndStatusIn(
            Long childId, Collection<PlannedAdmissionStatus> statuses);
}
