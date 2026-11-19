package com.carenest.service.account.impl;

import com.carenest.dto.account.GuardianPhoneChangeResponse;
import com.carenest.dto.child.GuardianChildSummary;
import com.carenest.dto.child.GuardianPhoneTarget;
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
import com.carenest.service.child.ChildRecordService;
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
 * Dùng chung cho gửi và duyệt yêu cầu đổi SĐT (G94-180/AUTH-21): mở yêu cầu + báo người duyệt, phạm vi người duyệt,
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
    private final ChildRecordService childRecordService;
    private final StaffAssignmentService staffAssignmentService;
    private final StaffAccessPolicy staffAccessPolicy;
    private final NotificationService notificationService;
    private final Clock clock;

    /**
     * Yêu cầu mới thay yêu cầu đang chờ của cùng phụ huynh (và cùng tài khoản); báo người duyệt ở điểm trường của trẻ,
     * trừ người gửi. {@code account} null ⇒ phụ huynh chưa có tài khoản, số cũ là SĐT trên hồ sơ.
     */
    GuardianPhoneChangeRequest open(
            CurrentUser actor, GuardianPhoneTarget target, UserAccount account, String newPhone, String reason) {
        requestRepository
                .findByGuardianIdAndStatus(target.guardianId(), PhoneChangeStatus.PENDING)
                .ifPresent(this::cancel);
        if (account != null) {
            requestRepository
                    .findByUserIdAndStatus(account.getId(), PhoneChangeStatus.PENDING)
                    .ifPresent(this::cancel);
        }
        GuardianPhoneChangeRequest request = requestRepository.save(GuardianPhoneChangeRequest.open(
                account == null ? null : account.getId(),
                target.childId(),
                target.guardianId(),
                account == null ? target.phone() : account.getUsername(),
                newPhone,
                reason,
                actor.userId(),
                clock.instant()));
        Set<Long> approvers = new HashSet<>(approverIds(target.campusId()));
        approvers.remove(actor.userId());
        String whose = account == null
                ? "số điện thoại của " + target.fullName()
                : "số điện thoại đăng nhập của " + account.getFullName();
        notificationService.notifyUsers(
                approvers,
                NotificationType.GUARDIAN_PHONE_CHANGE_REQUESTED,
                "Yêu cầu đổi số điện thoại phụ huynh",
                actor.fullName() + " đề nghị đổi " + whose + " (phụ huynh của " + target.childName()
                        + "). Duyệt ở màn Tài khoản phụ huynh.",
                target.childId());
        return request;
    }

    private void cancel(GuardianPhoneChangeRequest pending) {
        pending.cancel();
        requestRepository.saveAndFlush(pending);
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
        // Phụ huynh chưa có tài khoản: họ tên trên hồ sơ (dòng đã bỏ khỏi hồ sơ ⇒ null)
        Map<Long, String> guardianNames = childRecordService.findGuardianNames(requests.stream()
                .filter(r -> r.getUserId() == null && r.getGuardianId() != null)
                .map(GuardianPhoneChangeRequest::getGuardianId)
                .collect(Collectors.toSet()));
        boolean canManage = viewer.hasPermission(Permission.PARENT_ACCOUNT_MANAGE);
        return requests.stream()
                .map(r -> {
                    GuardianChildSummary child = children.get(r.getChildId());
                    Long campusId = child == null ? null : child.campusId();
                    // AUTH-21 (sửa 2026-11-11): người gửi có quyền duyệt thì tự duyệt được
                    boolean canDecide = r.isPending() && canManage && inScope(viewer, campusId);
                    return new GuardianPhoneChangeResponse(
                            r.getId(),
                            r.getChildId(),
                            child == null ? null : child.code(),
                            child == null ? null : child.fullName(),
                            campusId,
                            r.getGuardianId(),
                            r.getUserId(),
                            r.getUserId() == null ? guardianNames.get(r.getGuardianId()) : names.get(r.getUserId()),
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
