package com.carenest.service.announcement.impl;

import com.carenest.dto.announcement.AnnouncementScheduleRequest;
import com.carenest.dto.announcement.AnnouncementScheduleResponse;
import com.carenest.dto.announcement.RecipientTargetRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.announcement.AnnouncementSchedule;
import com.carenest.entity.announcement.AnnouncementTemplate;
import com.carenest.entity.announcement.SchoolAnnouncement;
import com.carenest.entity.announcement.SchoolAnnouncement.Target;
import com.carenest.exception.GlobalException;
import com.carenest.repository.announcement.AnnouncementScheduleRepository;
import com.carenest.repository.announcement.AnnouncementTemplateRepository;
import com.carenest.repository.announcement.SchoolAnnouncementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.announcement.AnnouncementScheduleService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.ScheduleFrequency;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnnouncementScheduleServiceImpl implements AnnouncementScheduleService {
    private static final String LOG_PREFIX = "[AnnouncementScheduleServiceImpl]";

    private final AnnouncementScheduleRepository scheduleRepository;
    private final AnnouncementTemplateRepository templateRepository;
    private final SchoolAnnouncementRepository announcementRepository;
    private final RecipientResolver recipientResolver;
    private final NotificationService notificationService;
    private final SchoolYearService schoolYearService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementScheduleResponse> listSchedules(CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|listSchedules|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            List<AnnouncementSchedule> rows = scheduleRepository.findAllByOrderByIdAsc();
            Map<Long, String> names =
                    templateRepository
                            .findAllById(rows.stream()
                                    .map(AnnouncementSchedule::getTemplateId)
                                    .toList())
                            .stream()
                            .collect(Collectors.toMap(AnnouncementTemplate::getId, AnnouncementTemplate::getName));
            List<AnnouncementScheduleResponse> list = rows.stream()
                    .map(s -> toView(s, names.get(s.getTemplateId())))
                    .toList();
            log.info("{}|count={}|END", logPrefix, list.size());
            return list;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementScheduleResponse createSchedule(CurrentUser user, AnnouncementScheduleRequest request) {
        String logPrefix = LOG_PREFIX + "|createSchedule|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            AnnouncementSchedule s = AnnouncementSchedule.create(user.userId(), clock.instant());
            AnnouncementTemplate template = apply(user, s, request);
            scheduleRepository.save(s);
            log.info("{}|scheduleId={}|END", logPrefix, s.getId());
            return toView(s, template.getName());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementScheduleResponse updateSchedule(
            CurrentUser user, Long scheduleId, AnnouncementScheduleRequest request) {
        String logPrefix = LOG_PREFIX + "|updateSchedule|userId=" + user.userId() + "|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            AnnouncementSchedule s = find(scheduleId);
            AnnouncementTemplate template = apply(user, s, request);
            log.info("{}|END", logPrefix);
            return toView(s, template.getName());
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteSchedule(CurrentUser user, Long scheduleId) {
        String logPrefix = LOG_PREFIX + "|deleteSchedule|userId=" + user.userId() + "|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            scheduleRepository.delete(find(scheduleId));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public boolean runDue(Long scheduleId) {
        String logPrefix = LOG_PREFIX + "|runDue|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            AnnouncementSchedule s = scheduleRepository.lockDue(scheduleId, now).orElse(null);
            if (s == null) {
                log.info("{}|skipped|END", logPrefix);
                return false;
            }
            ZonedDateTime due = s.getNextRunAt().atZone(clock.getZone());
            boolean schoolDay = schoolYearService.isSchoolDay(due.toLocalDate());
            AnnouncementTemplate template =
                    templateRepository.findById(s.getTemplateId()).orElse(null);
            // SCH-05: lần trúng ngày nghỉ bị bỏ, lịch vẫn chuyển sang lần kế tiếp (PAR-10)
            if (schoolDay && template != null) {
                send(s, template, now);
            }
            ZonedDateTime from = now.atZone(clock.getZone()).isAfter(due) ? now.atZone(clock.getZone()) : due;
            s.advanceTo(nextRun(s, from).toInstant(), now);
            log.info("{}|schoolDay={}|END", logPrefix, schoolDay);
            return true;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findDueScheduleIds(int limit) {
        return scheduleRepository.findDueIds(clock.instant(), PageRequest.of(0, limit));
    }

    /** Tạo thông báo của trường từ mẫu, người gửi là HT tạo lịch; người nhận chốt lúc gửi. */
    private void send(AnnouncementSchedule s, AnnouncementTemplate template, Instant now) {
        Target target = s.target();
        Set<Long> recipients = RecipientResolver.all(recipientResolver.resolve(target, s.getCreatedBy(), today()));
        if (recipients.isEmpty()) {
            return;
        }
        SchoolAnnouncement a = announcementRepository.save(SchoolAnnouncement.create(
                s.getCreatedBy(), template.getTitle(), template.getBody(), target, now, true, s.getId(), now));
        int count = notificationService.notifyAnnouncement(a.getId(), recipients, a.getTitle(), a.getBody());
        a.markSent(count, now);
    }

    private AnnouncementTemplate apply(CurrentUser user, AnnouncementSchedule s, AnnouncementScheduleRequest r) {
        validate(r);
        AnnouncementTemplate template = templateRepository
                .findById(r.templateId())
                .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_TEMPLATE_NOT_FOUND));
        RecipientTargetRequest t = r.target();
        Target target = new Target(t.scopeType(), t.campusId(), t.ageGroupId(), t.classroomId(), t.roles());
        RecipientResolver.validate(target);
        recipientResolver.checkCanSend(user, target);
        target = recipientResolver.normalize(target);
        Integer dow = r.frequency() == ScheduleFrequency.WEEKLY ? r.dayOfWeek() : null;
        Integer dom = r.frequency() == ScheduleFrequency.MONTHLY ? r.dayOfMonth() : null;
        Instant now = clock.instant();
        Instant next = AnnouncementScheduleCalculator.nextRun(
                        r.frequency(), dow, dom, r.sendTime(), now.atZone(clock.getZone()))
                .toInstant();
        s.edit(
                template.getId(),
                target,
                r.frequency(),
                dow,
                dom,
                r.sendTime(),
                r.active() == null || r.active(),
                next,
                now);
        return template;
    }

    private static void validate(AnnouncementScheduleRequest r) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (r == null) {
            throw GlobalException.of(ApiCode.INVALID_REQUEST_DATA);
        }
        if (r.templateId() == null) {
            errors.add(new FieldErrorResponse("templateId", "Chọn mẫu thông báo"));
        }
        if (r.target() == null) {
            errors.add(new FieldErrorResponse("target", "Chọn người nhận"));
        }
        if (r.sendTime() == null) {
            errors.add(new FieldErrorResponse("sendTime", "Chọn giờ gửi"));
        }
        if (r.frequency() == null) {
            errors.add(new FieldErrorResponse("frequency", "Chọn chu kỳ tuần hoặc tháng"));
        } else if (r.frequency() == ScheduleFrequency.WEEKLY
                && (r.dayOfWeek() == null || r.dayOfWeek() < 1 || r.dayOfWeek() > 7)) {
            errors.add(new FieldErrorResponse("dayOfWeek", "Chọn thứ trong tuần (1–7)"));
        } else if (r.frequency() == ScheduleFrequency.MONTHLY
                && (r.dayOfMonth() == null || r.dayOfMonth() < 1 || r.dayOfMonth() > 31)) {
            errors.add(new FieldErrorResponse("dayOfMonth", "Chọn ngày trong tháng (1–31)"));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
    }

    private void checkPrincipal(CurrentUser user) {
        if (!recipientResolver.managesWholeSchool(user)) {
            throw GlobalException.of(ApiCode.FORBIDDEN);
        }
    }

    private AnnouncementSchedule find(Long id) {
        return scheduleRepository
                .findById(id)
                .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_SCHEDULE_NOT_FOUND));
    }

    private ZonedDateTime nextRun(AnnouncementSchedule s, ZonedDateTime after) {
        return AnnouncementScheduleCalculator.nextRun(
                s.getFrequency(),
                s.getDayOfWeek() == null ? null : s.getDayOfWeek().intValue(),
                s.getDayOfMonth() == null ? null : s.getDayOfMonth().intValue(),
                s.getSendTime(),
                after);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static AnnouncementScheduleResponse toView(AnnouncementSchedule s, String templateName) {
        Target t = s.target();
        return new AnnouncementScheduleResponse(
                s.getId(),
                s.getTemplateId(),
                templateName,
                new RecipientTargetRequest(t.scopeType(), t.campusId(), t.ageGroupId(), t.classroomId(), t.roles()),
                s.getFrequency(),
                s.getDayOfWeek() == null ? null : s.getDayOfWeek().intValue(),
                s.getDayOfMonth() == null ? null : s.getDayOfMonth().intValue(),
                s.getSendTime(),
                s.isActive(),
                s.getNextRunAt());
    }
}
