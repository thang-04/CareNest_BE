package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.ChildListItemResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.child.Child;
import com.carenest.entity.child.ChildAllergy;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildAllergyRepository;
import com.carenest.repository.child.ChildHealthDeclarationRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildListService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh sách trẻ (D-4, G94-180/CHD-09). Một truy vấn lấy id + lớp hiển thị đã lọc, sắp xếp cho cả phạm vi (quy mô một
 * trường); cắt trang ở đây rồi đọc chi tiết theo lô cho trang (trẻ, lớp, dị ứng, khai báo, phụ huynh, số tài khoản).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChildListServiceImpl implements ChildListService {
    private static final String LOG_PREFIX = "[ChildListServiceImpl]";
    // IN () không hợp lệ trong SQL: tập rỗng truyền id không tồn tại
    private static final List<Long> NO_IDS = List.of(-1L);
    // Mặc định bỏ trẻ đã nghỉ: màn Tài khoản phụ huynh không gửi trạng thái (plan tiếp nhận mục 4.7)
    private static final Set<ChildStatus> DEFAULT_STATUSES =
            EnumSet.of(ChildStatus.ACTIVE, ChildStatus.PENDING_PLACEMENT);

    private final ChildRepository childRepository;
    private final ChildAllergyRepository allergyRepository;
    private final ChildHealthDeclarationRepository declarationRepository;
    private final GuardianViews guardianViews;
    private final GuardianChildLinkService guardianChildLinkService;
    private final ClassroomService classroomService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final StaffAssignmentService staffAssignmentService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChildListItemResponse> searchChildren(CurrentUser actor, ChildListFilter filter) {
        String logPrefix = LOG_PREFIX + "|searchChildren|userId=" + actor.userId() + "|schoolYearId="
                + filter.schoolYearId() + "|campusId=" + filter.campusId() + "|classId=" + filter.classId() + "|page="
                + filter.page() + "|size=" + filter.size();
        log.info("{}|START", logPrefix);
        try {
            // AUTH-04: danh sách theo phạm vi nhà trường chỉ cho HT, PHT, GV (phụ huynh xem con qua API riêng)
            if (!actor.hasRole(CareNestRole.PRINCIPAL)
                    && !actor.hasRole(CareNestRole.VICE_PRINCIPAL)
                    && !actor.hasRole(CareNestRole.TEACHER)) {
                throw GlobalException.of(ApiCode.FORBIDDEN);
            }
            LocalDate today = LocalDate.now(clock);
            boolean wholeSchool = staffAccessPolicy.managesWholeSchool(actor);
            Set<Long> campusIds = wholeSchool ? Set.of() : staffAccessPolicy.managedCampusIds(actor);
            Set<Long> classIds = wholeSchool || !actor.hasRole(CareNestRole.TEACHER)
                    ? Set.of()
                    : staffAssignmentService.findCurrentTeacherClassroomIds(actor.userId(), today);
            if (!wholeSchool) {
                checkFilterScope(filter, campusIds, classIds);
            }
            Set<ChildStatus> statuses =
                    filter.statuses() == null || filter.statuses().isEmpty() ? DEFAULT_STATUSES : filter.statuses();
            String keyword =
                    filter.keyword() == null ? "" : filter.keyword().strip().toLowerCase(Locale.ROOT);

            List<ChildRepository.ChildListRow> rows = childRepository.findListRows(
                    filter.schoolYearId(),
                    today,
                    statuses.stream().map(Enum::name).toList(),
                    wholeSchool,
                    campusIds.isEmpty() ? NO_IDS : campusIds,
                    classIds.isEmpty() ? NO_IDS : classIds,
                    filter.campusId(),
                    filter.classId(),
                    keyword.isEmpty() ? "" : "%" + keyword + "%");
            int total = rows.size();
            int from = (int) Math.min((long) filter.page() * filter.size(), total);
            int to = Math.min(from + filter.size(), total);
            List<ChildRepository.ChildListRow> pageRows = rows.subList(from, to);

            List<ChildListItemResponse> items = items(actor, pageRows, wholeSchool, campusIds);
            int totalPages = (total + filter.size() - 1) / filter.size();
            log.info("{}|total={}|END", logPrefix, total);
            return new PageResponse<>(items, filter.page(), filter.size(), total, totalPages);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Như trước: lọc lớp / điểm trường ngoài phạm vi ⇒ 403 (AUTH-08), không trả danh sách rỗng. */
    private void checkFilterScope(ChildListFilter filter, Set<Long> campusIds, Set<Long> classIds) {
        if (filter.classId() != null && !classIds.contains(filter.classId())) {
            Long classCampus = classroomService.findCampusId(filter.classId()).orElse(null);
            if (classCampus == null || !campusIds.contains(classCampus)) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
        }
        if (filter.campusId() != null && !campusIds.contains(filter.campusId())) {
            boolean teachesThere = !classIds.isEmpty()
                    && classroomService.findCampusIds(classIds).containsValue(filter.campusId());
            if (!teachesThere) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
        }
    }

    private List<ChildListItemResponse> items(
            CurrentUser actor, List<ChildRepository.ChildListRow> rows, boolean wholeSchool, Set<Long> campusIds) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> childIds =
                rows.stream().map(ChildRepository.ChildListRow::getChildId).toList();
        Map<Long, Child> children = childRepository.findAllById(childIds).stream()
                .collect(Collectors.toMap(Child::getId, Function.identity()));
        Map<Long, ClassroomInfoResponse> classes = new HashMap<>();
        rows.stream()
                .map(ChildRepository.ChildListRow::getClassroomId)
                .filter(id -> id != null && !classes.containsKey(id))
                .forEach(id -> classroomService.findClassroomInfo(id).ifPresent(c -> classes.put(id, c)));
        Map<Long, List<String>> allergies = allergyRepository.findAllByChildIdInOrderByIdAsc(childIds).stream()
                .collect(Collectors.groupingBy(
                        ChildAllergy::getChildId, Collectors.mapping(ChildAllergy::getName, Collectors.toList())));
        Set<Long> declared = new HashSet<>(declarationRepository.findDeclaredChildIds(childIds));
        Map<Long, List<ChildGuardianResponse>> guardians = guardianViews.of(childIds);
        Map<Long, Long> accountCounts = guardianChildLinkService.countGuardiansByChildIds(childIds);
        boolean managesParents = actor.hasPermission(Permission.PARENT_ACCOUNT_MANAGE);

        return rows.stream()
                .map(row -> {
                    Child child = children.get(row.getChildId());
                    ClassroomInfoResponse cls = row.getClassroomId() == null ? null : classes.get(row.getClassroomId());
                    Long campusId = cls != null ? cls.campusId() : child.getIntakeCampusId();
                    boolean canManage = managesParents
                            && child.getStatus() != ChildStatus.LEFT
                            && (wholeSchool || campusIds.contains(campusId));
                    return new ChildListItemResponse(
                            child.getId(),
                            child.getCode(),
                            child.getFullName(),
                            child.getDateOfBirth(),
                            child.getGender(),
                            child.getStatus(),
                            campusId,
                            cls == null ? null : cls.id(),
                            cls == null ? null : cls.name(),
                            cls == null ? null : cls.ageGroupId(),
                            cls == null ? null : cls.schoolYearId(),
                            child.getEnrolledOn(),
                            child.getLeftOn(),
                            child.getReadmittedOn(),
                            allergies.getOrDefault(child.getId(), List.of()),
                            declared.contains(child.getId()),
                            minimized(guardians.getOrDefault(child.getId(), List.of())),
                            canManage,
                            child.getPhotoKey() == null ? null : "/children/" + child.getId() + "/photo",
                            cls == null ? null : cls.id(),
                            accountCounts.getOrDefault(child.getId(), 0L));
                })
                .toList();
    }

    // AUTH-03: danh sách không cần CCCD, email của phụ huynh (hồ sơ trẻ mới có)
    private static List<ChildGuardianResponse> minimized(Collection<ChildGuardianResponse> guardians) {
        return guardians.stream()
                .map(g -> new ChildGuardianResponse(
                        g.fullName(),
                        g.relation(),
                        g.phone(),
                        null,
                        null,
                        g.accountStatus(),
                        g.smsStatus(),
                        g.smsAt(),
                        g.photoUrl(),
                        g.id(),
                        g.accountRecipient()))
                .toList();
    }
}
