package com.carenest.service.observation;

import com.carenest.dto.observation.ThemeEvaluationDtos.ClassItem;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationRequest;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeEvaluationResponse;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeItem;
import com.carenest.dto.observation.ThemeEvaluationDtos.ThemeSigners;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
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

    /**
     * Họ tên in ở chỗ ký (GV chủ nhiệm / các GV lớp, tổ trưởng nhóm tuổi, PHT ban hành kế hoạch năm) tại ngày at; dùng cho
     * bản in cuối chủ đề và đánh giá học kỳ (không kiểm quyền, nơi gọi tự kiểm).
     */
    ThemeSigners signersAt(Long classId, LocalDate at);

    /** Kết quả cuối năm của một MT, tổng hợp theo số chủ đề có gắn MT; className = các lớp cũ nếu có. */
    record ThemeGoalResult(Boolean met, int metThemes, int totalThemes, String themeNames, String className) {}

    /**
     * Kết quả MT của một trẻ tổng hợp từ các bảng cuối chủ đề (phiếu cuối năm, OBS-28; không kiểm quyền, nơi gọi tự kiểm):
     * khóa = dòng gốc MT; gồm lớp classId và các lớp trẻ đã học trong năm cùng nhóm tuổi. Mỗi chủ đề có gắn MT tính một
     * kết quả; MT cuối năm đạt khi ít nhất 50% chủ đề đạt. Bảng chủ đề đã xác nhận dùng snapshot đã chốt.
     */
    Map<Long, ThemeGoalResult> childThemeResults(Long classId, Long childId);

    /**
     * Như trên nhưng chỉ các chủ đề đã xong có ngày kết thúc trong [from, to] (bảng học kỳ, OBS-25 Cần chốt 10/11/2026).
     */
    Map<Long, ThemeGoalResult> childThemeResults(Long classId, Long childId, LocalDate from, LocalDate to);
}
