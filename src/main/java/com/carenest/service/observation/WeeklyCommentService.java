package com.carenest.service.observation;

import com.carenest.dto.observation.WeeklyCommentDtos.ParentWeeklyCommentResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentBoardResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentDraftRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentReopenRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentReopenResponse;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSaveRequest;
import com.carenest.dto.observation.WeeklyCommentDtos.WeeklyCommentSendRequest;
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

    /** HT / PHT có quyền mở lại ở điểm trường của lớp đưa bản đã gửi về nháp (có lý do); trả các lần mở lại vừa tạo. */
    List<WeeklyCommentReopenResponse> reopen(CurrentUser currentUser, WeeklyCommentReopenRequest request);

    /** Lịch sử mở lại nhận xét tuần, mới nhất trước: BGH theo điểm trường trong phạm vi, GV theo lớp đang dạy. */
    List<WeeklyCommentReopenResponse> getReopens(
            CurrentUser currentUser, Long campusId, Long classId, LocalDate from, LocalDate to);
}
