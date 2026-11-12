package com.carenest.dto.eduplan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** EDU-84: đổi phạm vi học liệu — MINE, CLASS (kèm lớp), SCHOOL (đề nghị hoặc PHT đưa thẳng). */
public record ResourceShareRequest(@NotBlank @Pattern(regexp = "MINE|CLASS|SCHOOL") String scope, Long classroomId) {}
