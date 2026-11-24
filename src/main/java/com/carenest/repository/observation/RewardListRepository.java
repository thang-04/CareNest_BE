package com.carenest.repository.observation;

import com.carenest.entity.observation.RewardList;
import com.carenest.utils.enums.RewardStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardListRepository extends JpaRepository<RewardList, Long> {

    List<RewardList> findByClassroomIdIn(Collection<Long> classroomIds);

    Optional<RewardList> findByClassroomId(Long classroomId);

    List<RewardList> findByClassroomIdInAndStatus(Collection<Long> classroomIds, RewardStatus status);

    boolean existsBySchoolYearId(Long schoolYearId);
}
