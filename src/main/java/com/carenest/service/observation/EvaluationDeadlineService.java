package com.carenest.service.observation;

import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationPeriod;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationUnlockRequest;
import com.carenest.dto.observation.EvaluationDeadlineDtos.EvaluationUnlockResponse;
import com.carenest.dto.observation.EvaluationDeadlineDtos.OverdueClassItem;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.EvaluationLockKind;
import java.time.LocalDate;
import java.util.List;

/**
 * Hạn, khóa và mở lại đánh giá cuối chủ đề / học kỳ / cuối năm theo lớp – kỳ (OBS-06, OBS-27, OBS-28): mở từ ngày kết
 * thúc kỳ, khóa sau 23:59:59 của ngày học thứ N sau ngày đó (N cố định: 3 / 5 / 5); HT / PHT có quyền mở lại tới hạn.
 */
public interface EvaluationDeadlineService {

    /** Các kỳ đánh giá của lớp (chủ đề đã duyệt, học kỳ I, II, cuối năm) kèm hạn, khóa, đang mở lại. */
    List<EvaluationPeriod> getPeriods(CurrentUser currentUser, Long classId);

    /** Các kỳ của lớp, không kiểm quyền người xem (cho service khác, việc nhắc). */
    List<EvaluationPeriod> periodsOf(ClassroomInfoResponse cls);

    /** Quá hạn và không có lần mở lại còn hiệu lực ⇒ EVALUATION_LOCKED; kỳ không xác định được thì bỏ qua. */
    void checkWritable(ClassroomInfoResponse cls, EvaluationLockKind kind, String periodKey);

    /** Khóa học kỳ của một đánh giá học kỳ theo ngày kết thúc kỳ: 1 nếu là học kỳ I, còn lại 2. */
    String semesterKey(ClassroomInfoResponse cls, LocalDate periodEnd);

    EvaluationUnlockResponse unlock(CurrentUser currentUser, EvaluationUnlockRequest request);

    List<EvaluationUnlockResponse> getUnlocks(
            CurrentUser currentUser, Long campusId, Long classId, EvaluationLockKind kind, Boolean active);

    EvaluationUnlockResponse revokeUnlock(CurrentUser currentUser, Long unlockId);

    /** BGH: lớp quá hạn chưa xong (nhận xét tuần tuần trước, chủ đề, học kỳ, cuối năm) trong phạm vi điểm trường. */
    List<OverdueClassItem> getOverdue(CurrentUser currentUser, Long campusId);

    /** Kỳ đã khóa (không đang mở lại) mà còn việc dở của các lớp (BGH nhắc), không kiểm quyền. */
    List<OverdueClassItem> overdueOf(List<ClassroomInfoResponse> classes);

    /** {đã xác nhận, cần làm} của một lớp – kỳ: chủ đề = 1 bảng; học kỳ, cuối năm = số trẻ của lớp tới hết kỳ. */
    int[] progress(ClassroomInfoResponse cls, EvaluationPeriod period);
}
