package com.carenest.service.eduplan.impl;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.*;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduResourceAttachment;
import com.carenest.entity.eduplan.EduResourceItem;
import com.carenest.entity.eduplan.EduResourceItemTag;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduResourceAttachmentRepository;
import com.carenest.repository.eduplan.EduResourceItemRepository;
import com.carenest.repository.eduplan.EduResourceItemTagRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.ResourceLibraryService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.LearningResourcePolicy;
import com.carenest.utils.enums.EduClassDocStatus;
import com.carenest.utils.enums.FileCategory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceLibraryServiceImpl implements ResourceLibraryService {
    private static final String LOG_PREFIX = "[ResourceLibraryServiceImpl]";
    private static final int MAX_PAGE = 50;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EduResourceItemRepository items;
    private final EduResourceItemTagRepository tags;
    private final EduResourceAttachmentRepository attachments;
    private final EduPlanGoalRepository goals;
    private final EduClassDocRepository classDocRepository;
    private final EduClassDocService classDocs;
    private final ClassroomService classrooms;
    private final SchoolYearService schoolYears;
    private final FileStorage storage;
    private final Clock clock;

    private record Ranked(EduResourceItem item, String match) {}

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ResourceItemResponse> list(CurrentUser user, Query query) {
        String p = LOG_PREFIX + "|list|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            String q = normalize(query.q());
            String teach = normalize(query.teachThemeName());
            List<EduResourceItem> own = items.findAllByOwnerId(user.userId()).stream()
                    .filter(i -> q.isEmpty() || normalize(i.getTitle()).contains(q))
                    .filter(i -> query.kind() == null || query.kind().equals(i.getKind()))
                    .filter(i -> query.ageGroupId() == null
                            || i.getAgeGroupId() == null
                            || query.ageGroupId().equals(i.getAgeGroupId()))
                    .toList();
            Map<Long, List<EduResourceItemTag>> tagMap = tagsOf(own);
            List<EduResourceItem> found = own.stream()
                    .filter(i -> (teach.isEmpty() && query.schoolYearId() == null)
                            || tagMap.getOrDefault(i.getId(), List.of()).stream()
                                    .anyMatch(t -> EduResourceItemTag.THEME.equals(t.getKind())
                                            && (teach.isEmpty() || teach.equals(normalize(t.getName())))
                                            && (query.schoolYearId() == null
                                                    || query.schoolYearId().equals(t.getSchoolYearId()))))
                    .toList();
            Set<Long> attached = attachedIds(user, query.docKey(), query.activityKey());
            Matcher matcher = matcher(query);
            // ponytail: xếp gợi ý trong bộ nhớ (≤ vài nghìn mục/người); chuyển sang SQL khi kho vượt ~10.000 mục
            List<Ranked> ranked = found.stream()
                    .map(i -> new Ranked(i, matcher.match(tagMap.getOrDefault(i.getId(), List.of()))))
                    .sorted(Comparator.comparingInt((Ranked r) -> rank(r.match()))
                            .thenComparing((Ranked r) -> r.item().getUpdatedAt(), Comparator.reverseOrder())
                            .thenComparing((Ranked r) -> r.item().getId(), Comparator.reverseOrder()))
                    .toList();
            int size = Math.max(1, Math.min(MAX_PAGE, query.size() <= 0 ? MAX_PAGE : query.size()));
            int page = Math.max(0, query.page());
            List<Ranked> slice =
                    ranked.stream().skip((long) page * size).limit(size).toList();
            Map<Long, Long> usage =
                    usageOf(slice.stream().map(r -> r.item().getId()).toList());
            Map<Long, String> years = yearNames(user, tagMap);
            List<ResourceItemResponse> rows = slice.stream()
                    .map(r -> response(
                            r.item(),
                            tagMap.getOrDefault(r.item().getId(), List.of()),
                            years,
                            usage.getOrDefault(r.item().getId(), 0L),
                            r.match(),
                            attached.contains(r.item().getId())))
                    .toList();
            var result = new PageResponse<>(rows, page, size, ranked.size(), (ranked.size() + size - 1) / size);
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public ResourceItemResponse update(CurrentUser user, Long id, ResourceItemUpdateRequest request) {
        String p = LOG_PREFIX + "|update|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduResourceItem item = requireOwned(user, id);
            var now = clock.instant();
            if (request.title() != null) {
                String title = titleOr(request.title(), item.getTitle());
                // EDU-88: đổi tên bị chặn cùng điều kiện với xóa (tên hiện trong giáo án)
                if (!title.equals(item.getTitle())) requireUnlocked(item.getId());
                item.rename(title, now);
            }
            if (Boolean.TRUE.equals(request.clearAgeGroup())) item.changeAgeGroup(null, now);
            else if (request.ageGroupId() != null) item.changeAgeGroup(request.ageGroupId(), now);
            List<EduResourceItemTag> current = tags.findAllByItemIdIn(List.of(item.getId()));
            if (request.removeTagIds() != null) {
                Set<Long> remove = new HashSet<>(request.removeTagIds());
                // Nhãn tự thêm đi theo dòng gắn, chỉ nhãn thêm tay bỏ được
                List<EduResourceItemTag> manual = current.stream()
                        .filter(t -> t.getAttachmentId() == null && remove.contains(t.getId()))
                        .toList();
                tags.deleteAll(manual);
                current = current.stream().filter(t -> !manual.contains(t)).toList();
            }
            addManualTags(item.getId(), current, request);
            items.saveAndFlush(item);
            var result = single(user, item);
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void delete(CurrentUser user, Long id) {
        String p = LOG_PREFIX + "|delete|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduResourceItem item = requireOwned(user, id);
            requireUnlocked(id);
            // EDU-85: xóa vĩnh viễn gỡ khỏi mọi giáo án (không đổi trạng thái giáo án); nhãn tự thêm xóa theo dòng gắn
            attachments.deleteAllByItemId(id);
            attachments.flush();
            String key = item.getStorageKey();
            items.delete(item);
            items.flush();
            if (key != null) removeAfterCommit(key);
            log.info("{}|END", p);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceUsagesResponse usages(CurrentUser user, Long id) {
        String p = LOG_PREFIX + "|usages|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            requireOwned(user, id);
            var result = lockOf(id).response();
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FileUrlResponse contentUrl(CurrentUser user, Long id) {
        String p = LOG_PREFIX + "|contentUrl|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            var result = urlOf(storage, requireOwned(user, id));
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public EduResourceItem createFileItem(
            CurrentUser user, Long ageGroupId, FileStorage.StoredFile stored, String name, UUID requestId) {
        return items.saveAndFlush(EduResourceItem.file(
                user.userId(),
                stripExtension(name),
                ageGroupId,
                name,
                stored.contentType(),
                stored.size(),
                stored.key(),
                requestId,
                clock.instant()));
    }

    @Override
    public EduResourceItem findOrCreateLink(CurrentUser user, String rawUrl, String title, Long ageGroupId) {
        String url = LearningResourcePolicy.normalizeLink(rawUrl);
        var existing = items.findFirstByOwnerIdAndKindAndUrlOrderByIdAsc(user.userId(), EduResourceItem.LINK, url);
        if (existing.isPresent()) return existing.get();
        String fallback = url.contains("youtube.com") ? "Video YouTube" : "Tài liệu Google";
        return items.saveAndFlush(
                EduResourceItem.link(user.userId(), titleOr(title, fallback), ageGroupId, url, clock.instant()));
    }

    /** EDU-87: nhãn tự thêm theo dòng gắn; mã mục tiêu Bộ lấy ở server từ MT của kế hoạch năm. */
    @Override
    public void tagAttachment(Long itemId, Long attachmentId, Long schoolYearId, ResourceTagsRequest request) {
        if (request == null) return;
        List<EduResourceItemTag> rows = new ArrayList<>();
        if (request.themeId() != null || blankToNull(request.themeName()) != null)
            rows.add(EduResourceItemTag.of(
                    itemId,
                    EduResourceItemTag.THEME,
                    request.themeId(),
                    null,
                    blankToNull(request.themeName()),
                    schoolYearId,
                    attachmentId));
        Set<Long> seen = new HashSet<>();
        for (EduPlanGoal g : goals.findAllById(request.goals()))
            if (seen.add(g.getId()))
                rows.add(EduResourceItemTag.of(
                        itemId, EduResourceItemTag.GOAL, g.getId(), g.getCurriculumId(), null, null, attachmentId));
        tags.saveAll(rows);
    }

    @Override
    public EduResourceItem requireOwned(CurrentUser user, Long id) {
        return items.findById(id)
                .filter(i -> i.getOwnerId().equals(user.userId()))
                .orElseThrow(() -> GlobalException.of(ApiCode.RESOURCE_ITEM_NOT_FOUND));
    }

    /** Liên kết xem: tệp ⇒ liên kết S3 có hạn; link ⇒ url đã chuẩn hóa (không hết hạn). */
    static FileUrlResponse urlOf(FileStorage storage, EduResourceItem item) {
        if (item.isLink()) return new FileUrlResponse(item.getUrl(), null);
        var url = storage.presignGet(
                FileCategory.LEARNING_RESOURCE, item.getStorageKey(), item.getContentType(), item.getOriginalName());
        return new FileUrlResponse(url.url(), url.expiresAt());
    }

    private record Lock(List<ResourceUsageResponse> rows, List<String> places, boolean review, LocalDate lastDay) {
        boolean locked() {
            return !places.isEmpty();
        }

        LocalDate deletableFrom() {
            return review || lastDay == null ? null : lastDay.plusDays(1);
        }

        ResourceUsagesResponse response() {
            return new ResourceUsagesResponse(rows, locked(), deletableFrom());
        }
    }

    /**
     * EDU-85: khóa xóa/đổi tên khi học liệu gắn ở giáo án ngày Chờ duyệt, hoặc đã ban hành mà ngày dạy là hôm nay hoặc
     * chưa tới; ngày theo {@code Clock} của app (Asia/Ho_Chi_Minh).
     */
    private Lock lockOf(Long itemId) {
        List<EduResourceAttachment> rows = attachments.findAllByItemIdOrderByIdAsc(itemId);
        Map<Long, EduClassDoc> docs = classDocRepository
                .findAllById(
                        rows.stream().map(EduResourceAttachment::getClassDocId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(EduClassDoc::getId, Function.identity()));
        Map<Long, EduClassDocStatus> status = new HashMap<>();
        Map<Long, String> classNames = new HashMap<>();
        LocalDate today = LocalDate.now(clock);
        List<ResourceUsageResponse> out = new ArrayList<>();
        List<String> places = new ArrayList<>();
        boolean review = false;
        LocalDate lastDay = null;
        for (EduResourceAttachment a : rows) {
            EduClassDoc d = docs.get(a.getClassDocId());
            if (d == null) continue;
            EduClassDocStatus s = status.computeIfAbsent(d.getId(), x -> classDocs.dayStatus(d));
            LocalDate day = LocalDate.parse(
                    a.getActivityKey().substring(0, a.getActivityKey().indexOf('#')));
            boolean locked = s == EduClassDocStatus.REVIEW || (s == EduClassDocStatus.APPROVED && !day.isBefore(today));
            if (locked) {
                String cls = classNames.computeIfAbsent(
                        d.getClassroomId(),
                        c -> classrooms
                                .findClassroomInfo(c)
                                .map(ClassroomInfoResponse::name)
                                .orElse("Lớp"));
                places.add(cls + " – " + day.format(DMY) + " – " + a.getActivityLabel());
                if (s == EduClassDocStatus.REVIEW) review = true;
                else if (lastDay == null || day.isAfter(lastDay)) lastDay = day;
            }
            out.add(new ResourceUsageResponse(
                    a.getId(),
                    d.getDocKey(),
                    d.getClassroomId(),
                    day.toString(),
                    a.getActivityKey(),
                    a.getActivityLabel(),
                    s.name(),
                    locked));
        }
        return new Lock(out, places, review, lastDay);
    }

    private void requireUnlocked(Long itemId) {
        Lock lock = lockOf(itemId);
        if (!lock.locked()) return;
        String when = lock.deletableFrom() == null
                ? "Xóa hoặc đổi tên được sau khi giáo án được duyệt hoặc trả lại."
                : "Xóa hoặc đổi tên được từ " + lock.deletableFrom().format(DMY) + ".";
        throw GlobalException.of(ApiCode.RESOURCE_ITEM_LOCKED, String.join("; ", lock.places()), when);
    }

    /** EDU-87: nhãn thêm tay ở trang Kho; bỏ qua nhãn tay đã có (chủ đề so theo tên chuẩn hóa + năm, MT theo id). */
    private void addManualTags(Long itemId, List<EduResourceItemTag> current, ResourceItemUpdateRequest request) {
        List<EduResourceItemTag> manual =
                current.stream().filter(t -> t.getAttachmentId() == null).toList();
        List<EduResourceItemTag> rows = new ArrayList<>();
        if (request.themes() != null)
            for (var t : request.themes()) {
                String name = blankToNull(t.name());
                if (name == null && t.themeId() == null) continue;
                boolean exists = manual.stream()
                                .anyMatch(m -> EduResourceItemTag.THEME.equals(m.getKind())
                                        && Objects.equals(m.getSchoolYearId(), t.schoolYearId())
                                        && (Objects.equals(m.getRefId(), t.themeId()) && t.themeId() != null
                                                || normalize(m.getName()).equals(normalize(name))))
                        || rows.stream()
                                .anyMatch(r -> normalize(r.getName()).equals(normalize(name))
                                        && Objects.equals(r.getSchoolYearId(), t.schoolYearId()));
                if (!exists)
                    rows.add(EduResourceItemTag.of(
                            itemId, EduResourceItemTag.THEME, t.themeId(), null, name, t.schoolYearId(), null));
            }
        if (request.goals() != null) {
            Set<Long> have = manual.stream()
                    .filter(m -> EduResourceItemTag.GOAL.equals(m.getKind()))
                    .map(EduResourceItemTag::getRefId)
                    .collect(Collectors.toSet());
            for (EduPlanGoal g : goals.findAllById(request.goals().stream()
                    .map(ResourceItemUpdateRequest.GoalTag::goalId)
                    .toList()))
                if (have.add(g.getId()))
                    rows.add(EduResourceItemTag.of(
                            itemId, EduResourceItemTag.GOAL, g.getId(), g.getCurriculumId(), null, null, null));
        }
        tags.saveAll(rows);
    }

    private interface Matcher {
        String match(List<EduResourceItemTag> itemTags);
    }

    /** EDU-87: Cùng MT (id hoặc mã Bộ) trước, rồi Cùng chủ đề (id hoặc tên chuẩn hóa, mọi năm). */
    private Matcher matcher(Query query) {
        Set<Long> goalIds = new HashSet<>(query.goalIds() == null ? List.of() : query.goalIds());
        Set<Long> curriculum = goalIds.isEmpty()
                ? Set.of()
                : goals.findAllById(goalIds).stream()
                        .map(EduPlanGoal::getCurriculumId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        String themeName = normalize(query.themeName());
        return itemTags -> {
            boolean goal = itemTags.stream()
                    .anyMatch(t -> EduResourceItemTag.GOAL.equals(t.getKind())
                            && (goalIds.contains(t.getRefId())
                                    || (t.getCurriculumGoalId() != null
                                            && curriculum.contains(t.getCurriculumGoalId()))));
            if (goal) return "GOAL";
            boolean theme = itemTags.stream()
                    .anyMatch(t -> EduResourceItemTag.THEME.equals(t.getKind())
                            && ((query.themeId() != null && query.themeId().equals(t.getRefId()))
                                    || (!themeName.isEmpty() && themeName.equals(normalize(t.getName())))));
            return theme ? "THEME" : null;
        };
    }

    private static int rank(String match) {
        return "GOAL".equals(match) ? 0 : "THEME".equals(match) ? 1 : 2;
    }

    static String normalize(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Set<Long> attachedIds(CurrentUser user, String docKey, String activityKey) {
        if (docKey == null || activityKey == null) return Set.of();
        EduClassDoc doc = classDocs.requireDayAccess(user, docKey, false);
        return attachments.findAllByClassDocIdAndActivityKey(doc.getId(), activityKey).stream()
                .map(EduResourceAttachment::getItemId)
                .collect(Collectors.toSet());
    }

    private Map<Long, List<EduResourceItemTag>> tagsOf(Collection<EduResourceItem> list) {
        if (list.isEmpty()) return Map.of();
        return tags.findAllByItemIdIn(list.stream().map(EduResourceItem::getId).toList()).stream()
                .collect(Collectors.groupingBy(EduResourceItemTag::getItemId));
    }

    private Map<Long, Long> usageOf(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : attachments.countByItemIds(ids)) out.put((Long) row[0], (Long) row[1]);
        return out;
    }

    /** Tên năm học cho nhãn chủ đề (chỉ gọi khi có nhãn mang năm học). */
    private Map<Long, String> yearNames(CurrentUser user, Map<Long, List<EduResourceItemTag>> tagMap) {
        boolean any = tagMap.values().stream().flatMap(List::stream).anyMatch(t -> t.getSchoolYearId() != null);
        // HashMap: nhãn không có năm học tra get(null) — Map.of() sẽ ném NPE
        Map<Long, String> out = new HashMap<>();
        if (any) schoolYears.getSchoolYears(user, null).forEach(y -> out.putIfAbsent(y.id(), y.name()));
        return out;
    }

    private ResourceItemResponse single(CurrentUser user, EduResourceItem item) {
        Map<Long, List<EduResourceItemTag>> tagMap = tagsOf(List.of(item));
        return response(
                item,
                tagMap.getOrDefault(item.getId(), List.of()),
                yearNames(user, tagMap),
                usageOf(List.of(item.getId())).getOrDefault(item.getId(), 0L),
                null,
                false);
    }

    private static ResourceItemResponse response(
            EduResourceItem i,
            List<EduResourceItemTag> itemTags,
            Map<Long, String> years,
            long usage,
            String match,
            boolean attached) {
        // Chủ đề đã dạy: gộp nhãn chủ đề không trùng theo tên chuẩn hóa + năm học
        Map<String, ResourceItemResponse.TaughtTheme> taught = new LinkedHashMap<>();
        for (EduResourceItemTag t : itemTags)
            if (EduResourceItemTag.THEME.equals(t.getKind()) && t.getName() != null)
                taught.putIfAbsent(
                        normalize(t.getName()) + "|" + t.getSchoolYearId(),
                        new ResourceItemResponse.TaughtTheme(
                                t.getName(), t.getSchoolYearId(), years.get(t.getSchoolYearId())));
        return new ResourceItemResponse(
                i.getId(),
                i.getKind(),
                i.getTitle(),
                i.getUrl(),
                i.getOriginalName(),
                i.getContentType(),
                i.getByteSize(),
                i.getAgeGroupId(),
                itemTags.stream()
                        .map(t -> new ResourceTagResponse(
                                t.getId(),
                                t.getKind(),
                                t.getRefId(),
                                t.getCurriculumGoalId(),
                                t.getName(),
                                t.getSchoolYearId(),
                                years.get(t.getSchoolYearId()),
                                t.getAttachmentId() != null))
                        .toList(),
                List.copyOf(taught.values()),
                usage,
                match,
                attached,
                i.getUpdatedAt());
    }

    /** EDU-85: tệp chỉ xóa sau khi xóa học liệu thành công; lỗi xóa tệp chỉ ghi log (removeQuietly). */
    private void removeAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.removeQuietly(FileCategory.LEARNING_RESOURCE, key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.removeQuietly(FileCategory.LEARNING_RESOURCE, key);
            }
        });
    }

    private static String titleOr(String title, String fallback) {
        String t = title == null ? "" : title.strip();
        if (t.isEmpty()) t = fallback;
        if (t.length() > 255) t = t.substring(0, 255);
        return t;
    }

    static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.isBlank() ? name : base;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
