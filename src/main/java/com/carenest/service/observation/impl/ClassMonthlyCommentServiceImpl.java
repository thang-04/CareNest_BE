package com.carenest.service.observation.impl;

import com.carenest.dto.observation.ClassMonthlyCommentDtos.ClassMonthlyCommentRequest;
import com.carenest.dto.observation.ClassMonthlyCommentDtos.ClassMonthlyCommentResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.observation.ClassDailyComment;
import com.carenest.entity.observation.ClassMonthlyComment;
import com.carenest.exception.GlobalException;
import com.carenest.repository.observation.ClassDailyCommentRepository;
import com.carenest.repository.observation.ClassMonthlyCommentRepository;
import com.carenest.repository.observation.DailyObservationRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.attendance.AttendanceService;
import com.carenest.service.attendance.AttendanceService.ClassChildDay;
import com.carenest.service.observation.ClassMonthlyCommentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AttendanceStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassMonthlyCommentServiceImpl implements ClassMonthlyCommentService {
    private static final String LOG_PREFIX = "[ClassMonthlyCommentServiceImpl]";
    private static final DateTimeFormatter MONTH_TEXT = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final int DAILY_NOTES_IN_DRAFT = 3;

    private final ObservationDayRules rules;
    private final AttendanceService attendanceService;
    private final AccountService accountService;
    private final ClassMonthlyCommentRepository commentRepository;
    private final ClassDailyCommentRepository dailyCommentRepository;
    private final DailyObservationRepository observationRepository;

    @Override
    @Transactional
    public ClassMonthlyCommentResponse get(CurrentUser currentUser, Long classId, LocalDate month) {
        String logPrefix = LOG_PREFIX + "|get|userId=" + currentUser.userId() + "|classId=" + classId;
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(classId);
            boolean teacher = rules.canRecord(currentUser, cls) || rules.taughtClosedYear(currentUser, cls);
            if (!teacher && !rules.readsCampus(currentUser, cls.campusId())) {
                throw GlobalException.of(ApiCode.EVALUATION_VIEW_FORBIDDEN);
            }
            LocalDate first = firstOf(cls, month);
            ClassMonthlyComment comment =
                    commentRepository.findByClassroomIdAndMonth(classId, first).orElse(null);
            // Tháng đã kết thúc, năm đang hoạt động: GV mở thì tạo nháp lần đầu
            if (comment == null
                    && ended(cls, first)
                    && rules.canRecord(currentUser, cls)
                    && cls.yearStatus() == SchoolYearStatus.ACTIVE) {
                comment = commentRepository.saveAndFlush(ClassMonthlyComment.draft(
                        cls.id(), cls.campusId(), cls.schoolYearId(), first, draftOf(cls, first), rules.now()));
            }
            ClassMonthlyCommentResponse response = toResponse(currentUser, cls, first, comment, teacher);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ClassMonthlyCommentResponse save(CurrentUser currentUser, ClassMonthlyCommentRequest request) {
        String logPrefix = LOG_PREFIX + "|save|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(request.classId());
            ClassMonthlyComment comment = editable(currentUser, cls, request.month());
            comment.save(ChildEvaluationServiceImpl.checkContent(request.content()), currentUser.userId(), rules.now());
            commentRepository.saveAndFlush(comment);
            ClassMonthlyCommentResponse response = toResponse(currentUser, cls, comment.getMonth(), comment, true);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ClassMonthlyCommentResponse confirm(CurrentUser currentUser, ClassMonthlyCommentRequest request) {
        String logPrefix = LOG_PREFIX + "|confirm|userId=" + currentUser.userId() + "|classId=" + request.classId();
        log.info("{}|START", logPrefix);
        try {
            ClassroomInfoResponse cls = rules.findClass(request.classId());
            ClassMonthlyComment comment = editable(currentUser, cls, request.month());
            comment.confirm(
                    ChildEvaluationServiceImpl.checkContent(request.content()), currentUser.userId(), rules.now());
            commentRepository.saveAndFlush(comment);
            ClassMonthlyCommentResponse response = toResponse(currentUser, cls, comment.getMonth(), comment, true);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    // Chỉ GV đang dạy lớp, năm đang hoạt động, tháng đã kết thúc, chưa xác nhận (OBS-26)
    private ClassMonthlyComment editable(CurrentUser user, ClassroomInfoResponse cls, LocalDate month) {
        if (!rules.canRecord(user, cls)) {
            throw GlobalException.of(ApiCode.EVALUATION_EDIT_FORBIDDEN);
        }
        if (cls.yearStatus() != SchoolYearStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.ASSESSMENT_YEAR_NOT_ACTIVE);
        }
        month = firstOf(cls, month);
        if (!ended(cls, month)) {
            throw GlobalException.of(ApiCode.CLASS_MONTHLY_COMMENT_NOT_READY);
        }
        ClassMonthlyComment comment =
                commentRepository.findByClassroomIdAndMonth(cls.id(), month).orElse(null);
        if (comment == null) {
            comment = ClassMonthlyComment.draft(
                    cls.id(), cls.campusId(), cls.schoolYearId(), month, draftOf(cls, month), rules.now());
        }
        if (comment.isConfirmed()) {
            throw GlobalException.of(ApiCode.EVALUATION_ALREADY_CONFIRMED);
        }
        return comment;
    }

    private ClassMonthlyCommentResponse toResponse(
            CurrentUser user, ClassroomInfoResponse cls, LocalDate month, ClassMonthlyComment c, boolean teacher) {
        boolean confirmed = c != null && c.isConfirmed();
        String status = c == null
                ? (ended(cls, month) ? "AWAITING_REVIEW" : "NOT_READY")
                : confirmed ? "CONFIRMED" : "AWAITING_REVIEW";
        boolean visible = c != null && (teacher || confirmed);
        String confirmedBy = confirmed
                ? accountService.findFullNames(List.of(c.getConfirmedBy())).get(c.getConfirmedBy())
                : null;
        return new ClassMonthlyCommentResponse(
                cls.id(),
                cls.name(),
                month,
                status,
                visible && teacher ? c.getDraftContent() : null,
                visible ? c.getContent() : null,
                confirmedBy,
                confirmed ? c.getConfirmedAt() : null,
                c == null ? null : c.getUpdatedAt(),
                !confirmed
                        && rules.canRecord(user, cls)
                        && cls.yearStatus() == SchoolYearStatus.ACTIVE
                        && ended(cls, month));
    }

    private boolean ended(ClassroomInfoResponse cls, LocalDate month) {
        LocalDate end = month.with(TemporalAdjusters.lastDayOfMonth());
        return end.isBefore(rules.today())
                || end.isAfter(cls.yearEnd()) && cls.yearEnd().isBefore(rules.today());
    }

    private static LocalDate firstOf(ClassroomInfoResponse cls, LocalDate month) {
        LocalDate first = month == null ? null : month.withDayOfMonth(1);
        if (first == null
                || first.isBefore(cls.yearStart().withDayOfMonth(1))
                || first.isAfter(cls.yearEnd().withDayOfMonth(1))) {
            throw GlobalException.of(
                    ApiCode.EVALUATION_MONTH_INVALID,
                    MONTH_TEXT.format(cls.yearStart()),
                    MONTH_TEXT.format(cls.yearEnd()));
        }
        return first;
    }

    // Nháp từ số liệu tháng của lớp: chuyên cần, mức đánh giá hằng ngày, vài nhận xét chung theo ngày (GV sửa lại)
    private String draftOf(ClassroomInfoResponse cls, LocalDate month) {
        LocalDate from = month.isBefore(cls.yearStart()) ? cls.yearStart() : month;
        LocalDate end = month.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate to = end.isAfter(cls.yearEnd()) ? cls.yearEnd() : end;
        List<ClassChildDay> days = attendanceService.findClassDays(List.of(cls.id()), from, to).stream()
                .filter(d -> rules.isSchoolDay(d.date()))
                .toList();
        Set<LocalDate> schoolDays = days.stream().map(ClassChildDay::date).collect(Collectors.toSet());
        Set<Long> children = days.stream().map(ClassChildDay::childId).collect(Collectors.toSet());
        long present = days.stream()
                .filter(d -> d.status() == AttendanceStatus.PRESENT)
                .count();
        int rate = days.isEmpty() ? 0 : Math.round(present * 100f / days.size());
        long assessed = observationRepository.findByChildIdInAndDateBetween(children, from, to).stream()
                .filter(o -> cls.id().equals(o.getClassroomId()))
                .count();
        long flags = observationRepository.findByChildIdInAndDateBetween(children, from, to).stream()
                .filter(o -> cls.id().equals(o.getClassroomId()) && Boolean.TRUE.equals(o.getFlag()))
                .count();
        List<String> lines = new ArrayList<>();
        lines.add("Tháng " + MONTH_TEXT.format(month) + ", lớp " + cls.name() + " có " + children.size() + " trẻ, học "
                + schoolDays.size() + " ngày, tỷ lệ chuyên cần " + rate + "%.");
        lines.add("Cô đánh giá hằng ngày " + assessed + " lượt, các bé nhận " + flags + " cờ bé ngoan.");
        List<String> notes =
                dailyCommentRepository
                        .findByClassroomIdInAndDateBetweenOrderByDateDesc(List.of(cls.id()), from, to)
                        .stream()
                        .map(ClassDailyComment::getText)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(t -> !t.isEmpty())
                        .distinct()
                        .limit(DAILY_NOTES_IN_DRAFT)
                        .toList();
        if (!notes.isEmpty()) {
            lines.add("Nhận xét trong tháng: " + String.join(" ", notes));
        }
        lines.add("Cô bổ sung điểm nổi bật, việc cần cố gắng của lớp tháng tới.");
        return String.join("\n", lines);
    }
}
