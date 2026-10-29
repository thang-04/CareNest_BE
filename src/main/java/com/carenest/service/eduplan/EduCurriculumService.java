package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.CurriculumContentResponse;
import com.carenest.dto.eduplan.CurriculumGoalRef;
import com.carenest.dto.eduplan.CurriculumGoalResponse;
import com.carenest.dto.eduplan.CurriculumSourceResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Danh mục Chương trình GDMN của Bộ (EDU-28): chỉ đọc. */
public interface EduCurriculumService {

    List<CurriculumSourceResponse> getSources();

    List<CurriculumGoalResponse> getGoals(Long ageGroupId);

    List<CurriculumContentResponse> getContents(Long ageGroupId);

    /** Mục tiêu của Bộ theo id (để kế hoạch năm kiểm nhóm tuổi và trả câu gốc). */
    Map<Long, CurriculumGoalRef> findGoalRefs(Collection<Long> ids);
}
