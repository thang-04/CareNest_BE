package com.carenest.service.announcement.impl;

import com.carenest.dto.announcement.AnnouncementAttachmentResponse;
import com.carenest.dto.announcement.AnnouncementRequest;
import com.carenest.dto.announcement.AnnouncementScheduleRequest;
import com.carenest.dto.announcement.AnnouncementScheduleResponse;
import com.carenest.dto.announcement.RecipientTargetRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.entity.announcement.AnnouncementAttachment;
import com.carenest.entity.announcement.AnnouncementSchedule;
import com.carenest.entity.announcement.AnnouncementTemplate;
import com.carenest.entity.announcement.SchoolAnnouncement;
import com.carenest.entity.announcement.SchoolAnnouncement.Target;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.announcement.AnnouncementAttachmentRepository;
import com.carenest.repository.announcement.AnnouncementScheduleRepository;
import com.carenest.repository.announcement.AnnouncementTemplateRepository;
import com.carenest.repository.announcement.SchoolAnnouncementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.announcement.AnnouncementScheduleService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.FileCategory;
import com.carenest.utils.enums.ScheduleFrequency;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final AnnouncementAttachmentRepository attachments;
    private final FileStorage storage;

    @Override
    @Transactional(readOnly = true)
    public List<AnnouncementScheduleResponse> listSchedules(CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|listSchedules|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            List<AnnouncementSchedule> rows = scheduleRepository.findAllByOrderByIdAsc();
            Map<Long, AnnouncementTemplate> templates = templateRepository
                    .findAllById(rows.stream()
                            .map(AnnouncementSchedule::getTemplateId)
                            .filter(Objects::nonNull)
                            .toList())
                    .stream()
                    .collect(Collectors.toMap(AnnouncementTemplate::getId, t -> t));
            Map<Long, List<AnnouncementAttachment>> files =
                    attachments
                            .findByScheduleIdIn(rows.stream()
                                    .map(AnnouncementSchedule::getId)
                                    .toList())
                            .stream()
                            .collect(Collectors.groupingBy(AnnouncementAttachment::getScheduleId));
            List<AnnouncementScheduleResponse> list = rows.stream()
                    .map(row ->
                            toView(row, templates.get(row.getTemplateId()), files.getOrDefault(row.getId(), List.of())))
                    .toList();
            log.info("{}|count={}|END", logPrefix, list.size());
            return list;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementScheduleResponse createSchedule(
            CurrentUser user, AnnouncementScheduleRequest request, List<MultipartFile> files) {
        String logPrefix = LOG_PREFIX + "|createSchedule|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        List<String> stored = new ArrayList<>();
        try {
            checkPrincipal(user);
            AnnouncementSchedule s = AnnouncementSchedule.create(user.userId(), clock.instant());
            AnnouncementTemplate template = apply(user, s, request);
            List<MultipartFile> uploads = AnnouncementServiceImpl.nonEmpty(files);
            AnnouncementServiceImpl.checkFileCount(uploads.size());
            scheduleRepository.save(s);
            AnnouncementFiles.store(attachments, storage, s.getId(), true, uploads, clock.instant(), stored);
            log.info("{}|scheduleId={}|END", logPrefix, s.getId());
            return toView(s, template, attachments.findByScheduleIdOrderByIdAsc(s.getId()));
        } catch (RuntimeException ex) {
            stored.forEach(key -> storage.removeQuietly(FileCategory.ANNOUNCEMENT, key));
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementScheduleResponse updateSchedule(
            CurrentUser user, Long scheduleId, AnnouncementScheduleRequest request, List<MultipartFile> files) {
        String logPrefix = LOG_PREFIX + "|updateSchedule|userId=" + user.userId() + "|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        List<String> stored = new ArrayList<>();
        try {
            checkPrincipal(user);
            AnnouncementSchedule s = scheduleRepository
                    .lockById(scheduleId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_SCHEDULE_NOT_FOUND));
            AnnouncementTemplate template = apply(user, s, request);
            List<AnnouncementAttachment> existing = attachments.findByScheduleIdOrderByIdAsc(scheduleId);
            Set<Long> keep = request.keepAttachmentIds() == null
                    ? existing.stream().map(AnnouncementAttachment::getId).collect(Collectors.toSet())
                    : new HashSet<>(request.keepAttachmentIds());
            List<AnnouncementAttachment> removed = existing.stream()
                    .filter(file -> !keep.contains(file.getId()))
                    .toList();
            List<MultipartFile> uploads = AnnouncementServiceImpl.nonEmpty(files);
            AnnouncementServiceImpl.checkFileCount(existing.size() - removed.size() + uploads.size());
            attachments.deleteAll(removed);
            AnnouncementFiles.store(attachments, storage, scheduleId, true, uploads, clock.instant(), stored);
            attachments.flush();
            AnnouncementFiles.cleanupAfterCommit(
                    attachments,
                    storage,
                    removed.stream().map(AnnouncementAttachment::getFileKey).toList());
            log.info("{}|END", logPrefix);
            return toView(s, template, attachments.findByScheduleIdOrderByIdAsc(scheduleId));
        } catch (RuntimeException ex) {
            stored.forEach(key -> storage.removeQuietly(FileCategory.ANNOUNCEMENT, key));
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementScheduleResponse getSchedule(CurrentUser user, Long scheduleId) {
        String logPrefix = LOG_PREFIX + "|getSchedule|userId=" + user.userId() + "|scheduleId=" + scheduleId;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            AnnouncementSchedule s = find(scheduleId);
            AnnouncementTemplate template = s.getTemplateId() == null
                    ? null
                    : templateRepository.findById(s.getTemplateId()).orElse(null);
            AnnouncementScheduleResponse result =
                    toView(s, template, attachments.findByScheduleIdOrderByIdAsc(scheduleId));
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public int activateSchedules(CurrentUser user, com.carenest.dto.announcement.ScheduleActivationRequest request) {
        String logPrefix = LOG_PREFIX + "|activateSchedules|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            if (request == null
                    || request.active() == null
                    || request.ids() == null
                    || request.ids().isEmpty()
                    || request.ids().stream().anyMatch(id -> id == null || id <= 0))
                throw GlobalException.of(ApiCode.INVALID_REQUEST_DATA);
            Set<Long> ids = new HashSet<>(request.ids());
            List<AnnouncementSchedule> rows = scheduleRepository.lockByIds(ids);
            if (rows.size() != ids.size()) throw GlobalException.of(ApiCode.ANNOUNCEMENT_SCHEDULE_NOT_FOUND);
            Instant now = clock.instant();
            int changed = 0;
            for (AnnouncementSchedule row : rows)
                if (row.isActive() != request.active()) {
                    row.activate(
                            request.active(),
                            request.active()
                                    ? nextRun(row, now.atZone(clock.getZone())).toInstant()
                                    : row.getNextRunAt(),
                            now);
                    changed++;
                }
            log.info("{}|count={}|END", logPrefix, changed);
            return changed;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteSchedule(CurrentUser user, Long scheduleId) {
        deleteSchedules(user, List.of(scheduleId));
    }

    @Override
    public int deleteSchedules(CurrentUser user, List<Long> scheduleIds) {
        String logPrefix = LOG_PREFIX + "|deleteSchedules|userId=" + user.userId() + "|ids=" + scheduleIds;
        log.info("{}|START", logPrefix);
        try {
            checkPrincipal(user);
            if (scheduleIds == null
                    || scheduleIds.isEmpty()
                    || scheduleIds.stream().anyMatch(id -> id == null || id <= 0))
                throw GlobalException.of(ApiCode.INVALID_REQUEST_DATA);
            Set<Long> ids = new HashSet<>(scheduleIds);
            // Khóa đủ toàn bộ trước khi xóa: thiếu một id thì không xóa lịch nào (không thành công một phần).
            List<AnnouncementSchedule> rows = scheduleRepository.lockByIds(ids);
            if (rows.size() != ids.size()) throw GlobalException.of(ApiCode.ANNOUNCEMENT_SCHEDULE_NOT_FOUND);
            // PAR-10: chỉ xóa lịch đã tắt (Tiền 17/11/2026); còn một lịch đang bật thì không xóa lịch nào.
            if (rows.stream().anyMatch(AnnouncementSchedule::isActive))
                throw GlobalException.of(ApiCode.ANNOUNCEMENT_SCHEDULE_ACTIVE);
            List<String> keys = attachments.findByScheduleIdIn(ids).stream()
                    .map(AnnouncementAttachment::getFileKey)
                    .toList();
            scheduleRepository.deleteAll(rows);
            scheduleRepository.flush();
            AnnouncementFiles.cleanupAfterCommit(attachments, storage, keys);
            log.info("{}|count={}|END", logPrefix, rows.size());
            return rows.size();
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
            AnnouncementTemplate template = s.getTemplateId() == null
                    ? null
                    : templateRepository.findById(s.getTemplateId()).orElse(null);
            // SCH-05: lần trúng ngày nghỉ bị bỏ, lịch vẫn chuyển sang lần kế tiếp (PAR-10)
            if (schoolDay && (s.getTitle() != null || template != null)) {
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
                s.getCreatedBy(),
                s.getTitle() != null ? s.getTitle() : template.getTitle(),
                s.getBody() != null ? s.getBody() : template.getBody(),
                target,
                now,
                true,
                s.getId(),
                now));
        for (AnnouncementAttachment file : attachments.findByScheduleIdOrderByIdAsc(s.getId())) {
            attachments.save(AnnouncementAttachment.of(
                    a.getId(), file.getFileKey(), file.getFileName(), file.getContentType(), file.getSizeBytes(), now));
        }
        int count = notificationService.notifyAnnouncement(a.getId(), recipients, a.getTitle(), a.getBody());
        a.markSent(count, now);
    }

    private AnnouncementTemplate apply(CurrentUser user, AnnouncementSchedule s, AnnouncementScheduleRequest r) {
        validate(r);
        AnnouncementTemplate template = r.templateId() == null
                ? null
                : templateRepository
                        .findById(r.templateId())
                        .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_TEMPLATE_NOT_FOUND));
        String title = r.title() != null ? r.title() : template == null ? null : template.getTitle();
        String body = r.body() != null ? r.body() : template == null ? null : template.getBody();
        AnnouncementServiceImpl.validateContent(
                new AnnouncementRequest(title, body, r.target(), null, r.keepAttachmentIds()));
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
                r.templateId(),
                target,
                r.frequency(),
                dow,
                dom,
                r.sendTime(),
                r.active() == null || r.active(),
                next,
                now);
        s.content(title.trim(), body.trim());
        return template;
    }

    private static void validate(AnnouncementScheduleRequest r) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (r == null) {
            throw GlobalException.of(ApiCode.INVALID_REQUEST_DATA);
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

    private static AnnouncementScheduleResponse toView(
            AnnouncementSchedule s, AnnouncementTemplate template, List<AnnouncementAttachment> files) {
        Target t = s.target();
        return new AnnouncementScheduleResponse(
                s.getId(),
                s.getTemplateId(),
                template == null ? null : template.getName(),
                new RecipientTargetRequest(t.scopeType(), t.campusId(), t.ageGroupId(), t.classroomId(), t.roles()),
                s.getFrequency(),
                s.getDayOfWeek() == null ? null : s.getDayOfWeek().intValue(),
                s.getDayOfMonth() == null ? null : s.getDayOfMonth().intValue(),
                s.getSendTime(),
                s.isActive(),
                s.getNextRunAt(),
                s.getTitle() != null ? s.getTitle() : template == null ? null : template.getTitle(),
                s.getBody() != null ? s.getBody() : template == null ? null : template.getBody(),
                files.stream()
                        .map(f -> new AnnouncementAttachmentResponse(
                                f.getId(), f.getFileName(), f.getContentType(), f.getSizeBytes()))
                        .toList());
    }
}
