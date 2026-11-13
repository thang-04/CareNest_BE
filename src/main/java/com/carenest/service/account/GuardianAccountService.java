package com.carenest.service.account;

import com.carenest.dto.account.AccountResponse;
import com.carenest.dto.child.ActivationCodeResponse;
import com.carenest.dto.child.AddGuardianRequest;
import com.carenest.dto.child.AddGuardianResponse;
import com.carenest.dto.child.ChildGuardianOverviewResponse;
import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.GuardianCandidateResponse;
import com.carenest.dto.child.GuardianResponse;
import com.carenest.dto.child.ParentPasswordResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.UserStatus;
import java.util.List;

/**
 * Tài khoản phụ huynh gắn trẻ (AUTH-18, AUTH-19): username = SĐT, mật khẩu tạm hệ thống sinh, phạm vi theo trẻ. Một
 * người một tài khoản: SĐT của nhân sự ⇒ thêm vai trò phụ huynh vào tài khoản đó.
 */
public interface GuardianAccountService {

    List<GuardianResponse> listGuardians(CurrentUser actor, Long childId);

    /**
     * Màn Tài khoản phụ huynh: trang trẻ như {@code GET /children} (phạm vi, 403 như ChildListService) kèm tài khoản của
     * phụ huynh nhận tài khoản như {@link #listGuardians}, đọc theo lô cho cả trang. Trẻ người xem không quản lý được
     * tài khoản phụ huynh ({@code canManage = false}) ⇒ {@code account} null.
     */
    PageResponse<ChildGuardianOverviewResponse> guardianOverview(CurrentUser actor, ChildListFilter filter);

    /** Tra SĐT trước khi thêm phụ huynh: tài khoản có sẵn hiện họ tên đã che để người nhập xác nhận đúng người. */
    GuardianCandidateResponse findGuardianCandidate(CurrentUser actor, Long childId, String phone);

    AddGuardianResponse addGuardian(CurrentUser actor, Long childId, AddGuardianRequest request);

    void removeGuardian(CurrentUser actor, Long childId, Long userId);

    AccountResponse updateParentStatus(CurrentUser actor, Long userId, UserStatus status);

    ParentPasswordResponse resetParentPassword(CurrentUser actor, Long userId);

    /** Gửi lại mã kích hoạt cho phụ huynh chưa kích hoạt (AUTH-18): cách nhau 60 giây, mã cũ hết hiệu lực. */
    ActivationCodeResponse resendActivationCode(CurrentUser actor, Long userId);
}
