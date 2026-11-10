package com.carenest.service.organization;

import com.carenest.dto.organization.TeacherCandidateResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Nguồn danh sách giáo viên cho phân công lớp/tổ trưởng. Bản hiện tại (phương án C, tạm) lấy từ phân công đang có;
 * khi có hồ sơ nhân sự (role + điểm trường) chỉ cần thay implementation, caller giữ nguyên.
 */
public interface TeacherDirectory {

    List<TeacherCandidateResponse> findTeachers(LocalDate date);
}
