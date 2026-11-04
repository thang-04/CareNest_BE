package com.carenest.service.observation;

import com.carenest.dto.observation.ThemeEvaluationDtos.ClassItem;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationRequest;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationResponse;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeItem;
import com.carenest.security.CurrentUser;
import java.util.List;

/** Đánh giá cuối chủ đề của lớp mọi nhóm tuổi (OBS-27). */
public interface ThemeEvaluationService {

    /** schoolYearId null = năm học đang hoạt động. */
    List<ClassItem> listClasses(CurrentUser currentUser, Long schoolYearId);

    List<ThemeItem> listThemes(CurrentUser currentUser, Long classId);

    ThemeEvaluationResponse get(CurrentUser currentUser, Long classId, Long themeId);

    ThemeEvaluationResponse save(CurrentUser currentUser, ThemeEvaluationRequest request);

    ThemeEvaluationResponse confirm(CurrentUser currentUser, ThemeEvaluationRequest request);
}
