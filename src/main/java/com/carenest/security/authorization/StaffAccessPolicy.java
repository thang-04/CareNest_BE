package com.carenest.security.authorization;

import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phạm vi quản lý nhân sự (AUTH-06, AUTH-15..17). Quyền hành động (ACCOUNT_MANAGE...) đã kiểm ở controller; ở đây
 * kiểm người thao tác được đụng tới role/campus nào:
 * Admin → tài khoản PRINCIPAL (và PARENT tới khi có luồng phụ huynh); HT (có phân công PRINCIPAL) → PHT/GV/bếp toàn
 * trường; PHT → GV/bếp trong campus mình đang phụ trách.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaffAccessPolicy {

    private static final Set<CareNestRole> PRINCIPAL_MANAGED = Collections.unmodifiableSet(
            EnumSet.of(CareNestRole.VICE_PRINCIPAL, CareNestRole.TEACHER, CareNestRole.KITCHEN_STAFF));
    private static final Set<CareNestRole> VICE_PRINCIPAL_MANAGED =
            Collections.unmodifiableSet(EnumSet.of(CareNestRole.TEACHER, CareNestRole.KITCHEN_STAFF));
    private static final Set<CareNestRole> ADMIN_CREATABLE =
            Collections.unmodifiableSet(EnumSet.of(CareNestRole.PRINCIPAL));

    private final StaffAssignmentService staffAssignmentService;
    private final EnrollmentService enrollmentService;
    private final ClassroomService classroomService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final Clock clock;

    /** HT có phân công PRINCIPAL đang hiệu lực (AUTH-01). */
    public boolean managesWholeSchool(CurrentUser actor) {
        return actor.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(actor.userId(), today());
    }

    /** Campus PHT đang phụ trách (AUTH-02); không phải PHT ⇒ rỗng. */
    public Set<Long> managedCampusIds(CurrentUser actor) {
        if (!actor.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            return Set.of();
        }
        return staffAssignmentService.findActiveCampusIds(actor.userId(), StaffRole.VICE_PRINCIPAL, today());
    }

    /** Xem được mọi campus/lớp: Admin (kỹ thuật) hoặc HT. */
    public boolean seesAllCampuses(CurrentUser actor) {
        return actor.hasRole(CareNestRole.ADMIN) || managesWholeSchool(actor);
    }

    public void checkCanViewCampus(CurrentUser actor, Long campusId) {
        if (!seesAllCampuses(actor) && !managedCampusIds(actor).contains(campusId)) {
            deny("checkCanViewCampus", actor, ApiCode.CAMPUS_ACCESS_DENIED);
        }
    }

    /** campusId = campus của phân công kèm theo (null nếu không kèm phân công). */
    public void checkCanCreate(CurrentUser actor, CareNestRole role, Long campusId) {
        boolean allowed = (actor.hasRole(CareNestRole.ADMIN) && ADMIN_CREATABLE.contains(role))
                || (PRINCIPAL_MANAGED.contains(role) && managesWholeSchool(actor))
                // AUTH-15: PHT chỉ tạo người được phân công ngay vào campus mình
                || (VICE_PRINCIPAL_MANAGED.contains(role)
                        && campusId != null
                        && managedCampusIds(actor).contains(campusId));
        if (!allowed) {
            deny("checkCanCreate", actor, ApiCode.ROLE_CREATE_DENIED);
        }
    }

    /** Khóa/mở, cấp lại mật khẩu tạm, xem chi tiết. targetRole null = chưa biết role ⇒ chỉ Admin. */
    public void checkCanManageAccount(CurrentUser actor, Long targetUserId, CareNestRole targetRole) {
        if (!canManageAccount(actor, targetUserId, targetRole)) {
            deny("checkCanManageAccount", actor, ApiCode.ACCOUNT_MANAGE_DENIED);
        }
    }

    public boolean canManageAccount(CurrentUser actor, Long targetUserId, CareNestRole targetRole) {
        if (actor.hasRole(CareNestRole.ADMIN)) {
            return true;
        }
        if (targetRole == null) {
            return false;
        }
        if (PRINCIPAL_MANAGED.contains(targetRole) && managesWholeSchool(actor)) {
            return true;
        }
        return VICE_PRINCIPAL_MANAGED.contains(targetRole)
                && !Collections.disjoint(managedCampusIds(actor), campusesOf(targetUserId, targetRole));
    }

    /** Gán/kết thúc phân công role đó tại campus đó (AUTH-16). */
    public void checkCanAssign(CurrentUser actor, StaffRole staffRole, Long campusId) {
        CareNestRole role = CareNestRole.valueOf(staffRole.name());
        boolean allowed = (PRINCIPAL_MANAGED.contains(role) && managesWholeSchool(actor))
                || (VICE_PRINCIPAL_MANAGED.contains(role)
                        && campusId != null
                        && managedCampusIds(actor).contains(campusId));
        if (!allowed) {
            deny("checkCanAssign", actor, ApiCode.ASSIGNMENT_MANAGE_DENIED);
        }
    }

    /** AUTH-18: HT mọi trẻ; PHT chỉ trẻ đang học trong campus mình (trẻ chưa có lớp chỉ HT). */
    public void checkCanManageChildGuardians(CurrentUser actor, Long childId) {
        if (!managesWholeSchool(actor)
                && Collections.disjoint(managedCampusIds(actor), currentCampusesOfChildren(Set.of(childId)))) {
            deny("checkCanManageChildGuardians", actor, ApiCode.CHILD_GUARDIANS_MANAGE_DENIED);
        }
    }

    /** AUTH-19: quản lý phụ huynh khi quản lý campus của một trẻ đang liên kết với họ. */
    public void checkCanManageParent(CurrentUser actor, Long parentUserId) {
        if (managesWholeSchool(actor)) {
            return;
        }
        Set<Long> childCampuses =
                currentCampusesOfChildren(guardianChildLinkService.findChildIdsOfGuardian(parentUserId));
        if (Collections.disjoint(managedCampusIds(actor), childCampuses)) {
            deny("checkCanManageParent", actor, ApiCode.ACCOUNT_MANAGE_DENIED);
        }
    }

    private Set<Long> currentCampusesOfChildren(Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return Set.of();
        }
        Collection<Long> classrooms = enrollmentService
                .findActiveClassroomIdByChildIds(childIds, today())
                .values();
        return new HashSet<>(classroomService.findCampusIds(classrooms).values());
    }

    private Set<Long> campusesOf(Long userId, CareNestRole role) {
        Set<Long> campuses = new HashSet<>();
        if (role == CareNestRole.TEACHER || role == CareNestRole.KITCHEN_STAFF) {
            campuses.addAll(
                    staffAssignmentService.findActiveCampusIds(userId, StaffRole.valueOf(role.name()), today()));
        }
        return campuses;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static void deny(String check, CurrentUser actor, ApiCode code) {
        log.warn("[{}]|userId={}|denied", check, actor.userId());
        throw GlobalException.of(code);
    }
}
