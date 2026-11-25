package com.carenest.service.meals;

import com.carenest.dto.meals.InspectionApprovalDecisionRequest;
import com.carenest.dto.meals.InspectionApprovalRequest;
import com.carenest.dto.meals.InspectionApprovalResponse;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
import java.time.LocalDate;
import java.util.List;

/** Mở ghi biên bản kiểm thực ngày cũ cần HT duyệt (INS-05): yêu cầu, quyết định và kiểm khi ghi. */
public interface InspectionGrantService {

    /** PHT gửi yêu cầu chờ HT; HT gọi thì mở ngay (tự mở, ghi lịch sử). */
    InspectionApprovalResponse request(
            CurrentUser currentUser,
            Long campusId,
            LocalDate date,
            InspectionTab tab,
            InspectionApprovalRequest request);

    InspectionApprovalResponse approve(
            CurrentUser currentUser, Long approvalId, InspectionApprovalDecisionRequest request);

    InspectionApprovalResponse reject(
            CurrentUser currentUser, Long approvalId, InspectionApprovalDecisionRequest request);

    InspectionApprovalResponse revoke(
            CurrentUser currentUser, Long approvalId, InspectionApprovalDecisionRequest request);

    /** Hàng chờ của HT theo trạng thái (mặc định chờ duyệt); điểm trường trống = mọi điểm trường. */
    List<InspectionApprovalResponse> queue(CurrentUser currentUser, Long campusId, InspectionGrantStatus status);

    /** Trạng thái mở ghi từng mẫu của điểm trường–ngày cho {@code GET /inspections/day}. */
    List<InspectionDayResponse.TabApproval> dayApprovals(CurrentUser currentUser, Long campusId, LocalDate date);

    /** Gọi trong giao dịch ghi (đã giữ khóa điểm trường): ngày đã qua mà chưa có quyền mở còn hạn ⇒ từ chối (INS-05). */
    void requireOpenDay(Long campusId, LocalDate date, InspectionTab tab);

    /** Sau khi ký thành công: đóng quyền mở của mẫu ngày cũ, lần sửa lại sau cần yêu cầu mới (INS-05). */
    void consume(Long campusId, LocalDate date, InspectionTab tab);
}
