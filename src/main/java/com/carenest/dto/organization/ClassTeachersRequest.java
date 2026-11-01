package com.carenest.dto.organization;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lớp có tối đa 2 giáo viên; chủ nhiệm không bắt buộc, có chọn thì phải là một trong số đó
 * (kích hoạt năm học vẫn cần đủ 2 giáo viên và có chủ nhiệm).
 * Danh sách rỗng = gỡ toàn bộ giáo viên của lớp (homeroomTeacherId bỏ trống).
 */
public record ClassTeachersRequest(@NotNull @Size(max = 2) List<@NotNull Long> teacherIds, Long homeroomTeacherId) {}
