package com.carenest.service.account;

import com.carenest.dto.account.ParentActivationRequest;
import com.carenest.dto.account.ParentActivationResponse;

/**
 * Phụ huynh kích hoạt tài khoản bằng mã SMS (AUTH-18, ADR-0026). Không cần đăng nhập; mọi trường hợp không hợp lệ trả
 * cùng một lỗi để không lộ SĐT nào có tài khoản.
 */
public interface ParentActivationService {

    ParentActivationResponse activate(ParentActivationRequest request);
}
