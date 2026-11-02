package com.carenest.dto.observation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** Tổng quan đánh giá các lớp theo kỳ (hôm nay / tuần học T2–T6 / tháng dương lịch, tính tới hôm nay). */
public record ClassAssessmentOverviewResponse(
        String period,
        LocalDate start,
        LocalDate end,
        int schoolDays,
        List<Brief> campuses,
        List<Brief> ageGroups,
        List<ClassBrief> classes,
        List<ClassRow> rows,
        Totals totals) {

    public record Brief(Long id, String name) {}

    public record ClassBrief(Long id, String name, Long campusId, Long ageGroupId) {}

    @Schema(name = "AssessmentClassRow")
    public record ClassRow(
            Long classId,
            String className,
            Long campusId,
            Long ageGroupId,
            int size,
            int present,
            int assessed,
            int healthChildren,
            int dat,
            int canCoGang,
            int chuaDat,
            int flags,
            int schoolDays,
            int assessedDays,
            int lockedDays,
            int reopenedDays,
            List<ClassCommentResponse> classComments,

            @Schema(description = "Số trẻ có ít nhất một ngày ăn ít / không ăn")
            int eatingChildren,

            @Schema(description = "Số trẻ có ít nhất một ngày ngủ ít / không ngủ")
            int napChildren,

            @Schema(description = "Số trẻ có ít nhất một ngày buồn / khó chịu")
            int emotionChildren,

            @Schema(description = "Số trẻ có ít nhất một ngày có hành vi cần chú ý")
            int behaviorChildren,

            @Schema(description = "Số trẻ có ít nhất một ngày chưa tích cực tham gia")
            int participationChildren) {}

    public record Totals(
            int classes,
            int complete,
            int present,
            int assessed,
            int healthChildren,
            int flags,
            Integer datRate,
            MostNotMet mostNotMet) {}

    public record MostNotMet(String className, int chuaDat) {}
}
