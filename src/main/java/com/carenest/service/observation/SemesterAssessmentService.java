package com.carenest.service.observation;

import com.carenest.dto.observation.SemesterAssessmentDtos.*;
import com.carenest.security.CurrentUser;

public interface SemesterAssessmentService {
    Response get(CurrentUser user, Long classId, int semester);

    Response save(CurrentUser user, Request request);

    /** Bảng tính lại để sửa bản đã xác nhận; chưa ghi gì cho tới khi xác nhận lại. */
    Response revision(CurrentUser user, Long classId, int semester);

    Response confirm(CurrentUser user, Request request);
}
