package com.carenest.service.organization;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ClassroomService {

    Optional<Long> findCampusId(Long classroomId);

    /** classroomId → campusId cho các lớp có tồn tại. */
    Map<Long, Long> findCampusIds(Collection<Long> classroomIds);

    /** Mọi lớp (mọi năm học) thuộc các campus; lọc theo ngày do nơi gọi áp qua enrollment. */
    List<Long> findClassroomIdsOfCampuses(Collection<Long> campusIds);
}
