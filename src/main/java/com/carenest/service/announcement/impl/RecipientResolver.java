package com.carenest.service.announcement.impl;

import com.carenest.dto.child.ClassChildResponse;
import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.announcement.SchoolAnnouncement.Target;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AnnouncementScopeType;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.RecipientRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Người nhận thông báo của trường theo phạm vi × vai trò, chốt tại ngày gửi thực tế (FR-011, FR-012, clarify Q4): phụ
 * huynh của trẻ đang học trong lớp thuộc phạm vi; GV lớp; tài khoản bếp các điểm trường; BGH = HT + PHT các điểm trường
 * của phạm vi. Bỏ trùng (PAR-06), bỏ người gửi.
 */
@Component
@RequiredArgsConstructor
public class RecipientResolver {

    private final ClassroomService classroomService;
    private final EnrollmentService enrollmentService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final StaffAssignmentService staffAssignmentService;
    private final StaffAccessPolicy staffAccessPolicy;

    /** Người nhận theo vai trò; một người có thể ở nhiều vai trò, {@link #all} gộp và bỏ trùng. */
    public Map<RecipientRole, Set<Long>> resolve(Target target, Long senderId, LocalDate date) {
        List<ClassroomInfoResponse> classes = classesOf(target);
        Set<Long> campusIds = campusIdsOf(target, classes);
        Map<RecipientRole, Set<Long>> result = new EnumMap<>(RecipientRole.class);
        for (RecipientRole role : target.roles()) {
            Set<Long> ids =
                    switch (role) {
                        case PARENT -> parentsOf(classes, date);
                        case TEACHER -> teachersOf(classes, date);
                        case KITCHEN ->
                            campusIds.stream()
                                    .flatMap(c -> staffAssignmentService.findCampusKitchenIds(c, date).stream())
                                    .collect(Collectors.toCollection(LinkedHashSet::new));
                        case BGH -> bghOf(campusIds, date);
                    };
            ids.remove(senderId);
            ids.remove(null);
            result.put(role, ids);
        }
        return result;
    }

    public static Set<Long> all(Map<RecipientRole, Set<Long>> byRole) {
        Set<Long> all = new LinkedHashSet<>();
        byRole.values().forEach(all::addAll);
        return all;
    }

    /** Dữ liệu phạm vi hợp lệ; sai ⇒ INVALID_REQUEST_DATA kèm lỗi từng trường. */
    public static void validate(Target target) {
        List<FieldErrorResponse> errors = new ArrayList<>();
        if (target.scopeType() == null) {
            errors.add(new FieldErrorResponse("target.scopeType", "Chọn phạm vi người nhận"));
        } else {
            switch (target.scopeType()) {
                case CAMPUS -> require(target.campusId(), "target.campusId", "Chọn điểm trường", errors);
                case AGE_GROUP -> require(target.ageGroupId(), "target.ageGroupId", "Chọn khối", errors);
                case CLASSROOM -> require(target.classroomId(), "target.classroomId", "Chọn lớp", errors);
                case SCHOOL -> {
                    // Toàn trường không cần id
                }
            }
        }
        if (target.roles() == null || target.roles().isEmpty()) {
            errors.add(new FieldErrorResponse("target.roles", "Chọn ít nhất một vai trò người nhận"));
        }
        if (!errors.isEmpty()) {
            throw GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, errors);
        }
    }

    /** HT gửi mọi phạm vi; PHT chỉ trong điểm trường được phân công, không toàn trường (FR-013, AUTH-02). */
    public void checkCanSend(CurrentUser sender, Target target) {
        if (staffAccessPolicy.managesWholeSchool(sender)) {
            return;
        }
        Set<Long> managed = staffAccessPolicy.managedCampusIds(sender);
        Long campusId =
                switch (target.scopeType()) {
                    case SCHOOL -> null;
                    case CAMPUS, AGE_GROUP -> target.campusId();
                    case CLASSROOM ->
                        classroomService.findCampusId(target.classroomId()).orElse(null);
                };
        if (campusId == null || !managed.contains(campusId)) {
            throw GlobalException.of(ApiCode.ANNOUNCEMENT_SCOPE_DENIED);
        }
    }

    /** BGH xem được thông báo: HT mọi thông báo; PHT thông báo thuộc điểm trường mình hoặc do mình gửi. */
    public boolean canView(CurrentUser viewer, Target target, Long senderId) {
        return Objects.equals(viewer.userId(), senderId)
                || staffAccessPolicy.managesWholeSchool(viewer)
                || (target.campusId() != null
                        && staffAccessPolicy.managedCampusIds(viewer).contains(target.campusId()));
    }

    /** Lớp ⇒ điền điểm trường của lớp để PHT điểm trường đó xem được trong danh sách. */
    public Target normalize(Target target) {
        if (target.scopeType() == AnnouncementScopeType.CLASSROOM) {
            return new Target(
                    target.scopeType(),
                    classroomService.findCampusId(target.classroomId()).orElse(null),
                    null,
                    target.classroomId(),
                    target.roles());
        }
        return target;
    }

    public boolean managesWholeSchool(CurrentUser user) {
        return staffAccessPolicy.managesWholeSchool(user);
    }

    public Set<Long> managedCampusIds(CurrentUser user) {
        return staffAccessPolicy.managedCampusIds(user);
    }

    private List<ClassroomInfoResponse> classesOf(Target target) {
        return classroomService.findActiveYearClassrooms().stream()
                .filter(c -> c.status() == ClassroomStatus.ACTIVE)
                .filter(c -> switch (target.scopeType()) {
                    case SCHOOL -> true;
                    case CAMPUS -> Objects.equals(c.campusId(), target.campusId());
                    case AGE_GROUP ->
                        Objects.equals(c.ageGroupId(), target.ageGroupId())
                                && (target.campusId() == null || Objects.equals(c.campusId(), target.campusId()));
                    case CLASSROOM -> Objects.equals(c.id(), target.classroomId());
                })
                .toList();
    }

    private static Set<Long> campusIdsOf(Target target, List<ClassroomInfoResponse> classes) {
        Set<Long> ids = classes.stream().map(ClassroomInfoResponse::campusId).collect(Collectors.toSet());
        if (target.scopeType() == AnnouncementScopeType.CAMPUS) {
            ids.add(target.campusId());
        }
        return ids;
    }

    private Set<Long> parentsOf(List<ClassroomInfoResponse> classes, LocalDate date) {
        Set<Long> childIds = new LinkedHashSet<>();
        for (ClassroomInfoResponse c : classes) {
            enrollmentService.findChildrenInClassroom(c.id(), date).stream()
                    .map(ClassChildResponse::id)
                    .forEach(childIds::add);
        }
        if (childIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return guardianChildLinkService.findLinksOfChildren(childIds).stream()
                .map(GuardianLinkResponse::userId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> teachersOf(List<ClassroomInfoResponse> classes, LocalDate date) {
        if (classes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Long> ids = new LinkedHashSet<>();
        staffAssignmentService
                .findCurrentTeacherIds(
                        classes.stream().map(ClassroomInfoResponse::id).toList(), date)
                .values()
                .forEach(ids::addAll);
        return ids;
    }

    private Set<Long> bghOf(Set<Long> campusIds, LocalDate date) {
        Set<Long> ids = new LinkedHashSet<>(staffAssignmentService.findPrincipalIds(date));
        campusIds.forEach(c -> ids.addAll(staffAssignmentService.findCampusVicePrincipalIds(c, date)));
        return ids;
    }

    private static void require(Long value, String field, String message, List<FieldErrorResponse> errors) {
        if (value == null) {
            errors.add(new FieldErrorResponse(field, message));
        }
    }
}
