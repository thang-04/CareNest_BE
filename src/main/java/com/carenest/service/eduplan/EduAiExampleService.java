package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.YearPlanDetailResponse;
import java.util.List;

public interface EduAiExampleService {
    List<Example> examples(YearPlanDetailResponse target);

    record Example(Long themeId, Long domainId, String theme, String text) {}
}
