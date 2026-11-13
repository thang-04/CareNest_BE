package com.carenest.service.observation;

import com.carenest.dto.observation.WeeklyCommentDtos.ParentWeeklyCommentResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentBoardResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentClassSummary;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentDraftRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSaveRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSendRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentUnlockRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentUnlockResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/**
 * Nhận xét tuần theo trẻ (OBS-29): GV đang dạy lớp viết, gửi phụ huynh; tổ trưởng, PHT, HT xem theo phạm vi; phụ huynh
 * đã liên kết với trẻ xem bản đã gửi.
 */
public interface WeeklyCommentService {

    WeeklyCommentBoardResponse getBoard(CurrentUser currentUser, Long classId, LocalDate weekStart);

    /** Viết nháp theo số liệu tuần; không đụng bản đã gửi, bản cô đã viết chỉ viết lại khi overwrite. */
    WeeklyCommentBoardResponse writeDrafts(CurrentUser currentUser, WeeklyCommentDraftRequest request);

    WeeklyCommentBoardResponse saveDraft(CurrentUser currentUser, WeeklyCommentSaveRequest request);

    /** Gửi 1 trẻ hoặc cả lớp; báo từng phụ huynh đã liên kết với trẻ. */
    WeeklyCommentBoardResponse send(CurrentUser currentUser, WeeklyCommentSendRequest request);

    List<ParentWeeklyCommentResponse> listForParent(CurrentUser currentUser, Long childId);

    /** Sáng ngày học cuối của tuần: tạo nháp theo mẫu câu cho trẻ chưa có nhận xét ở mọi lớp năm đang hoạt động. */
    void createOpenWeekDrafts();

    /** HT / PHT có quyền mở lại ở điểm trường của lớp mở lại lớp – tuần đã khóa tới validUntil (có lý do). */
    WeeklyCommentUnlockResponse unlock(CurrentUser currentUser, WeeklyCommentUnlockRequest request);

    /** Lịch sử mở lại nhận xét tuần, mới nhất trước, theo điểm trường trong phạm vi (như mở lại ngày đánh giá). */
    List<WeeklyCommentUnlockResponse> getUnlocks(
            CurrentUser currentUser, Long campusId, Long classId, Boolean active, LocalDate from, LocalDate to);

    /** Kết thúc sớm lần mở lại còn hiệu lực. */
    WeeklyCommentUnlockResponse revokeUnlock(CurrentUser currentUser, Long unlockId);

    /** BGH: tình hình gửi nhận xét tuần của các lớp trong phạm vi, lớp quá hạn chưa gửi đủ. */
    List<WeeklyCommentClassSummary> getOverview(CurrentUser currentUser, Long campusId, LocalDate weekStart);
}
