package com.carenest.service.account;

import com.carenest.dto.account.ChildTeacherResponse;
import com.carenest.security.CurrentUser;
import java.util.List;

public interface ChildTeacherService {

    /** Giáo viên đang dạy lớp hiện tại của trẻ, chủ nhiệm trước (AUTH-04: chỉ phụ huynh liên kết). */
    List<ChildTeacherResponse> getTeachers(CurrentUser currentUser, Long childId);
}
