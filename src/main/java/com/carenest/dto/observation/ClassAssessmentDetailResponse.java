package com.carenest.dto.observation;

import com.carenest.utils.enums.HealthSymptom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Chi tiết đánh giá một lớp trong kỳ theo từng trẻ, kèm nhận xét từng ngày. */
public record ClassAssessmentDetailResponse(
        Long classId,
        String className,
        Long campusId,
        String period,
        LocalDate start,
        LocalDate end,
        int schoolDays,
        List<ChildRow> children,
        List<ClassCommentResponse> classComments) {

    public record ChildRow(
            Long childId,
            String fullName,
            int presentDays,
            int assessedDays,
            int healthDays,
            Map<HealthSymptom, Integer> signs,
            int dat,
            int canCoGang,
            int chuaDat,
            int flags,
            List<Comment> comments,
            int eatingDays,
            int napDays,
            int emotionDays,
            int behaviorDays,
            int participationDays) {}

    public record Comment(LocalDate date, String text) {}
}
