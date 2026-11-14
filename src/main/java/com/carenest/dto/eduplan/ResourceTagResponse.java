package com.carenest.dto.eduplan;

/** Nhãn học liệu; {@code auto} = tự thêm theo dòng gắn (gỡ là mất), ngược lại là nhãn thêm tay. */
public record ResourceTagResponse(
        Long id,
        String kind,
        Long refId,
        Long curriculumGoalId,
        String name,
        Long schoolYearId,
        String schoolYearName,
        boolean auto) {}
