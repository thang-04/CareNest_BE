package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.EduAiRequest;
import com.carenest.dto.eduplan.EduAiResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

/** AI hỗ trợ soạn và kế hoạch giáo dục (EDU-95): chỉ trả nháp, không ghi DB. */
public interface EduAiService {

    EduAiResponse.LessonSteps suggestLessonSteps(CurrentUser currentUser, EduAiRequest.LessonSteps request);

    List<EduAiResponse.Activity> suggestActivities(CurrentUser currentUser, EduAiRequest.Activities request);

    /** Sửa theo yêu cầu một giáo án ngày (người soạn, khi còn sửa được): danh sách ô cũ → mới, không lưu. */
    EduAiResponse.DayEdit editDay(CurrentUser currentUser, String docKey, EduAiRequest.EditDay request);

    /** EDU-103: gợi ý gom nội dung vào chủ đề của kế hoạch tổ (người tick được ở bản đang soạn), không lưu. */
    EduAiResponse.ThemeContentMap matchThemeContents(
            CurrentUser currentUser, Long planId, EduAiRequest.ThemeContentMap request);

    /** EDU-103: ghi log số gợi ý / số giữ của một lần Áp dụng; không lưu DB. */
    void logFeedback(CurrentUser currentUser, EduAiRequest.Feedback request);

    EduAiResponse.GoalContentMap matchGoalContents(CurrentUser currentUser, Long planId);
}
