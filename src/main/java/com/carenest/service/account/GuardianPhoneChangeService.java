package com.carenest.service.account;

import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.account.PhoneChangeDecisionRequest;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.PhoneChangeStatus;
import java.util.List;

/**
 * Duyệt yêu cầu đổi SĐT phụ huynh đã có tài khoản (G94-180/AUTH-21, user chốt 2026-11-10). Người duyệt: HT, hoặc PHT
 * có quyền quản lý tài khoản phụ huynh ở điểm trường hiện tại của trẻ; người gửi không tự duyệt.
 */
public interface GuardianPhoneChangeService {

    /** Yêu cầu của trẻ trong phạm vi người xem, mới nhất trước; {@code status} null ⇒ mọi trạng thái. */
    List<GuardianPhoneChangeResponse> list(CurrentUser actor, PhoneChangeStatus status);

    /**
     * Duyệt: tên đăng nhập = SĐT mới (Keycloak + DB), mật khẩu tạm mới, hủy mọi phiên, SĐT mới trên mọi hồ sơ trẻ của
     * phụ huynh; sau commit gửi SMS tới SĐT mới. Số mới đã thuộc người khác hoặc tài khoản đã đổi từ lúc gửi ⇒ 409.
     */
    GuardianPhoneChangeResponse approve(CurrentUser actor, Long requestId);

    /** Từ chối: giữ SĐT cũ; người gửi (hoặc người có quyền khác) từ chối được, ghi chú tùy chọn tối đa 300 ký tự. */
    GuardianPhoneChangeResponse reject(CurrentUser actor, Long requestId, PhoneChangeDecisionRequest request);
}
