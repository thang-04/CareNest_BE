package com.carenest.service.eduplan.impl;

import com.carenest.dto.common.FileUrlResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.eduplan.*;
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
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.LearningResourcePolicy;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceLibraryServiceImpl implements ResourceLibraryService {
    private static final String LOG_PREFIX = "[ResourceLibraryServiceImpl]";
    private static final int MAX_PAGE = 50;
    private static final List<Long> NONE = List.of(-1L);

    private final EduResourceItemRepository items;
    private final EduResourceItemTagRepository tags;
    private final EduResourceAttachmentRepository attachments;
    private final EduPlanGoalRepository goals;
    private final EduClassDocRepository classDocRepository;
    private final EduClassDocService classDocs;
    private final StaffAssignmentService assignments;
    private final FileStorage storage;
    private final Clock clock;

    /** Ngữ cảnh quyền của người xem, tính một lần mỗi yêu cầu (EDU-84). */
    private record Ranked(EduResourceItem item, String match) {}

    record Viewer(Long userId, boolean vicePrincipal, Set<Long> classIds, Set<Long> leaderAgeGroups) {}

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ResourceItemResponse> list(CurrentUser user, Query query) {
        String p = LOG_PREFIX + "|list|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            Viewer v = viewer(user);
            String view = query.view() == null ? "ALL" : query.view();
            String q = query.q() == null ? "" : query.q().strip().toLowerCase(Locale.ROOT);
            List<EduResourceItem> found = items.findVisible(
                            v.userId(), orNone(v.classIds()), v.vicePrincipal(), orNone(v.leaderAgeGroups()))
                    .stream()
                    .filter(i -> inView(v, i, view))
                    .filter(i -> q.isEmpty() || i.getTitle().toLowerCase(Locale.ROOT).contains(q))
                    .filter(i -> query.kind() == null || query.kind().equals(i.getKind()))
                    .filter(i -> query.ageGroupId() == null
                            || i.getAgeGroupId() == null
                            || query.ageGroupId().equals(i.getAgeGroupId()))
                    .toList();
            Map<Long, List<EduResourceItemTag>> tagMap = tagsOf(found);
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
            List<Ranked> slice = ranked.stream().skip((long) page * size).limit(size).toList();
            Map<Long, Long> usage = usageOf(slice.stream().map(r -> r.item().getId()).toList());
            List<ResourceItemResponse> rows = slice.stream()
                    .map(r -> response(v, r.item(), tagMap, usage, r.match(), attached.contains(r.item().getId())))
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
    public ResourceItemResponse upload(
            CurrentUser user, MultipartFile file, String title, Long ageGroupId, UUID requestId) {
        String p = LOG_PREFIX + "|upload|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            if (requestId == null) throw GlobalException.of(ApiCode.UPLOAD_REQUEST_ID_REQUIRED);
            var existing = items.findByOwnerIdAndUploadRequestId(user.userId(), requestId);
            if (existing.isPresent()) {
                log.info("{}|END", p);
                return single(user, existing.get());
            }
            String name = LearningResourcePolicy.safeName(file.getOriginalFilename());
            FileStorage.StoredFile stored;
            try (var input = file.getInputStream()) {
                stored = storage.store(FileCategory.LEARNING_RESOURCE, user.userId(), name, input, file.getSize());
            } catch (IOException ex) {
                throw GlobalException.of(ApiCode.FILE_UNREADABLE);
            }
            removeIfRolledBack(stored.key());
            EduResourceItem item = items.saveAndFlush(EduResourceItem.file(
                    user.userId(),
                    titleOr(title, stripExtension(name)),
                    ageGroupId,
                    name,
                    stored.contentType(),
                    stored.size(),
                    stored.key(),
                    requestId,
                    clock.instant()));
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
    public ResourceItemResponse addLink(CurrentUser user, ResourceLibraryLinkRequest request) {
        String p = LOG_PREFIX + "|addLink|userId=" + user.userId();
        log.info("{}|START", p);
        try {
            var result = single(user, findOrCreateLink(user, request.url(), request.title(), request.ageGroupId(), null));
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
            Viewer v = viewer(user);
            EduResourceItem item = requireEditable(v, id);
            var now = clock.instant();
            if (request.title() != null) item.rename(titleOr(request.title(), item.getTitle()), now);
            if (Boolean.TRUE.equals(request.clearAgeGroup())) item.changeAgeGroup(null, now);
            else if (request.ageGroupId() != null) item.changeAgeGroup(request.ageGroupId(), now);
            if (request.hidden() != null) item.changeHidden(request.hidden(), now);
            if (request.tags() != null) {
                tags.deleteAllByItemId(item.getId());
                tags.flush();
                saveTags(item, request.tags());
            }
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
    public ResourceItemResponse share(CurrentUser user, Long id, ResourceShareRequest request) {
        String p = LOG_PREFIX + "|share|userId=" + user.userId() + "|id=" + id + "|scope=" + request.scope();
        log.info("{}|START", p);
        try {
            Viewer v = viewer(user);
            EduResourceItem item = requireEditable(v, id);
            if (item.isPending()) throw GlobalException.of(ApiCode.RESOURCE_PENDING_REVIEW);
            boolean owner = item.getOwnerId().equals(v.userId());
            var now = clock.instant();
            switch (request.scope()) {
                case EduResourceItem.MINE -> item.moveTo(EduResourceItem.MINE, null, now);
                case EduResourceItem.CLASS -> {
                    // EDU-84: chỉ chia sẻ cho lớp mình đang được phân công
                    if (!owner || request.classroomId() == null || !v.classIds().contains(request.classroomId()))
                        throw GlobalException.of(ApiCode.RESOURCE_ITEM_FORBIDDEN);
                    item.moveTo(EduResourceItem.CLASS, request.classroomId(), now);
                }
                default -> {
                    if (EduResourceItem.SCHOOL.equals(item.getScope()))
                        throw GlobalException.of(ApiCode.RESOURCE_SHARE_STATE);
                    // PHT đưa thẳng lên kho trường; giáo viên/tổ trưởng gửi đề nghị chờ duyệt
                    if (v.vicePrincipal()) item.moveTo(EduResourceItem.SCHOOL, null, now);
                    else item.requestSchool(now);
                }
            }
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
    public ResourceItemResponse withdraw(CurrentUser user, Long id) {
        String p = LOG_PREFIX + "|withdraw|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            EduResourceItem item = items.findById(id).orElseThrow(() -> GlobalException.of(ApiCode.RESOURCE_ITEM_NOT_FOUND));
            if (!item.getOwnerId().equals(user.userId())) throw GlobalException.of(ApiCode.RESOURCE_ITEM_FORBIDDEN);
            if (!item.isPending()) throw GlobalException.of(ApiCode.RESOURCE_SHARE_STATE);
            item.withdraw(clock.instant());
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
    public ResourceItemResponse review(CurrentUser user, Long id, ResourceReviewRequest request) {
        String p = LOG_PREFIX + "|review|userId=" + user.userId() + "|id=" + id + "|approve=" + request.approve();
        log.info("{}|START", p);
        try {
            Viewer v = viewer(user);
            EduResourceItem item = items.findById(id).orElseThrow(() -> GlobalException.of(ApiCode.RESOURCE_ITEM_NOT_FOUND));
            if (!item.isPending()) throw GlobalException.of(ApiCode.RESOURCE_SHARE_STATE);
            if (!canReview(v, item)) throw GlobalException.of(ApiCode.RESOURCE_ITEM_FORBIDDEN);
            String reason = request.reason() == null ? "" : request.reason().strip();
            if (!request.approve() && reason.isEmpty()) throw GlobalException.of(ApiCode.RESOURCE_REVIEW_REASON_REQUIRED);
            item.reviewed(request.approve(), reason, v.userId(), clock.instant());
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
            EduResourceItem item = requireEditable(viewer(user), id);
            if (item.isPending()) throw GlobalException.of(ApiCode.RESOURCE_PENDING_REVIEW);
            // EDU-85: còn giáo án dùng thì chỉ ẩn được
            long used = attachments.countByItemId(id);
            if (used > 0) throw GlobalException.of(ApiCode.RESOURCE_ITEM_IN_USE, used);
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
    public List<ResourceUsageResponse> usages(CurrentUser user, Long id) {
        String p = LOG_PREFIX + "|usages|userId=" + user.userId() + "|id=" + id;
        log.info("{}|START", p);
        try {
            requireVisible(user, id);
            List<EduResourceAttachment> rows = attachments.findAllByItemIdOrderByIdAsc(id);
            Map<Long, EduClassDoc> docs = classDocRepository
                    .findAllById(rows.stream().map(EduResourceAttachment::getClassDocId).collect(Collectors.toSet()))
                    .stream()
                    .collect(Collectors.toMap(EduClassDoc::getId, Function.identity()));
            var result = rows.stream()
                    .filter(a -> docs.containsKey(a.getClassDocId()))
                    .map(a -> {
                        EduClassDoc d = docs.get(a.getClassDocId());
                        return new ResourceUsageResponse(
                                a.getId(),
                                d.getDocKey(),
                                d.getClassroomId(),
                                a.getActivityKey().substring(0, a.getActivityKey().indexOf('#')),
                                a.getActivityKey(),
                                a.getActivityLabel());
                    })
                    .toList();
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
            var result = urlOf(storage, requireVisible(user, id));
            log.info("{}|END", p);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", p, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public EduResourceItem createFileItem(
            CurrentUser user,
            Long ageGroupId,
            FileStorage.StoredFile stored,
            String name,
            UUID requestId,
            ResourceTagsRequest tagRequest) {
        EduResourceItem item = items.saveAndFlush(EduResourceItem.file(
                user.userId(),
                stripExtension(name),
                ageGroupId,
                name,
                stored.contentType(),
                stored.size(),
                stored.key(),
                requestId,
                clock.instant()));
        saveTags(item, tagRequest);
        return item;
    }

    @Override
    public EduResourceItem findOrCreateLink(
            CurrentUser user, String rawUrl, String title, Long ageGroupId, ResourceTagsRequest tagRequest) {
        String url = LearningResourcePolicy.normalizeLink(rawUrl);
        var existing = items.findFirstByOwnerIdAndKindAndUrlOrderByIdAsc(user.userId(), EduResourceItem.LINK, url);
        if (existing.isPresent()) return existing.get();
        String fallback = url.contains("youtube.com") ? "Video YouTube" : "Tài liệu Google";
        EduResourceItem item = items.saveAndFlush(
                EduResourceItem.link(user.userId(), titleOr(title, fallback), ageGroupId, url, clock.instant()));
        saveTags(item, tagRequest);
        return item;
    }

    @Override
    public EduResourceItem requireVisible(CurrentUser user, Long id) {
        Viewer v = viewer(user);
        return items.findById(id)
                .filter(i -> visible(v, i))
                .orElseThrow(() -> GlobalException.of(ApiCode.RESOURCE_ITEM_NOT_FOUND));
    }

    /** Liên kết xem: tệp ⇒ liên kết S3 có hạn; link ⇒ url đã chuẩn hóa (không hết hạn). */
    static FileUrlResponse urlOf(FileStorage storage, EduResourceItem item) {
        if (item.isLink()) return new FileUrlResponse(item.getUrl(), null);
        var url = storage.presignGet(
                FileCategory.LEARNING_RESOURCE, item.getStorageKey(), item.getContentType(), item.getOriginalName());
        return new FileUrlResponse(url.url(), url.expiresAt());
    }

    private Viewer viewer(CurrentUser user) {
        LocalDate today = LocalDate.now(clock);
        return new Viewer(
                user.userId(),
                user.hasRole(CareNestRole.VICE_PRINCIPAL),
                assignments.findCurrentTeacherClassroomIds(user.userId(), today),
                assignments.findActiveTeamLeaderAgeGroupIds(user.userId(), today));
    }

    static boolean visible(Viewer v, EduResourceItem i) {
        return i.getOwnerId().equals(v.userId())
                || EduResourceItem.SCHOOL.equals(i.getScope())
                || (EduResourceItem.CLASS.equals(i.getScope()) && v.classIds().contains(i.getClassroomId()))
                || canReview(v, i);
    }

    /** EDU-84: PHT duyệt mọi đề nghị; tổ trưởng duyệt đề nghị của nhóm tuổi mình, không tự duyệt của mình. */
    static boolean canReview(Viewer v, EduResourceItem i) {
        if (!i.isPending() || i.getOwnerId().equals(v.userId())) return false;
        return v.vicePrincipal() || (i.getAgeGroupId() != null && v.leaderAgeGroups().contains(i.getAgeGroupId()));
    }

    static boolean canEdit(Viewer v, EduResourceItem i) {
        return i.getOwnerId().equals(v.userId()) || (v.vicePrincipal() && EduResourceItem.SCHOOL.equals(i.getScope()));
    }

    private static boolean inView(Viewer v, EduResourceItem i, String view) {
        boolean mine = i.getOwnerId().equals(v.userId());
        return switch (view) {
            case "MINE" -> mine && !i.isHidden();
            case "CLASS" -> EduResourceItem.CLASS.equals(i.getScope()) && !i.isHidden() && (mine || v.classIds().contains(i.getClassroomId()));
            case "SCHOOL" -> EduResourceItem.SCHOOL.equals(i.getScope()) && !i.isHidden();
            case "PENDING" -> canReview(v, i);
            case "HIDDEN" -> i.isHidden() && canEdit(v, i);
            // ALL: mọi học liệu dùng được (bỏ đề nghị của người khác chỉ hiện để duyệt)
            default -> !i.isHidden() && (mine || !EduResourceItem.MINE.equals(i.getScope()));
        };
    }

    private EduResourceItem requireEditable(Viewer v, Long id) {
        EduResourceItem item = items.findById(id)
                .filter(i -> visible(v, i))
                .orElseThrow(() -> GlobalException.of(ApiCode.RESOURCE_ITEM_NOT_FOUND));
        if (!canEdit(v, item)) throw GlobalException.of(ApiCode.RESOURCE_ITEM_FORBIDDEN);
        return item;
    }

    /** EDU-87: nhãn chủ đề + MT; mã mục tiêu Bộ lấy ở server từ MT của kế hoạch năm. */
    private void saveTags(EduResourceItem item, ResourceTagsRequest request) {
        if (request == null) return;
        List<EduResourceItemTag> rows = new ArrayList<>();
        if (request.themeId() != null || (request.themeName() != null && !request.themeName().isBlank()))
            rows.add(EduResourceItemTag.of(
                    item.getId(), EduResourceItemTag.THEME, request.themeId(), null, blankToNull(request.themeName())));
        Set<Long> seen = new HashSet<>();
        for (EduPlanGoal g : goals.findAllById(request.goals())) {
            if (seen.add(g.getId()))
                rows.add(EduResourceItemTag.of(item.getId(), EduResourceItemTag.GOAL, g.getId(), g.getCurriculumId(), null));
        }
        tags.saveAll(rows);
    }

    private interface Matcher {
        String match(List<EduResourceItemTag> itemTags);
    }

    /** EDU-87: Cùng MT (id hoặc mã Bộ) trước, rồi Cùng chủ đề (id hoặc tên chuẩn hóa). */
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
                                    || (t.getCurriculumGoalId() != null && curriculum.contains(t.getCurriculumGoalId()))));
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

    private ResourceItemResponse single(CurrentUser user, EduResourceItem item) {
        Viewer v = viewer(user);
        return response(v, item, tagsOf(List.of(item)), usageOf(List.of(item.getId())), null, false);
    }

    private static ResourceItemResponse response(
            Viewer v,
            EduResourceItem i,
            Map<Long, List<EduResourceItemTag>> tagMap,
            Map<Long, Long> usage,
            String match,
            boolean attached) {
        return new ResourceItemResponse(
                i.getId(),
                i.getKind(),
                i.getTitle(),
                i.getUrl(),
                i.getOriginalName(),
                i.getContentType(),
                i.getByteSize(),
                i.getOwnerId(),
                i.getOwnerId().equals(v.userId()),
                i.getScope(),
                i.getClassroomId(),
                i.getShareStatus(),
                i.getShareReason(),
                i.getAgeGroupId(),
                i.isHidden(),
                tagMap.getOrDefault(i.getId(), List.of()).stream()
                        .map(t -> new ResourceTagResponse(t.getKind(), t.getRefId(), t.getCurriculumGoalId(), t.getName()))
                        .toList(),
                usage.getOrDefault(i.getId(), 0L),
                match,
                attached,
                canEdit(v, i),
                canReview(v, i),
                i.getUpdatedAt());
    }

    private void removeIfRolledBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.removeQuietly(FileCategory.LEARNING_RESOURCE, key);
            }
        });
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

    private static Collection<Long> orNone(Set<Long> ids) {
        return ids == null || ids.isEmpty() ? NONE : ids;
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
