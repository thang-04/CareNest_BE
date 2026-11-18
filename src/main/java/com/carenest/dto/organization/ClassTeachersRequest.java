package com.carenest.dto.organization;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lớp có tối đa 3 giáo viên (SCH-04, Cần chốt 18/11/2026); GV phụ trách chính không bắt buộc khi lưu năm sắp tới, có
 * chọn thì phải là một trong số đó (năm đang học và kích hoạt năm học cần từ 2 giáo viên, có GV phụ trách chính).
 * Danh sách rỗng = gỡ toàn bộ giáo viên của lớp (homeroomTeacherId bỏ trống).
 */
public record ClassTeachersRequest(@NotNull @Size(max = 3) List<@NotNull Long> teacherIds, Long homeroomTeacherId) {}
