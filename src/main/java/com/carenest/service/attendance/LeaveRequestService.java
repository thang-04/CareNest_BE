package com.carenest.service.attendance;

import com.carenest.dto.attendance.LeaveRequestDtos.CreateLeaveRequest;
import com.carenest.dto.attendance.LeaveRequestDtos.LeaveRequestResponse;
import com.carenest.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;

/** Đơn nghỉ của phụ huynh: gửi / hủy (phụ huynh), xem theo phạm vi (GV lớp mình, PHT điểm trường, HT toàn trường). */
public interface LeaveRequestService {

    LeaveRequestResponse createLeaveRequest(CurrentUser currentUser, CreateLeaveRequest request);

    List<LeaveRequestResponse> findMyLeaveRequests(CurrentUser currentUser);

    LeaveRequestResponse cancelLeaveRequest(CurrentUser currentUser, Long leaveRequestId);

    List<LeaveRequestResponse> searchLeaveRequests(
            CurrentUser currentUser, Long classId, Long campusId, LocalDate from, LocalDate to);
}
