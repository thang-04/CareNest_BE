package com.carenest.dto.observation;

import com.carenest.utils.enums.ClassRatingLevel;
import com.carenest.utils.enums.ClassRatingPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Xếp loại nhóm lớp theo kỳ (sổ chỉ đạo chuyên môn mục 7). 7 tiêu chí: records = hồ sơ sổ sách, program = thực hiện
 * chương trình, theme = thực hiện chủ đề, environment = tạo môi trường, routine = nền nếp trẻ, health = sức khỏe,
 * parents = kết hợp với cha mẹ; overall = xếp loại chung do người chấm chọn.
 */
public final class ClassRatingDtos {
    private ClassRatingDtos() {}

    public record Levels(
            ClassRatingLevel records,
            ClassRatingLevel program,
            ClassRatingLevel theme,
            ClassRatingLevel environment,
            ClassRatingLevel routine,
            ClassRatingLevel health,
            ClassRatingLevel parents,
            ClassRatingLevel overall) {}

    public record ClassRatingRow(
            Long classId,
            String className,
            Long campusId,
            Long ageGroupId,
            Levels levels,
            String note,
            String ratedByName,
            Instant ratedAt,
            boolean canEdit) {}

    /** openFrom: ngày bắt đầu chấm được kỳ này (null khi năm học chưa đặt ngày học kỳ); trước ngày đó chỉ xem. */
    public record ClassRatingResponse(
            ClassRatingPeriod period,
            Long schoolYearId,
            LocalDate openFrom,
            List<Long> campusIds,
            List<ClassRatingRow> rows) {}

    public record ClassRatingItem(
            @NotNull Long classId,
            @NotNull Levels levels,
            @Size(max = 500) String note) {}

    public record ClassRatingSaveRequest(
            @NotNull ClassRatingPeriod period,
            @NotNull @Size(min = 1) List<@NotNull @Valid ClassRatingItem> items) {}
}
