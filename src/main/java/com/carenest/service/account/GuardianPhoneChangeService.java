package com.carenest.service.account;

import com.carenest.dto.account.GuardianPhoneChangeCreateRequest;
import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.account.PhoneChangeDecisionRequest;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.PhoneChangeStatus;
import java.util.List;

/**
 * Yêu cầu đổi SĐT phụ huynh trên hồ sơ trẻ (G94-180/AUTH-21; user chốt 2026-11-10, sửa 2026-11-11): mọi phụ huynh, có
 * hay chưa có tài khoản, đổi số qua yêu cầu. Người duyệt: HT, hoặc PHT có quyền quản lý tài khoản phụ huynh ở điểm trường
 * hiện tại của trẻ; người gửi có quyền duyệt thì tự duyệt được.
 */
public interface GuardianPhoneChangeService {

    /** Yêu cầu của trẻ trong phạm vi người xem, mới nhất trước; {@code status} null ⇒ mọi trạng thái. */
    List<GuardianPhoneChangeResponse> list(CurrentUser actor, PhoneChangeStatus status);

    /**
     * Gửi yêu cầu đổi SĐT một phụ huynh của trẻ: phạm vi như Sửa hồ sơ, trẻ chưa nghỉ. Số mới hợp lệ, khác số đang lưu,
     * không thuộc phụ huynh/tài khoản khác. Yêu cầu đang chờ của cùng phụ huynh bị thay (CANCELLED). Báo người duyệt.
     */
    GuardianPhoneChangeResponse request(
            CurrentUser actor, Long childId, Long guardianId, GuardianPhoneChangeCreateRequest body);

    /**
     * Duyệt. Phụ huynh có tài khoản: tên đăng nhập = SĐT mới (Keycloak + DB), mật khẩu tạm mới, hủy mọi phiên, SĐT mới
     * trên mọi hồ sơ trẻ của phụ huynh; sau commit gửi SMS tới SĐT mới. Chưa có tài khoản: chỉ đổi SĐT trên dòng phụ
     * huynh đó, không SMS. Số mới đã thuộc người khác, hoặc tài khoản/hồ sơ đã đổi từ lúc gửi ⇒ 409.
     */
    GuardianPhoneChangeResponse approve(CurrentUser actor, Long requestId);

    /** Từ chối: giữ SĐT cũ; người gửi (hoặc người có quyền khác) từ chối được, ghi chú tùy chọn tối đa 300 ký tự. */
    GuardianPhoneChangeResponse reject(CurrentUser actor, Long requestId, PhoneChangeDecisionRequest request);
}
