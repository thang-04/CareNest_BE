package com.carenest.service.account;

import com.carenest.dto.child.ChildEnrollmentResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.dto.child.ParentAccountLookupResponse;
import com.carenest.security.CurrentUser;

/**
 * Phần tài khoản phụ huynh của tiếp nhận trẻ (AUTH-18, AUTH-20): tra tài khoản theo CCCD, kiểm một người một tài
 * khoản, lưu form tiếp nhận kèm tạo/liên kết tài khoản và gửi SMS. Ở feature account (như GuardianAccountService) vì
 * cần tạo user Keycloak; hồ sơ trẻ ghi qua {@code ChildIntakeService} của feature child.
 */
public interface ChildEnrollmentService {

    /** Tài khoản phụ huynh có CCCD này, hoặc null; trẻ ngoài phạm vi người hỏi chỉ được đếm. */
    ParentAccountLookupResponse findParentAccount(CurrentUser actor, String idNumber);

    /** Kiểm như khi lưu (AUTH-18), không ghi gì; lệch ⇒ 409 kèm các ô {@code guardians.{i}.phone|idNumber}. */
    void checkGuardians(CurrentUser actor, GuardianCheckRequest request);

    /**
     * Lưu form tiếp nhận: hồ sơ trẻ + phụ huynh cùng một giao dịch; người có quyền quản lý tài khoản phụ huynh thì tạo
     * tài khoản cho phụ huynh chưa có và gửi SMS sau khi lưu, không có quyền thì phụ huynh chờ kích hoạt (P-18).
     */
    ChildEnrollmentResponse enroll(CurrentUser actor, EnrollChildRequest request);
}
