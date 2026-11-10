package com.carenest.service.child;

import com.carenest.dto.child.ChildEditDraft;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.dto.child.WithdrawChildRequest;
import com.carenest.security.CurrentUser;

/**
 * Thay đổi hồ sơ trẻ sau tiếp nhận (G94-180): cho nghỉ học, Sửa hồ sơ. Phần tài khoản phụ huynh (sửa tài khoản, yêu cầu
 * đổi SĐT) do feature account điều phối; feature này chỉ ghi dữ liệu trẻ.
 */
public interface ChildRecordService {

    /**
     * CHD-03: trẻ Đã nghỉ từ {@code leftAt}, đóng lớp đang học, lịch sử WITHDRAWN, báo giáo viên lớp; không xóa dữ liệu.
     * Phụ huynh mất quyền xem trẻ ngay; tài khoản chỉ là phụ huynh không còn con đang học bị khóa (CHD-04).
     */
    ChildViewResponse withdraw(CurrentUser actor, Long childId, WithdrawChildRequest request);

    /**
     * Kiểm Sửa hồ sơ (mục 4.2 plan tiếp nhận): phạm vi, trẻ chưa nghỉ, lỗi theo ô (400), ngày sinh không sau ngày tiếp
     * nhận, không bỏ phụ huynh đang liên kết tài khoản, trùng hồ sơ khác (409 CHD-04). Ghép phụ huynh gửi lên với dòng
     * có sẵn theo id, rồi CCCD, rồi vị trí. Không ghi gì.
     */
    ChildEditDraft prepareEdit(CurrentUser actor, Long childId, UpdateChildRequest request);

    /**
     * Ghi bản đã kiểm (trẻ, hành chính, phụ huynh, lịch sử UPDATED kèm từng ô đổi); chạy trong giao dịch của người gọi.
     * SĐT ghi theo {@code draft} — người gọi giữ số cũ cho phụ huynh đang chờ duyệt đổi SĐT.
     */
    void applyEdit(CurrentUser actor, ChildEditDraft draft);

    /** Chép họ tên, email, CCCD của tài khoản sang mọi dòng phụ huynh đã liên kết tài khoản đó (AUTH-21). */
    void syncGuardianAccountInfo(Long accountId, String fullName, String email, String citizenId);

    /** Đổi SĐT trên mọi dòng phụ huynh đã liên kết tài khoản (yêu cầu đổi SĐT được duyệt); chạy trong giao dịch người gọi. */
    void changeGuardianPhone(Long accountId, String newPhone);
}
