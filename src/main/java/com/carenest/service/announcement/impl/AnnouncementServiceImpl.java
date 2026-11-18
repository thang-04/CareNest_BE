package com.carenest.service.announcement.impl;

import com.carenest.config.NotificationTimingProperties;
import com.carenest.dto.announcement.AnnouncementAttachmentResponse;
import com.carenest.dto.announcement.AnnouncementListResponse;
import com.carenest.dto.announcement.AnnouncementRequest;
import com.carenest.dto.announcement.AnnouncementResponse;
import com.carenest.dto.announcement.AnnouncementSelectionRequest;
import com.carenest.dto.announcement.MyAnnouncementResponse;
import com.carenest.dto.announcement.RecipientPreviewResponse;
import com.carenest.dto.announcement.RecipientTargetRequest;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.entity.announcement.AnnouncementAttachment;
import com.carenest.entity.announcement.SchoolAnnouncement;
import com.carenest.entity.announcement.SchoolAnnouncement.Target;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.announcement.AnnouncementAttachmentRepository;
import com.carenest.repository.announcement.SchoolAnnouncementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.announcement.AnnouncementService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AnnouncementStatus;
import com.carenest.utils.enums.FileCategory;
import com.carenest.utils.enums.RecipientRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnnouncementServiceImpl implements AnnouncementService {
    private static final String LOG_PREFIX = "[AnnouncementServiceImpl]";
    static final int MAX_FILES = 5;
    private static final int TITLE_MAX = 150;
    private static final int BODY_MAX = 4000;
    private static final int MAX_PAGE_SIZE = 100;

    private final SchoolAnnouncementRepository announcementRepository;
    private final AnnouncementAttachmentRepository attachmentRepository;
    private final RecipientResolver recipientResolver;
    private final NotificationService notificationService;
    private final AccountService accountService;
    private final FileStorage storage;
    private final Clock clock;
    private final NotificationTimingProperties timing;

    @Override
    @Transactional(readOnly = true)
    public RecipientPreviewResponse previewRecipients(CurrentUser user, RecipientTargetRequest request) {
        String logPrefix = LOG_PREFIX + "|previewRecipients|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            Target target = checkedTarget(user, request);
            Map<RecipientRole, Set<Long>> byRole = recipientResolver.resolve(target, user.userId(), today());
            Map<RecipientRole, Integer> counts = new EnumMap<>(RecipientRole.class);
            byRole.forEach((role, ids) -> counts.put(role, ids.size()));
            RecipientPreviewResponse preview =
                    new RecipientPreviewResponse(RecipientResolver.all(byRole).size(), counts);
            log.info("{}|total={}|END", logPrefix, preview.total());
            return preview;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementResponse createAnnouncement(
            CurrentUser user, AnnouncementRequest request, List<MultipartFile> files) {
        String logPrefix = LOG_PREFIX + "|createAnnouncement|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        List<String> storedKeys = new ArrayList<>();
        try {
            validateContent(request);
            Target target = checkedTarget(user, request.target());
            List<MultipartFile> uploads = nonEmpty(files);
            checkFileCount(uploads.size());
            Instant now = clock.instant();
            Set<Long> recipients = requireRecipients(target, user.userId());
            boolean immediate = request.sendAt() == null;
            Instant sendAt = immediate ? now : checkSendAt(request.sendAt(), now);
            SchoolAnnouncement a = announcementRepository.save(SchoolAnnouncement.create(
                    user.userId(),
                    request.title().trim(),
                    request.body().trim(),
                    target,
                    sendAt,
                    immediate,
                    null,
                    now));
            storeFiles(a.getId(), uploads, now, storedKeys);
            if (immediate) {
                deliver(a, recipients, now);
            }
            AnnouncementResponse response = toView(a, user);
            log.info("{}|announcementId={}|status={}|END", logPrefix, a.getId(), a.getStatus());
            return response;
        } catch (RuntimeException ex) {
            storedKeys.forEach(k -> storage.removeQuietly(FileCategory.ANNOUNCEMENT, k));
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementResponse updateAnnouncement(
            CurrentUser user, Long announcementId, AnnouncementRequest request, List<MultipartFile> files) {
        String logPrefix =
                LOG_PREFIX + "|updateAnnouncement|userId=" + user.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        List<String> storedKeys = new ArrayList<>();
        try {
            SchoolAnnouncement a = lockEditable(user, announcementId);
            validateContent(request);
            Target target = checkedTarget(user, request.target());
            List<MultipartFile> uploads = nonEmpty(files);
            Set<Long> keep =
                    request.keepAttachmentIds() == null ? Set.of() : new HashSet<>(request.keepAttachmentIds());
            List<AnnouncementAttachment> existing = attachmentRepository.findByAnnouncementIdOrderByIdAsc(a.getId());
            List<AnnouncementAttachment> removed =
                    existing.stream().filter(f -> !keep.contains(f.getId())).toList();
            checkFileCount(existing.size() - removed.size() + uploads.size());
            Instant now = clock.instant();
            Set<Long> recipients = requireRecipients(target, user.userId());
            boolean immediate = request.sendAt() == null;
            Instant sendAt = immediate ? now : checkSendAt(request.sendAt(), now);
            a.edit(request.title().trim(), request.body().trim(), target, sendAt, now);
            attachmentRepository.deleteAll(removed);
            storeFiles(a.getId(), uploads, now, storedKeys);
            if (immediate) {
                a.startSending(now);
                deliver(a, recipients, now);
            }
            removed.forEach(f -> storage.removeQuietly(FileCategory.ANNOUNCEMENT, f.getFileKey()));
            AnnouncementResponse response = toView(a, user);
            log.info("{}|status={}|END", logPrefix, a.getStatus());
            return response;
        } catch (RuntimeException ex) {
            storedKeys.forEach(k -> storage.removeQuietly(FileCategory.ANNOUNCEMENT, k));
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AnnouncementResponse cancelAnnouncement(CurrentUser user, Long announcementId) {
        String logPrefix =
                LOG_PREFIX + "|cancelAnnouncement|userId=" + user.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        try {
            SchoolAnnouncement a = lockEditable(user, announcementId);
            a.cancel(clock.instant());
            AnnouncementResponse response = toView(a, user);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementListResponse listAnnouncements(
            CurrentUser user, String q, AnnouncementStatus status, int page, int size) {
        String logPrefix = LOG_PREFIX + "|listAnnouncements|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            PageRequest paging = PageRequest.of(
                    Math.max(page, 0),
                    size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE),
                    Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            Page<SchoolAnnouncement> rows = announcementRepository.findAll(filtered(user, q, status), paging);
            Map<Long, String> names = accountService.findFullNames(
                    rows.stream().map(SchoolAnnouncement::getSenderId).toList());
            PageResponse<AnnouncementResponse> list = PageResponse.from(rows, a -> toView(a, user, names, List.of()));
            log.info("{}|count={}|END", logPrefix, list.items().size());
            long cancelledCount = status == AnnouncementStatus.CANCELLED
                    ? rows.getTotalElements()
                    : status != null
                            ? 0
                            : announcementRepository.count(filtered(user, q, AnnouncementStatus.CANCELLED));
            return AnnouncementListResponse.of(list, cancelledCount);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteAnnouncement(CurrentUser user, Long announcementId) {
        String logPrefix =
                LOG_PREFIX + "|deleteAnnouncement|userId=" + user.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        try {
            SchoolAnnouncement a = announcementRepository
                    .lockById(announcementId)
                    .filter(x -> recipientResolver.canView(user, x.target(), x.getSenderId()))
                    .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_FOUND));
            if (a.getStatus() != AnnouncementStatus.CANCELLED)
                throw GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_DELETABLE);
            deleteRows(List.of(a));
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public int deleteCancelledAnnouncements(CurrentUser user, String q, AnnouncementStatus status) {
        String logPrefix = LOG_PREFIX + "|deleteCancelledAnnouncements|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            if (status != null && status != AnnouncementStatus.CANCELLED) {
                log.info("{}|count=0|END", logPrefix);
                return 0;
            }
            List<SchoolAnnouncement> rows =
                    announcementRepository.findAll(filtered(user, q, AnnouncementStatus.CANCELLED), Sort.by("id"));
            if (!rows.isEmpty()) deleteRows(rows);
            log.info("{}|count={}|END", logPrefix, rows.size());
            return rows.size();
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public int deleteSelectedAnnouncements(CurrentUser user, AnnouncementSelectionRequest selection) {
        String logPrefix = LOG_PREFIX + "|deleteSelectedAnnouncements|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            if (selection == null) throw GlobalException.of(ApiCode.INVALID_REQUEST_DATA);
            List<Long> ids = selection.ids() == null ? List.of() : selection.ids();
            List<Long> excluded = selection.excludedIds() == null ? List.of() : selection.excludedIds();
            if (ids.stream().anyMatch(id -> id == null || id <= 0)
                    || excluded.stream().anyMatch(id -> id == null || id <= 0)
                    || (selection.allMatching() && !ids.isEmpty())
                    || (!selection.allMatching() && (ids.isEmpty() || !excluded.isEmpty()))) {
                throw GlobalException.of(ApiCode.INVALID_REQUEST_DATA);
            }
            Specification<SchoolAnnouncement> spec;
            if (selection.allMatching()) {
                if (selection.status() != null && selection.status() != AnnouncementStatus.CANCELLED) {
                    log.info("{}|count=0|END", logPrefix);
                    return 0;
                }
                spec = filtered(user, selection.q(), AnnouncementStatus.CANCELLED);
                if (!excluded.isEmpty())
                    spec = spec.and((root, query, cb) -> cb.not(root.get("id").in(excluded)));
            } else {
                spec = filtered(user, null, null)
                        .and((root, query, cb) -> root.get("id").in(ids));
            }
            // Kiểm toàn bộ lựa chọn sau khóa, trước xóa: sai một dòng thì không xóa thành công một phần.
            List<SchoolAnnouncement> rows = announcementRepository.findAll(spec, Sort.by("id"));
            if (!selection.allMatching() && rows.size() != new HashSet<>(ids).size())
                throw GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_FOUND);
            if (rows.stream().anyMatch(row -> row.getStatus() != AnnouncementStatus.CANCELLED))
                throw GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_DELETABLE);
            if (!rows.isEmpty()) deleteRows(rows);
            log.info("{}|count={}|END", logPrefix, rows.size());
            return rows.size();
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Specification<SchoolAnnouncement> filtered(CurrentUser user, String q, AnnouncementStatus status) {
        boolean wholeSchool = recipientResolver.managesWholeSchool(user);
        Set<Long> campuses = recipientResolver.managedCampusIds(user);
        String term = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT);
        String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> {
            var scope = wholeSchool
                    ? cb.conjunction()
                    : cb.or(
                            cb.equal(root.get("senderId"), user.userId()),
                            root.get("scopeCampusId").in(withDummy(campuses)));
            var state = status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
            var search = term.isEmpty()
                    ? cb.conjunction()
                    : cb.or(
                            cb.like(cb.lower(root.get("title")), pattern, '\\'),
                            cb.like(cb.lower(root.get("body")), pattern, '\\'));
            return cb.and(scope, state, search);
        };
    }

    private void deleteRows(List<SchoolAnnouncement> rows) {
        List<String> keys =
                attachmentRepository
                        .findByAnnouncementIdIn(
                                rows.stream().map(SchoolAnnouncement::getId).toList())
                        .stream()
                        .map(AnnouncementAttachment::getFileKey)
                        .toList();
        announcementRepository.deleteAll(rows);
        announcementRepository.flush();
        AnnouncementFiles.cleanupAfterCommit(attachmentRepository, storage, keys);
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementResponse getAnnouncement(CurrentUser user, Long announcementId) {
        String logPrefix =
                LOG_PREFIX + "|getAnnouncement|userId=" + user.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        try {
            SchoolAnnouncement a = viewable(user, announcementId);
            AnnouncementResponse response = toView(a, user);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MyAnnouncementResponse getMyAnnouncement(CurrentUser user, Long announcementId) {
        String logPrefix =
                LOG_PREFIX + "|getMyAnnouncement|userId=" + user.userId() + "|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        try {
            SchoolAnnouncement a = readable(user, announcementId);
            MyAnnouncementResponse response = new MyAnnouncementResponse(
                    a.getId(),
                    a.getTitle(),
                    a.getBody(),
                    a.getSentAt(),
                    accountService.findFullNames(List.of(a.getSenderId())).get(a.getSenderId()),
                    attachmentsOf(a.getId()));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Download downloadAttachment(CurrentUser user, Long attachmentId) {
        String logPrefix = LOG_PREFIX + "|downloadAttachment|userId=" + user.userId() + "|attachmentId=" + attachmentId;
        log.info("{}|START", logPrefix);
        try {
            AnnouncementAttachment f = attachmentRepository
                    .findById(attachmentId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_FOUND));
            if (f.getScheduleId() != null) {
                if (!recipientResolver.managesWholeSchool(user))
                    throw GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_FOUND);
            } else readable(user, f.getAnnouncementId());
            Download download = new Download(
                    storage.load(FileCategory.ANNOUNCEMENT, f.getFileKey()), f.getFileName(), f.getContentType());
            log.info("{}|END", logPrefix);
            return download;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public boolean dispatchDue(Long announcementId) {
        String logPrefix = LOG_PREFIX + "|dispatchDue|announcementId=" + announcementId;
        log.info("{}|START", logPrefix);
        try {
            Instant now = clock.instant();
            SchoolAnnouncement a = announcementRepository
                    .lockDue(announcementId, List.of(AnnouncementStatus.SCHEDULED), now)
                    .orElse(null);
            if (a == null) {
                log.info("{}|skipped|END", logPrefix);
                return false;
            }
            // Danh sách người nhận chốt tại thời điểm gửi thực tế (trẻ chuyển lớp sau khi hẹn)
            a.startSending(now);
            deliver(a, RecipientResolver.all(recipientResolver.resolve(a.target(), a.getSenderId(), today())), now);
            log.info("{}|recipients={}|END", logPrefix, a.getRecipientCount());
            return true;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findDueAnnouncementIds(int limit) {
        return announcementRepository.findDueIds(
                List.of(AnnouncementStatus.SCHEDULED), clock.instant(), PageRequest.of(0, limit));
    }

    /** Tạo dòng thông báo cho từng người (đẩy qua hàng chờ US1, không chờ FCM) rồi đánh dấu đã gửi. */
    private void deliver(SchoolAnnouncement a, Set<Long> recipients, Instant now) {
        int count = notificationService.notifyAnnouncement(a.getId(), recipients, a.getTitle(), a.getBody());
        a.markSent(count, now);
    }

    private SchoolAnnouncement lockEditable(CurrentUser user, Long announcementId) {
        SchoolAnnouncement a = announcementRepository
                .lockById(announcementId)
                .filter(x -> recipientResolver.canView(user, x.target(), x.getSenderId()))
                .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_FOUND));
        if (a.getStatus() != AnnouncementStatus.SCHEDULED) {
            throw GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_EDITABLE);
        }
        return a;
    }

    private SchoolAnnouncement viewable(CurrentUser user, Long announcementId) {
        return announcementRepository
                .findById(announcementId)
                .filter(a -> recipientResolver.canView(user, a.target(), a.getSenderId()))
                .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_FOUND));
    }

    /** Người nhận (đã gửi), người gửi hoặc BGH trong phạm vi; khác ⇒ 404 như không tồn tại (FR-016). */
    private SchoolAnnouncement readable(CurrentUser user, Long announcementId) {
        SchoolAnnouncement a = announcementRepository
                .findById(announcementId)
                .orElseThrow(() -> GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_FOUND));
        boolean recipient = a.getStatus() == AnnouncementStatus.SENT
                && notificationService.isAnnouncementRecipient(user.userId(), a.getId());
        if (!recipient && !recipientResolver.canView(user, a.target(), a.getSenderId())) {
            throw GlobalException.of(ApiCode.ANNOUNCEMENT_NOT_FOUND);
        }
        return a;
    }

    private Target checkedTarget(CurrentUser user, RecipientTargetRequest request) {
        if (request == null) {
            throw GlobalException.withFieldErrors(
                    ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse("target", "Chọn người nhận")));
        }
        Target target = new Target(
                request.scopeType(), request.campusId(), request.ageGroupId(), request.classroomId(), request.roles());
        RecipientResolver.validate(target);
        recipientResolver.checkCanSend(user, target);
        return recipientResolver.normalize(target);
    }

    private Set<Long> requireRecipients(Target target, Long senderId) {
        Set<Long> recipients = RecipientResolver.all(recipientResolver.resolve(target, senderId, today()));
        if (recipients.isEmpty()) {
            throw GlobalException.of(ApiCode.ANNOUNCEMENT_NO_RECIPIENTS);
        }
        return recipients;
    }

    static void validateContent(AnnouncementRequest request) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (request == null || blank(request.title()) || request.title().trim().length() > TITLE_MAX) {
            errors.add(new FieldErrorResponse("title", "Tiêu đề bắt buộc, tối đa " + TITLE_MAX + " ký tự"));
        }
        if (request == null || blank(request.body()) || request.body().trim().length() > BODY_MAX) {
            errors.add(new FieldErrorResponse("body", "Nội dung bắt buộc, tối đa " + BODY_MAX + " ký tự"));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
    }

    private Instant checkSendAt(Instant sendAt, Instant now) {
        int maxDays = timing.announcement().maxScheduleDays();
        if (!sendAt.isAfter(now) || sendAt.isAfter(now.plus(Duration.ofDays(maxDays)))) {
            throw GlobalException.of(ApiCode.ANNOUNCEMENT_INVALID_SEND_AT, maxDays);
        }
        return sendAt;
    }

    static void checkFileCount(int count) {
        if (count > MAX_FILES) {
            throw GlobalException.of(ApiCode.ANNOUNCEMENT_TOO_MANY_FILES, MAX_FILES);
        }
    }

    /** Lưu tệp lên kho (kiểm loại, dung lượng, chữ ký đầu tệp ở FileStorage); lỗi sau đó thì nơi gọi xóa các key đã lưu. */
    private void storeFiles(Long announcementId, List<MultipartFile> files, Instant now, List<String> storedKeys) {
        AnnouncementFiles.store(attachmentRepository, storage, announcementId, false, files, now, storedKeys);
    }

    private AnnouncementResponse toView(SchoolAnnouncement a, CurrentUser viewer) {
        return toView(a, viewer, accountService.findFullNames(List.of(a.getSenderId())), attachmentsOf(a.getId()));
    }

    private static AnnouncementResponse toView(
            SchoolAnnouncement a,
            CurrentUser viewer,
            Map<Long, String> names,
            List<AnnouncementAttachmentResponse> attachments) {
        Target t = a.target();
        return new AnnouncementResponse(
                a.getId(),
                a.getTitle(),
                a.getBody(),
                new RecipientTargetRequest(t.scopeType(), t.campusId(), t.ageGroupId(), t.classroomId(), t.roles()),
                a.getStatus(),
                a.getSendAt(),
                a.getSentAt(),
                a.getRecipientCount(),
                a.getSenderId(),
                names.get(a.getSenderId()),
                attachments,
                a.getStatus() == AnnouncementStatus.SCHEDULED && viewer != null,
                a.getStatus() == AnnouncementStatus.CANCELLED && viewer != null);
    }

    private List<AnnouncementAttachmentResponse> attachmentsOf(Long announcementId) {
        return attachmentRepository.findByAnnouncementIdOrderByIdAsc(announcementId).stream()
                .map(f -> new AnnouncementAttachmentResponse(
                        f.getId(), f.getFileName(), f.getContentType(), f.getSizeBytes()))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        return files == null
                ? List.of()
                : files.stream()
                        .filter(Objects::nonNull)
                        .filter(f -> !f.isEmpty())
                        .toList();
    }

    // IN () rỗng không hợp lệ ở một số CSDL: thêm id không tồn tại
    private static Set<Long> withDummy(Set<Long> ids) {
        Set<Long> result = new HashSet<>(ids);
        result.add(-1L);
        return result;
    }

    private static String safeName(String original) {
        String name = original == null ? "tep" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "tep";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
