package com.carenest.service.attendance;

import com.carenest.dto.attendance.PickupBoardResponse;
import com.carenest.dto.attendance.PickupChildResponse;
import com.carenest.dto.attendance.PickupRecordResponse;
import com.carenest.dto.attendance.PickupRequest;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/**
 * Đón trẻ, xác nhận trả trẻ (PCK-01..04, ATT-10): GV đang phụ trách lớp trả trẻ có mặt hôm nay sau khi khớp ảnh phụ
 * huynh hoặc phụ huynh xác nhận qua điện thoại; trả xong báo phụ huynh. Chỉ lớp của năm học đang hoạt động.
 */
public interface PickupService {

    PickupBoardResponse getBoard(CurrentUser currentUser, Long classId, LocalDate date);

    PickupChildResponse getChild(CurrentUser currentUser, Long childId);

    PickupRecordResponse recordResult(CurrentUser currentUser, PickupRequest request);

    /** Các lần xác minh trong ngày ở các lớp GV đang phụ trách, mới nhất trước. */
    List<PickupRecordResponse> listResults(CurrentUser currentUser, LocalDate date);
}
