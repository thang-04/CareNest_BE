package com.carenest.service.observation;

import com.carenest.dto.observation.ThemeEvaluationDtos.ClassItem;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationRequest;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationResponse;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeItem;
import com.carenest.security.CurrentUser;
import java.util.List;
import java.util.Map;

/** Đánh giá cuối chủ đề của lớp mọi nhóm tuổi (OBS-27). */
public interface ThemeEvaluationService {

    /** schoolYearId null = năm học đang hoạt động. */
    List<ClassItem> listClasses(CurrentUser currentUser, Long schoolYearId);

    List<ThemeItem> listThemes(CurrentUser currentUser, Long classId);

    ThemeEvaluationResponse get(CurrentUser currentUser, Long classId, Long themeId);

    ThemeEvaluationResponse save(CurrentUser currentUser, ThemeEvaluationRequest request);

    ThemeEvaluationResponse confirm(CurrentUser currentUser, ThemeEvaluationRequest request);

    /** Kết quả một MT của trẻ ở bảng cuối chủ đề: đạt / chưa đạt và tên chủ đề lấy kết quả. */
    record ThemeGoalResult(Boolean met, String themeName) {}

    /**
     * Kết quả MT của một trẻ tổng hợp từ các bảng cuối chủ đề của lớp (phiếu cuối năm, OBS-28; không kiểm quyền, nơi gọi
     * tự kiểm): khóa = dòng gốc MT; mỗi MT lấy chủ đề gần nhất có kết quả (bảng đã xác nhận: bản đã chốt).
     */
    Map<Long, ThemeGoalResult> childThemeResults(Long classId, Long childId);
}
