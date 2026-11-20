package com.carenest.service.eduplan;

import com.carenest.dto.eduplan.EduAiRequest;
import com.carenest.dto.eduplan.EduAiResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

/** AI hỗ trợ soạn và duyệt kế hoạch giáo dục (EDU-95, EDU-96): chỉ trả nháp, không ghi DB. */
public interface EduAiService {

    EduAiResponse.BranchNames suggestBranchNames(CurrentUser currentUser, EduAiRequest.BranchNames request);

    EduAiResponse.BranchName rewriteBranchName(CurrentUser currentUser, EduAiRequest.RewriteBranchName request);

    EduAiResponse.LessonSteps suggestLessonSteps(CurrentUser currentUser, EduAiRequest.LessonSteps request);

    List<EduAiResponse.Activity> suggestActivities(CurrentUser currentUser, EduAiRequest.Activities request);

    /** Sửa theo yêu cầu một giáo án ngày (người soạn, khi còn sửa được): danh sách ô cũ → mới, không lưu. */
    EduAiResponse.DayEdit editDay(CurrentUser currentUser, String docKey, EduAiRequest.EditDay request);

    /** Tóm tắt giáo án ngày cho người duyệt / người soạn: gợi ý, không lưu. */
    EduAiResponse.DayReview reviewDay(CurrentUser currentUser, String docKey);
}
