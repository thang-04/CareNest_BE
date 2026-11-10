package com.carenest.service.account.impl;

import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.entity.account.GuardianPhoneChangeRequest;
import com.carenest.entity.account.UserAccount;
import com.carenest.repository.account.GuardianPhoneChangeRequestRepository;
import com.carenest.repository.account.RolePermissionRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserPermissionOverrideRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EffectivePermissionResolver;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.NotificationType;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PhoneChangeStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Dùng chung cho Sửa hồ sơ và màn duyệt đổi SĐT (G94-180/AUTH-21): mở yêu cầu + báo người duyệt, phạm vi người duyệt,
 * chuyển yêu cầu thành response. Chạy trong giao dịch của người gọi.
 */
@Component
@RequiredArgsConstructor
class PhoneChangeSupport {

    private final GuardianPhoneChangeRequestRepository requestRepository;
    private final UserAccountRepository userAccountRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final ChildIntakeService childIntakeService;
    private final StaffAssignmentService staffAssignmentService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final NotificationService notificationService;
    private final Clock clock;

    /**
     * Yêu cầu mới thay yêu cầu đang chờ của cùng tài khoản (Sửa hồ sơ lần sau sang số khác); báo người duyệt ở điểm
     * trường của trẻ, trừ người gửi.
     */
    GuardianPhoneChangeRequest open(
            CurrentUser actor,
            Long childId,
            Long campusId,
            String childName,
            Long guardianId,
            UserAccount account,
            String newPhone,
            String reason) {
        requestRepository
                .findByUserIdAndStatus(account.getId(), PhoneChangeStatus.PENDING)
                .ifPresent(pending -> {
                    pending.cancel();
                    requestRepository.saveAndFlush(pending);
                });
        GuardianPhoneChangeRequest request = requestRepository.save(GuardianPhoneChangeRequest.open(
                account.getId(),
                childId,
                guardianId,
                account.getUsername(),
                newPhone,
                reason,
                actor.userId(),
                clock.instant()));
        Set<Long> approvers = new HashSet<>(approverIds(campusId));
        approvers.remove(actor.userId());
        notificationService.notifyUsers(
                approvers,
                NotificationType.GUARDIAN_PHONE_CHANGE_REQUESTED,
                "Yêu cầu đổi số điện thoại phụ huynh",
                actor.fullName() + " đề nghị đổi số điện thoại đăng nhập của " + account.getFullName()
                        + " (phụ huynh của " + childName + "). Duyệt ở màn Tài khoản phụ huynh.",
                childId);
        return request;
    }

    /**
     * Người duyệt được báo: PHT đang phân công ở điểm trường có quyền quản lý tài khoản phụ huynh (ma trận + ghi đè), và
     * HT (luôn có quyền).
     */
    Set<Long> approverIds(Long campusId) {
        LocalDate today = LocalDate.now(clock);
        Set<Long> ids = new HashSet<>(staffAssignmentService.findPrincipalIds(today));
        if (campusId == null) {
            return ids;
        }
        var matrix = rolePermissionRepository.findByRole(CareNestRole.VICE_PRINCIPAL);
        for (Long userId : staffAssignmentService.findCampusVicePrincipalIds(campusId, today)) {
            Set<Permission> effective = EffectivePermissionResolver.resolve(
                    Set.of(CareNestRole.VICE_PRINCIPAL), matrix, overrideRepository.findByUserId(userId));
            if (effective.contains(Permission.PARENT_ACCOUNT_MANAGE)) {
                ids.add(userId);
            }
        }
        return ids;
    }

    /** Như hồ sơ trẻ: HT toàn trường, PHT điểm trường được phân công. */
    boolean inScope(CurrentUser actor, Long campusId) {
        return staffAccessPolicy.managesWholeSchool(actor)
                || (campusId != null
                        && staffAccessPolicy.managedCampusIds(actor).contains(campusId));
    }

    Map<Long, GuardianChildSummary> childrenOf(Collection<GuardianPhoneChangeRequest> requests) {
        List<Long> childIds = requests.stream()
                .map(GuardianPhoneChangeRequest::getChildId)
                .distinct()
                .toList();
        return childIds.isEmpty()
                ? Map.of()
                : childIntakeService.findChildSummaries(childIds).stream()
                        .collect(Collectors.toMap(GuardianChildSummary::id, Function.identity()));
    }

    List<GuardianPhoneChangeResponse> toResponses(
            CurrentUser viewer, List<GuardianPhoneChangeRequest> requests, Map<Long, GuardianChildSummary> children) {
        Set<Long> userIds = requests.stream()
                .flatMap(r -> Stream.of(r.getUserId(), r.getRequestedBy(), r.getDecidedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = userIds.isEmpty()
                ? Map.of()
                : userAccountRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName));
        boolean canManage = viewer.hasPermission(Permission.PARENT_ACCOUNT_MANAGE);
        return requests.stream()
                .map(r -> {
                    GuardianChildSummary child = children.get(r.getChildId());
                    Long campusId = child == null ? null : child.campusId();
                    boolean canDecide = r.isPending()
                            && canManage
                            && !viewer.userId().equals(r.getRequestedBy())
                            && inScope(viewer, campusId);
                    return new GuardianPhoneChangeResponse(
                            r.getId(),
                            r.getChildId(),
                            child == null ? null : child.code(),
                            child == null ? null : child.fullName(),
                            campusId,
                            r.getGuardianId(),
                            r.getUserId(),
                            names.get(r.getUserId()),
                            r.getOldPhone(),
                            r.getNewPhone(),
                            r.getStatus(),
                            r.getReason(),
                            r.getRequestedBy(),
                            names.get(r.getRequestedBy()),
                            r.getRequestedAt(),
                            r.getDecidedBy(),
                            r.getDecidedBy() == null ? null : names.get(r.getDecidedBy()),
                            r.getDecidedAt(),
                            r.getDecisionNote(),
                            r.getSmsStatus(),
                            canDecide);
                })
                .toList();
    }
}
