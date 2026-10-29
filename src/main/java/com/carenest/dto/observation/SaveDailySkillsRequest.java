package com.carenest.dto.observation;

import com.carenest.utils.enums.Participation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * View Kiến thức kỹ năng: kết quả MT từng hoạt động của giáo án đã duyệt. Chỉ gửi trẻ / hoạt động cô đã chấm; hoạt động gửi
 * với 3 danh sách rỗng và không có mức tham gia thì xóa kết quả cũ. comment null = giữ nhận xét đang có.
 */
public record SaveDailySkillsRequest(
        @NotNull LocalDate date, @NotEmpty @Valid List<Entry> entries) {

    public record Entry(
            @NotNull Long childId,
            @Size(max = 500) String comment,
            @Valid List<Activity> activities) {}

    public record Activity(
            @NotBlank String key,
            Participation participation,
            List<Long> goalsMet,
            List<Long> goalsEffort,
            List<Long> goalsNotMet) {}
}
