package com.carenest.service.observation.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.observation.EvaluationDtos.EvaluationDay;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.ChildEvaluation;
import com.carenest.entity.observation.WeeklyComment;
import com.carenest.repository.observation.ChildEvaluationRepository;
import com.carenest.repository.observation.WeeklyCommentRepository;
import com.carenest.service.observation.impl.PeriodCommentFacts.Note;
import com.carenest.utils.enums.EvaluationPeriodType;
import com.carenest.utils.enums.EvaluationStatus;
import com.carenest.utils.enums.WeeklyCommentStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Gom dữ liệu một trẻ trong kỳ cho AI viết nháp (OBS-34): số liệu đếm sẵn, nhận xét hằng ngày, nhận xét tuần đã gửi và
 * nhận xét kỳ nhỏ hơn đã xác nhận, đều của chính lớp này (trẻ chuyển lớp giữa kỳ không lẫn lớp kia).
 */
@Component
@RequiredArgsConstructor
class PeriodCommentFactsBuilder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final EvaluationSources sources;
    private final WeeklyCommentRepository weeklyCommentRepository;
    private final ChildEvaluationRepository evaluationRepository;

    /** Rỗng khi trẻ không có dữ liệu nguồn trong kỳ. */
    PeriodCommentFacts build(ClassroomInfoResponse cls, ChildEvaluation ev, ClassChildResponse child, LocalDate today) {
        LocalDate end = ev.getPeriodEnd().isAfter(today) ? today : ev.getPeriodEnd();
        EvaluationSources.ChildData data = sources.load(cls, List.of(ev.getChildId()), ev.getPeriodStart(), end, true)
                .get(ev.getChildId());
        if (data == null) {
            return null;
        }
        return new PeriodCommentFacts(
                ev.getPeriodType(),
                ev.getPeriodStart(),
                ev.getPeriodEnd(),
                child.fullName(),
                child.dateOfBirth(),
                data.presentDays(),
                data.flagCount(),
                data.structured(),
                finals(ev),
                weekly(cls, ev),
                daily(data.days()));
    }

    private static List<Note> daily(List<EvaluationDay> days) {
        return days.stream()
                .filter(d -> d.comment() != null && !d.comment().isBlank())
                .map(d -> new Note("Ngày " + DAY.format(d.date()), d.comment()))
                .toList();
    }

    private List<Note> weekly(ClassroomInfoResponse cls, ChildEvaluation ev) {
        return weeklyCommentRepository
                .findByChildIdAndStatusAndWeekStartBetweenOrderByWeekStartAsc(
                        ev.getChildId(), WeeklyCommentStatus.SENT, ev.getPeriodStart(), ev.getPeriodEnd())
                .stream()
                .filter(w -> cls.id().equals(w.getClassroomId()))
                .filter(w -> !w.getContent().isBlank())
                .map((WeeklyComment w) -> new Note("Tuần từ " + DAY.format(w.getWeekStart()), w.getContent()))
                .toList();
    }

    // Học kỳ đọc các tháng đã xác nhận; cuối năm đọc các học kỳ, thêm tháng nằm ngoài mọi học kỳ đã xác nhận
    private List<Note> finals(ChildEvaluation ev) {
        if (ev.getPeriodType() == EvaluationPeriodType.MONTH) {
            return List.of();
        }
        List<ChildEvaluation> months = confirmed(ev, EvaluationPeriodType.MONTH);
        if (ev.getPeriodType() == EvaluationPeriodType.SEMESTER) {
            return months.stream().map(PeriodCommentFactsBuilder::monthNote).toList();
        }
        List<ChildEvaluation> semesters = confirmed(ev, EvaluationPeriodType.SEMESTER);
        List<Note> notes = new ArrayList<>();
        semesters.forEach(s -> notes.add(new Note(
                "Nhận xét học kỳ từ " + DAY_YEAR.format(s.getPeriodStart()) + " đến "
                        + DAY_YEAR.format(s.getPeriodEnd()),
                s.getFinalContent())));
        months.stream()
                .filter(m -> semesters.stream()
                        .noneMatch(s -> !m.getPeriodStart().isBefore(s.getPeriodStart())
                                && !m.getPeriodStart().isAfter(s.getPeriodEnd())))
                .forEach(m -> notes.add(monthNote(m)));
        return notes;
    }

    private List<ChildEvaluation> confirmed(ChildEvaluation ev, EvaluationPeriodType type) {
        return evaluationRepository
                .findByChildIdAndPeriodTypeAndStatusAndPeriodStartBetweenOrderByPeriodStartAsc(
                        ev.getChildId(), type, EvaluationStatus.CONFIRMED, ev.getPeriodStart(), ev.getPeriodEnd())
                .stream()
                .filter(e -> e.getClassroomId().equals(ev.getClassroomId()))
                .filter(e -> e.getFinalContent() != null && !e.getFinalContent().isBlank())
                .toList();
    }

    private static Note monthNote(ChildEvaluation month) {
        return new Note("Nhận xét tháng " + MONTH.format(month.getPeriodStart()), month.getFinalContent());
    }
}
