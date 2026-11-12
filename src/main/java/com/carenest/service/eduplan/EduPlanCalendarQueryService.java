package com.carenest.service.eduplan;

import java.time.LocalDate;
import java.util.List;

public interface EduPlanCalendarQueryService {

    record EffectiveTheme(
            Long themeId, Long lineageId, Long teamPlanId, String name, LocalDate startDate, LocalDate endDate) {}

    /**
     * Trả về danh sách chủ đề hiệu lực của lớp:
     * - Nếu lớp có tài liệu branch đã APPROVED có lịch v2 (themeSchedules): lấy ngày theo lịch lớp đã duyệt.
     * - Nếu chưa có lịch lớp đã duyệt (chưa tạo, còn DRAFT/REVIEW hoặc legacy): lấy lịch theo kế hoạch TEAM đã ban hành.
     */
    List<EffectiveTheme> findApprovedThemes(Long classroomId);
}
