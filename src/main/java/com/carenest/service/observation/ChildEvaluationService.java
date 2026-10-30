package com.carenest.service.observation;

import com.carenest.dto.observation.EvaluationDtos.DevelopmentChildRow;
import com.carenest.dto.observation.EvaluationDtos.DevelopmentResponse;
import com.carenest.dto.observation.EvaluationDtos.EvaluationDetailResponse;
import com.carenest.dto.observation.EvaluationDtos.EvaluationRow;
import com.carenest.dto.observation.EvaluationDtos.ProgramCompletionResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.EvaluationPeriodType;
import java.time.LocalDate;
import java.util.List;

/** Đánh giá tháng / cuối năm: GV lớp xem bản nháp, sửa, xác nhận; PHT / HT xem bản đã xác nhận trong phạm vi. */
public interface ChildEvaluationService {

    /** GV đang dạy lớp của năm đang hoạt động: tạo bản nháp còn thiếu của các tháng đã kết thúc trước khi trả danh sách. */
    List<EvaluationRow> list(
            CurrentUser currentUser, EvaluationPeriodType periodType, Long schoolYearId, Long classId, LocalDate month);

    EvaluationDetailResponse get(CurrentUser currentUser, Long id);

    EvaluationRow saveContent(CurrentUser currentUser, Long id, String content);

    EvaluationRow confirm(CurrentUser currentUser, Long id, String content);

    EvaluationRow redraft(CurrentUser currentUser, Long id);

    /** Đánh giá cuối năm trẻ lớp 5–6 tuổi, trước khi xác nhận. */
    EvaluationRow markProgram(CurrentUser currentUser, Long id, Boolean completed);

    /** PHT điểm trường mình, HT toàn trường (lọc điểm trường): lớp 5–6 tuổi, số trẻ hoàn thành chương trình. */
    ProgramCompletionResponse programReport(CurrentUser currentUser, Long schoolYearId, Long campusId);

    /** Trẻ đang học các lớp trong phạm vi của năm học (năm đã kết thúc: trẻ tại ngày cuối năm). */
    List<DevelopmentChildRow> developmentChildren(CurrentUser currentUser, Long schoolYearId, Long classId);

    /** Hồ sơ (GV lớp) / tiến độ (PHT, HT) phát triển của trẻ trong khoảng ngày; chỉ gồm đánh giá đã xác nhận. */
    DevelopmentResponse development(CurrentUser currentUser, Long childId, LocalDate start, LocalDate end);
}
