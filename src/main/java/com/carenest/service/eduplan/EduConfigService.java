package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.eduplan.ExpectedResultResponse;
import java.util.List;

/** Dữ liệu tham chiếu của kế hoạch giáo dục (SD-09). */
public interface EduConfigService {

    EduConfigResponse getEduConfig();

    List<ExpectedResultResponse> getExpectedResults(Long ageGroupId);
}
