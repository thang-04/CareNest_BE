package com.carenest.dto.organization;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Mỗi lớp đúng 2 giáo viên; chủ nhiệm là một trong 2 người, do người phân công chọn. */
public record ClassTeachersRequest(
        @NotNull @Size(min = 2, max = 2) List<@NotNull Long> teacherIds,
        @NotNull Long homeroomTeacherId) {}
