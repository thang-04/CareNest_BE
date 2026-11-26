package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.observation.SemesterAssessmentDtos.Child;
import com.carenest.dto.observation.SemesterAssessmentDtos.Domain;
import com.carenest.dto.observation.SemesterAssessmentDtos.Goal;
import com.carenest.dto.observation.SemesterAssessmentDtos.Response;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SemesterDomainResultTest {
    private Goal g(Boolean met) {
        return new Goal(1L, (short) 1, "TEST", met, met == null ? 0 : met ? 1 : 0, met == null ? 0 : 1);
    }

    @Test
    @DisplayName("OBS-25: lĩnh vực đạt đúng 50%, dưới 50% chưa đạt, thiếu để trống")
    void computesHalfWithoutRoundingOrTreatingMissingAsFailure() {
        assertThat(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of(g(true), g(false)))
                        .met())
                .isTrue();
        assertThat(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of(g(true), g(false), g(false)))
                        .met())
                .isFalse();
        assertThat(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of(g(true), g((Boolean) null)))
                        .met())
                .isNull();
        assertThat(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of()).met())
                .isNull();
    }

    private Response board(List<Domain> columns, List<Child> rows) {
        LocalDate day = LocalDate.of(2026, 12, 31);
        return new Response(
                1L,
                "Lớp",
                1L,
                "2026-2027",
                1,
                day.minusMonths(4),
                day,
                "NONE",
                null,
                columns,
                rows,
                true,
                false,
                null,
                null,
                null,
                null);
    }

    private Child child(long id, Boolean... results) {
        List<Goal> goals = java.util.Arrays.stream(results).map(this::g).toList();
        return new Child(id, "Trẻ " + id, List.of(SemesterAssessmentServiceImpl.summarize(1L, "D", goals)), null);
    }

    @Test
    @DisplayName(
            "OBS-25: trẻ vào lớp muộn chưa có kết quả không chặn xác nhận học kỳ, bảng không có kết quả nào thì chặn")
    void lateJoinerWithoutResultsDoesNotBlockConfirmation() {
        List<Domain> columns = List.of(SemesterAssessmentServiceImpl.summarize(1L, "D", List.of()));

        assertThat(SemesterAssessmentServiceImpl.incomplete(
                        board(columns, List.of(child(1, true, false), child(2, (Boolean) null)))))
                .isFalse();
        assertThat(SemesterAssessmentServiceImpl.incomplete(
                        board(columns, List.of(child(1, (Boolean) null), child(2, (Boolean) null)))))
                .isTrue();
        assertThat(SemesterAssessmentServiceImpl.incomplete(board(columns, List.of())))
                .isTrue();
        assertThat(SemesterAssessmentServiceImpl.incomplete(board(List.of(), List.of(child(1, true)))))
                .isTrue();
    }
}
