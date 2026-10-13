package com.carenest.service.organization;

import java.util.Optional;

public interface ClassroomService {

    Optional<Long> findCampusId(Long classroomId);
}
