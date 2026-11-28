package com.carenest.dto.meals;

import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Các biên bản kiểm thực của một điểm trường–ngày. {@code canEdit}: người xem ghi, ký, sửa lại được (INS-01).
 * {@code approvals}: trạng thái mở ghi ngày cũ theo từng mẫu (INS-05), để Web không tự suy quyền từ ngày trên máy.
 */
public record InspectionDayResponse(
        Long campusId,
        LocalDate date,
        boolean canEdit,
        List<InspectionSheetResponse> sheets,
        List<TabApproval> approvals) {

    /**
     * {@code locked}: ngày đã qua và chưa có quyền mở còn hạn nên BE từ chối ghi. {@code status}: yêu cầu gần nhất của
     * mẫu (rỗng = chưa từng yêu cầu; quá hạn trả {@code EXPIRED}). {@code canRequest}: người xem gửi yêu cầu được.
     * {@code selfOpen}: người xem là HT, yêu cầu của họ mở ngay không cần ai duyệt.
     */
    public record TabApproval(
            InspectionTab tab,
            boolean locked,
            InspectionGrantStatus status,
            Long approvalId,
            String reason,
            String requestedByName,
            String decisionNote,
            Instant expiresAt,
            boolean canRequest,
            boolean selfOpen) {}
}
