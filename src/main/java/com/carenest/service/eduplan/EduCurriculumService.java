package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.CurriculumContentResponse;
import com.carenest.dto.eduplan.CurriculumGoalResponse;
import com.carenest.dto.eduplan.CurriculumRef;
import com.carenest.dto.eduplan.CurriculumSourceResponse;
import com.carenest.utils.enums.EduGoalRowKind;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Danh mục Chương trình GDMN của Bộ (EDU-28): chỉ đọc. */
public interface EduCurriculumService {

    List<CurriculumSourceResponse> getSources();

    List<CurriculumGoalResponse> getGoals(Long ageGroupId);

    List<CurriculumContentResponse> getContents(Long ageGroupId);

    /** Dòng danh mục của Bộ theo tầng và id, để kế hoạch năm kiểm nhóm tuổi và trả câu gốc. */
    Map<Long, CurriculumRef> findRefs(EduGoalRowKind kind, Collection<Long> ids);
}
