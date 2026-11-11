package com.carenest.service.account;

import com.carenest.dto.child.ChildUpdateResponse;
import com.carenest.dto.child.UpdateChildRequest;
import com.carenest.security.CurrentUser;

/**
 * Sửa hồ sơ trẻ ({@code PUT /children/{id}}, mục 4.2 plan tiếp nhận) kèm tài khoản phụ huynh: họ tên, email, CCCD của
 * phụ huynh đã có tài khoản cập nhật tài khoản ngay (cần quyền quản lý tài khoản phụ huynh); SĐT thành yêu cầu chờ duyệt
 * (G94-180/AUTH-21, user chốt 2026-11-10).
 */
public interface ChildProfileEditService {

    ChildUpdateResponse update(CurrentUser actor, Long childId, UpdateChildRequest request);
}
