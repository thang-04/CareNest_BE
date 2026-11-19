package com.carenest.service.observation;

import com.carenest.dto.observation.SemesterAssessmentDtos.*;
import com.carenest.security.CurrentUser;

public interface SemesterAssessmentService {
    Response get(CurrentUser user, Long classId, int semester);

    Response save(CurrentUser user, Request request);

    Response confirm(CurrentUser user, Request request);
}
