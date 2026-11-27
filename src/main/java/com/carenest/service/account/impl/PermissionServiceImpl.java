package com.carenest.service.account.impl;

import com.carenest.dto.account.BulkUpdateRolePermissionsRequest;
import com.carenest.dto.account.EduWorkflowIssuesResponse;
import com.carenest.dto.account.PermissionChangeLogResponse;
import com.carenest.dto.account.PermissionOverrideResponse;
import com.carenest.dto.account.PermissionResponse;
import com.carenest.dto.account.RolePermissionResponse;
import com.carenest.dto.account.UpdatePermissionOverridesRequest;
import com.carenest.dto.account.UpdatePermissionOverridesRequest.OverrideItem;
import com.carenest.dto.account.UpdateRolePermissionsRequest;
import com.carenest.dto.account.UserPermissionOverridesResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.entity.account.PermissionChangeLog;
import com.carenest.entity.account.RolePermission;
import com.carenest.entity.account.UserAccount;
import com.carenest.entity.account.UserPermissionOverride;
import com.carenest.exception.GlobalException;
import com.carenest.integration.keycloak.IdentityProviderClient;
import com.carenest.integration.keycloak.IdentityProviderException;
import com.carenest.mapper.account.PermissionMapper;
import com.carenest.repository.account.PermissionChangeLogRepository;
import com.carenest.repository.account.RolePermissionRepository;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.repository.account.UserPermissionOverrideRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.security.authorization.EffectivePermissionResolver;
import com.carenest.security.authorization.TeamLeaderAssignmentChecker;
import com.carenest.service.account.PermissionService;
import com.carenest.service.account.RoleMatrixWorkflowSync;
import com.carenest.service.organization.CampusService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionScope;
import com.carenest.utils.enums.PermissionState;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ma trận quyền theo role, ngoại lệ từng người và lịch sử (ADR-0017, AUTH-10..14). Endpoint gọi vào đây đã chặn
 * bằng PERMISSION_MANAGE (chỉ PRINCIPAL). Đọc role của người đích từ Keycloak nên các method ngoại lệ không đặt
 * @Transactional bao lời gọi HTTP; phần ghi DB chạy trong TransactionTemplate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private static final String LOG_PREFIX = "[PermissionServiceImpl]";
    static final String EDU_MATRIX_MIGRATION_REASON = "EDU-110 chuyển cấu hình";
    private static final Sort CHANGE_LOG_ORDER = Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("id"));

    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final PermissionChangeLogRepository permissionChangeLogRepository;
    private final UserAccountRepository userAccountRepository;
    private final IdentityProviderClient identityProviderClient;
    private final PermissionMapper permissionMapper;
    private final TransactionTemplate transactionTemplate;
    private final TeamLeaderAssignmentChecker teamLeaderAssignmentChecker;
    private final EduPlanAccessPolicy eduPlanAccessPolicy;
    private final RoleMatrixWorkflowSync workflowSync;
    private final Clock clock;
    private final CampusService campusService;

    @Override
    @Transactional(readOnly = true)
    public boolean hasEffectivePermission(Long userId, CareNestRole role, Permission permission) {
        return EffectivePermissionResolver.resolve(
                        Set.of(role),
                        rolePermissionRepository.findByRole(role),
                        userPermissionOverrideRepository.findByUserId(userId))
                .contains(permission);
    }

    @Override
    public List<PermissionResponse> getPermissionCatalog() {
        String logPrefix = LOG_PREFIX + "|getPermissionCatalog";
        log.info("{}|START", logPrefix);
        try {
            List<PermissionResponse> catalog = Arrays.stream(Permission.values())
                    .map(permissionMapper::toPermissionResponse)
                    .toList();
            log.info("{}|END", logPrefix);
            return catalog;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionResponse> getRolePermissions() {
        String logPrefix = LOG_PREFIX + "|getRolePermissions";
        log.info("{}|START", logPrefix);
        try {
            Map<CareNestRole, List<RolePermission>> rowsByRole = new EnumMap<>(CareNestRole.class);
            for (RolePermission row : rolePermissionRepository.findAll()) {
                rowsByRole
                        .computeIfAbsent(row.getRole(), role -> new ArrayList<>())
                        .add(row);
            }
            List<RolePermissionResponse> roles = Arrays.stream(CareNestRole.values())
                    .map(role -> toRoleResponse(role, rowsByRole.getOrDefault(role, List.of())))
                    .toList();
            log.info("{}|END", logPrefix);
            return roles;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RolePermissionResponse getRolePermissions(CareNestRole role) {
        String logPrefix = LOG_PREFIX + "|getRolePermissions|role=" + role;
        log.info("{}|START", logPrefix);
        try {
            RolePermissionResponse response = toRoleResponse(role, rolePermissionRepository.findByRole(role));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public RolePermissionResponse updateRolePermissions(
            CurrentUser actor, CareNestRole role, UpdateRolePermissionsRequest request) {
        String logPrefix = LOG_PREFIX + "|updateRolePermissions|actorUserId=" + actor.userId() + "|role=" + role;
        log.info("{}|START", logPrefix);
        try {
            Map<CareNestRole, Set<Permission>> requested = new EnumMap<>(CareNestRole.class);
            requested.put(role, toEnumSet(request.permissions()));
            int changed = saveMatrix(actor, requested, request.reason());
            RolePermissionResponse response = toRoleResponse(role, rolePermissionRepository.findByRole(role));
            log.info("{}|changed={}|END", logPrefix, changed);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public List<RolePermissionResponse> updateRolePermissionsBulk(
            CurrentUser actor, BulkUpdateRolePermissionsRequest request) {
        String logPrefix = LOG_PREFIX + "|updateRolePermissionsBulk|actorUserId=" + actor.userId() + "|roles="
                + request.roles().size();
        log.info("{}|START", logPrefix);
        try {
            Map<CareNestRole, Set<Permission>> requested = new EnumMap<>(CareNestRole.class);
            for (BulkUpdateRolePermissionsRequest.RoleItem item : request.roles()) {
                // BR2.4: một vai trò chỉ xuất hiện một lần
                if (requested.put(item.role(), toEnumSet(item.permissions())) != null) {
                    throw GlobalException.of(ApiCode.INVALID_REQUEST_DATA);
                }
            }
            int changed = saveMatrix(actor, requested, request.reason());
            List<RolePermissionResponse> response = getRolePermissions();
            log.info("{}|changed={}|END", logPrefix, changed);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EduWorkflowIssuesResponse getEduWorkflowIssues() {
        String logPrefix = LOG_PREFIX + "|getEduWorkflowIssues";
        log.info("{}|START", logPrefix);
        try {
            EduWorkflowIssuesResponse response = workflowSync.validate(currentMatrix());
            log.info("{}|issues={}|END", logPrefix, response.issues().size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public String migrateEduRoleMatrix(java.time.Instant now) {
        String logPrefix = LOG_PREFIX + "|migrateEduRoleMatrix";
        log.info("{}|START", logPrefix);
        try {
            // Dấu đã chuyển = lịch sử với đúng lý do (chỉ có khi giao dịch chuyển thành công)
            if (permissionChangeLogRepository.existsByReason(EDU_MATRIX_MIGRATION_REASON)) {
                log.info("{}|status=ALREADY_MIGRATED|END", logPrefix);
                return "ALREADY_MIGRATED";
            }
            Optional<Long> principalId = workflowSync.activePrincipalUserId(now);
            if (principalId.isEmpty()) {
                log.warn("{}|status=SKIPPED_NO_PRINCIPAL|END", logPrefix);
                return "SKIPPED_NO_PRINCIPAL";
            }
            workflowSync.lockWorkflowSettings();
            Map<CareNestRole, Set<Permission>> before = currentMatrix();
            Map<CareNestRole, Set<Permission>> ticks = workflowSync.ticksFromCurrentPolicy();
            Map<CareNestRole, Set<Permission>> after = new EnumMap<>(CareNestRole.class);
            before.forEach((role, on) -> {
                EnumSet<Permission> next = EnumSet.noneOf(Permission.class);
                on.stream().filter(p -> !Permission.EDU_WORKFLOW.contains(p)).forEach(next::add);
                next.addAll(ticks.getOrDefault(role, Set.of()));
                after.put(role, next);
            });
            // BR4.2: loại vi phạm giữ ô tick cũ (policy đang chạy hợp lệ theo RolePolicy nên hiếm khi xảy ra)
            EduWorkflowIssuesResponse issues = workflowSync.validate(after);
            for (EduWorkflowIssuesResponse.Issue issue : issues.issues()) {
                EduPlanAccessPolicy.DocType type = EduPlanAccessPolicy.DocType.valueOf(issue.docType());
                after.forEach((role, on) -> {
                    for (Permission p : List.of(type.edit(), type.approve())) {
                        if (before.get(role).contains(p)) on.add(p);
                        else on.remove(p);
                    }
                });
            }
            UUID batchId = UUID.randomUUID();
            int changed = 0;
            for (Map.Entry<CareNestRole, Set<Permission>> entry : after.entrySet()) {
                if (!entry.getValue().equals(before.get(entry.getKey()))) {
                    changed += writeRoleRows(
                            principalId.get(), entry.getKey(), entry.getValue(), EDU_MATRIX_MIGRATION_REASON, batchId);
                }
            }
            int policiesChanged = workflowSync.apply(after, principalId.get(), now);
            String summary = "migrated=" + changed + "|policies=" + policiesChanged + "|kept="
                    + issues.issues().stream()
                            .map(EduWorkflowIssuesResponse.Issue::docType)
                            .distinct()
                            .count()
                    + "|issues="
                    + issues.issues().stream()
                            .map(i -> i.docType() + ":" + i.rule())
                            .collect(Collectors.joining(","));
            log.info("{}|{}|END", logPrefix, summary);
            return summary;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /**
     * WF1 (EDU-110): kiểm mã cấu hình được, đổi mã EDU ⇒ chỉ HT hiệu lực (7201) + khóa cài đặt + luật BR1.x trên ma
     * trận sau khi áp (7202 kèm issues); ghi ô tick + lịch sử từng vai trò rồi ghi chuỗi suy ra. Chạy trong giao dịch
     * của người gọi: lỗi bất kỳ ⇒ hoàn tác toàn bộ (BR2.3). Trả số ô đổi.
     */
    private int saveMatrix(CurrentUser actor, Map<CareNestRole, Set<Permission>> requested, String reason) {
        for (Map.Entry<CareNestRole, Set<Permission>> entry : requested.entrySet()) {
            // AUTH-11: quyền ADMIN/PARENT cố định trong code; PRINCIPAL chỉ có ô 8 mã EDU (BR2.1)
            if (!Permission.isConfigurableRole(entry.getKey())) {
                throw GlobalException.of(ApiCode.ROLE_PERMISSIONS_FIXED);
            }
            for (Permission permission : entry.getValue()) {
                if (!permission.isConfigurableFor(entry.getKey()) || !permission.inRoleMatrix()) {
                    throw GlobalException.of(ApiCode.PERMISSION_NOT_CONFIGURABLE_FOR_ROLE, permission, entry.getKey());
                }
            }
        }
        Map<CareNestRole, Set<Permission>> before = currentMatrix();
        boolean eduChanged = requested.entrySet().stream()
                .anyMatch(e -> !eduPart(e.getValue()).equals(eduPart(before.getOrDefault(e.getKey(), Set.of()))));
        Map<CareNestRole, Set<Permission>> after = new EnumMap<>(before);
        after.putAll(requested);
        if (eduChanged) {
            if (!eduPlanAccessPolicy.canConfigureWorkflow(actor)) {
                throw GlobalException.of(ApiCode.EDU_CONFIG_FORBIDDEN);
            }
            workflowSync.lockWorkflowSettings();
            EduWorkflowIssuesResponse issues = workflowSync.validate(after);
            if (!issues.issues().isEmpty()) {
                throw GlobalException.withData(ApiCode.SIGNERS_INVALID, issues);
            }
        }
        UUID batchId = UUID.randomUUID();
        int changed = 0;
        for (Map.Entry<CareNestRole, Set<Permission>> entry : requested.entrySet()) {
            changed += writeRoleRows(actor.userId(), entry.getKey(), entry.getValue(), reason, batchId);
        }
        if (eduChanged) {
            workflowSync.apply(after, actor.userId(), clock.instant());
        }
        return changed;
    }

    private int writeRoleRows(
            Long actorUserId, CareNestRole role, Set<Permission> requested, String reason, UUID batchId) {
        List<RolePermission> currentRows = rolePermissionRepository.findByRole(role);
        Set<Permission> currentlyOn = EffectivePermissionResolver.resolve(Set.of(role), currentRows, List.of());
        List<PermissionChangeLog> changes = new ArrayList<>();
        for (Permission permission : Permission.roleMatrixPermissionsOf(role)) {
            boolean before = currentlyOn.contains(permission);
            boolean after = requested.contains(permission);
            if (before != after) {
                changes.add(PermissionChangeLog.forRole(
                        batchId, actorUserId, role, permission.name(), onOff(before), onOff(after), reason));
            }
        }
        // Dòng không còn được bật (kể cả mã cũ đã bỏ khỏi enum) bị xóa; dòng mới được thêm
        Set<String> requestedCodes = requested.stream().map(Permission::name).collect(Collectors.toSet());
        List<RolePermission> removed = currentRows.stream()
                .filter(row -> !requestedCodes.contains(row.getPermissionCode()))
                .toList();
        Set<String> existingCodes =
                currentRows.stream().map(RolePermission::getPermissionCode).collect(Collectors.toSet());
        List<RolePermission> added = requestedCodes.stream()
                .filter(code -> !existingCodes.contains(code))
                .map(code -> RolePermission.create(role, code, actorUserId))
                .toList();
        rolePermissionRepository.deleteAll(removed);
        rolePermissionRepository.flush();
        rolePermissionRepository.saveAll(added);
        permissionChangeLogRepository.saveAll(changes);
        return changes.size();
    }

    /** Ô tick hiện tại của mọi vai trò cấu hình được (chỉ phần ma trận, không gồm quyền cố định). */
    private Map<CareNestRole, Set<Permission>> currentMatrix() {
        Map<CareNestRole, Set<Permission>> matrix = new EnumMap<>(CareNestRole.class);
        List<RolePermission> rows = rolePermissionRepository.findByRoleIn(Permission.CONFIGURABLE_ROLES);
        for (CareNestRole role : Permission.CONFIGURABLE_ROLES) {
            Set<Permission> on = EnumSet.noneOf(Permission.class);
            for (RolePermission row : rows) {
                if (row.getRole() == role) {
                    Permission.fromCode(row.getPermissionCode())
                            .filter(p -> p.isConfigurableFor(role))
                            .ifPresent(on::add);
                }
            }
            matrix.put(role, on);
        }
        return matrix;
    }

    private static Set<Permission> eduPart(Set<Permission> permissions) {
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        permissions.stream().filter(Permission.EDU_WORKFLOW::contains).forEach(result::add);
        return result;
    }

    @Override
    public UserPermissionOverridesResponse getPermissionOverrides(Long userId) {
        String logPrefix = LOG_PREFIX + "|getPermissionOverrides|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            UserAccount account = findAccount(userId);
            Set<CareNestRole> roles = rolesOf(account);
            UserPermissionOverridesResponse response = toOverridesResponse(userId, roles);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public UserPermissionOverridesResponse updatePermissionOverrides(
            CurrentUser actor, Long userId, UpdatePermissionOverridesRequest request) {
        String logPrefix =
                LOG_PREFIX + "|updatePermissionOverrides|actorUserId=" + actor.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-12: không ai tự cấp/thu quyền của chính mình
            if (userId.equals(actor.userId())) {
                throw GlobalException.of(ApiCode.CANNOT_CHANGE_OWN_PERMISSIONS);
            }
            UserAccount account = findAccount(userId);
            Set<CareNestRole> roles = rolesOf(account);
            // AUTH-11: người có quyền cố định (PRINCIPAL/ADMIN) hoặc không có role cấu hình được thì không có ngoại lệ
            boolean hasFixedPrivilegedRole =
                    roles.contains(CareNestRole.PRINCIPAL) || roles.contains(CareNestRole.ADMIN);
            if (hasFixedPrivilegedRole || roles.stream().noneMatch(Permission::isConfigurableRole)) {
                throw GlobalException.of(ApiCode.PERMISSIONS_NOT_OVERRIDABLE);
            }
            Map<Permission, Requested> requested = toRequestedOverrides(request.overrides(), roles);

            transactionTemplate.executeWithoutResult(
                    status -> replaceOverrides(actor.userId(), userId, requested, request.reason()));

            UserPermissionOverridesResponse response = toOverridesResponse(userId, roles);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void clearPermissionOverrides(Long actorUserId, Long userId, String reason) {
        String logPrefix = LOG_PREFIX + "|clearPermissionOverrides|actorUserId=" + actorUserId + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            replaceOverrides(actorUserId, userId, Map.of(), reason);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PermissionChangeLogResponse> getPermissionChangeLogs(
            CareNestRole targetRole, Long targetUserId, int page, int size) {
        String logPrefix =
                LOG_PREFIX + "|getPermissionChangeLogs|targetRole=" + targetRole + "|targetUserId=" + targetUserId;
        log.info("{}|START", logPrefix);
        try {
            if (targetRole != null && targetUserId != null) {
                throw GlobalException.of(ApiCode.PERMISSION_LOG_FILTER_INVALID);
            }
            PageRequest pageable = PageRequest.of(page, size, CHANGE_LOG_ORDER);
            Page<PermissionChangeLog> logs;
            if (targetRole != null) {
                logs = permissionChangeLogRepository.findByTargetRole(targetRole, pageable);
            } else if (targetUserId != null) {
                logs = permissionChangeLogRepository.findByTargetUserId(targetUserId, pageable);
            } else {
                logs = permissionChangeLogRepository.findAll(pageable);
            }
            Set<Long> userIds = new HashSet<>();
            logs.forEach(row -> {
                userIds.add(row.getActorUserId());
                if (row.getTargetUserId() != null) {
                    userIds.add(row.getTargetUserId());
                }
            });
            Map<Long, String> names = new HashMap<>();
            userAccountRepository.findAllById(userIds).forEach(user -> names.put(user.getId(), user.getFullName()));
            PageResponse<PermissionChangeLogResponse> response =
                    PageResponse.from(logs, row -> permissionMapper.toChangeLogResponse(row, names::get));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private RolePermissionResponse toRoleResponse(CareNestRole role, List<RolePermission> rows) {
        return new RolePermissionResponse(
                role,
                Permission.isConfigurableRole(role),
                Permission.roleMatrixPermissionsOf(role).stream().sorted().toList(),
                EffectivePermissionResolver.resolve(Set.of(role), rows, List.of()).stream()
                        .sorted()
                        .toList());
    }

    private UserPermissionOverridesResponse toOverridesResponse(Long userId, Set<CareNestRole> roles) {
        Set<CareNestRole> configurableRoles =
                roles.stream().filter(Permission::isConfigurableRole).collect(Collectors.toSet());
        List<RolePermission> matrixRows =
                configurableRoles.isEmpty() ? List.of() : rolePermissionRepository.findByRoleIn(configurableRoles);
        List<UserPermissionOverride> overrides = userPermissionOverrideRepository.findByUserId(userId);

        EnumSet<Permission> configurable = EnumSet.noneOf(Permission.class);
        configurableRoles.forEach(role -> configurable.addAll(Permission.configurablePermissionsOf(role)));
        List<PermissionOverrideResponse> overrideResponses = new ArrayList<>();
        for (UserPermissionOverride override : overrides) {
            Permission.fromCode(override.getPermissionCode())
                    .filter(configurable::contains)
                    .ifPresent(permission ->
                            overrideResponses.add(permissionMapper.toOverrideResponse(permission, override)));
        }
        overrideResponses.sort((left, right) -> left.permission().compareTo(right.permission()));

        return new UserPermissionOverridesResponse(
                userId,
                roles.stream().sorted().toList(),
                configurable.stream().sorted().toList(),
                overrideResponses,
                EffectivePermissionResolver.resolve(roles, matrixRows, overrides).stream()
                        .sorted()
                        .toList());
    }

    private Map<Permission, Requested> toRequestedOverrides(List<OverrideItem> items, Set<CareNestRole> roles) {
        Map<Permission, Requested> requested = new EnumMap<>(Permission.class);
        for (OverrideItem item : items) {
            // EDU-110 (BR5.1): chuỗi duyệt suy từ ma trận nên 8 mã soạn/duyệt không có ngoại lệ theo người
            if (!item.permission().overridable()) {
                throw GlobalException.of(ApiCode.PERMISSIONS_NOT_OVERRIDABLE);
            }
            if (item.permission() == Permission.CHILD_PROFILE_EDIT
                    && !roles.contains(CareNestRole.VICE_PRINCIPAL)
                    && (item.scope() == PermissionScope.SCHOOL || item.campusId() != null)) {
                throw GlobalException.of(ApiCode.PERMISSION_SCOPE_NOT_ALLOWED);
            }
            PermissionScope scope = scopeOf(item);
            if (requested.put(item.permission(), new Requested(item.effect(), scope, campusOf(item, scope))) != null) {
                throw GlobalException.of(ApiCode.PERMISSION_DUPLICATED, item.permission());
            }
            if (!EffectivePermissionResolver.isConfigurableForAny(item.permission(), roles)) {
                throw GlobalException.of(ApiCode.PERMISSION_NOT_CONFIGURABLE_FOR_ACCOUNT, item.permission());
            }
        }
        return requested;
    }

    // AUTH-20: phạm vi chỉ đi với GRANT; cả trường chỉ cho mã sửa theo mảng; bỏ trống ⇒ điểm trường phân công
    private static PermissionScope scopeOf(OverrideItem item) {
        if (item.scope() == null) {
            return UserPermissionOverride.defaultScope(item.effect());
        }
        boolean valid = item.effect() == PermissionEffect.GRANT
                && (item.scope() == PermissionScope.CAMPUS || item.permission().campusScoped());
        if (!valid) {
            throw GlobalException.of(ApiCode.PERMISSION_SCOPE_NOT_ALLOWED);
        }
        return item.scope();
    }

    // AUTH-20 (chốt 21/11/2026): GRANT mã sửa theo mảng giao được ở một điểm trường cụ thể, kể cả khác điểm trường PHT
    // phụ trách; điểm trường phải còn hoạt động
    private Long campusOf(OverrideItem item, PermissionScope scope) {
        if (item.campusId() == null) {
            return null;
        }
        if (scope != PermissionScope.CAMPUS || !item.permission().campusScoped()) {
            throw GlobalException.of(ApiCode.PERMISSION_SCOPE_NOT_ALLOWED);
        }
        if (campusService.findCampusStatus(item.campusId()).orElse(null) != CampusStatus.ACTIVE) {
            throw GlobalException.of(ApiCode.PERMISSION_CAMPUS_INVALID);
        }
        return item.campusId();
    }

    private void replaceOverrides(Long actorUserId, Long userId, Map<Permission, Requested> requested, String reason) {
        Map<String, UserPermissionOverride> current = userPermissionOverrideRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        UserPermissionOverride::getPermissionCode,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        Map<String, Requested> requestedByCode = new LinkedHashMap<>();
        requested.forEach((permission, item) -> requestedByCode.put(permission.name(), item));

        Set<String> codes = new LinkedHashSet<>(current.keySet());
        codes.addAll(requestedByCode.keySet());
        UUID batchId = UUID.randomUUID();
        List<PermissionChangeLog> changes = new ArrayList<>();
        List<UserPermissionOverride> toDelete = new ArrayList<>();
        List<UserPermissionOverride> toSave = new ArrayList<>();
        for (String code : codes) {
            UserPermissionOverride existing = current.get(code);
            PermissionEffect before = existing == null ? null : existing.getEffect();
            PermissionScope beforeScope = existing == null ? null : existing.getScope();
            Requested wanted = requestedByCode.get(code);
            PermissionEffect after = wanted == null ? null : wanted.effect();
            PermissionScope afterScope = wanted == null ? null : wanted.scope();
            Long beforeCampusId = existing == null ? null : existing.getCampusId();
            Long afterCampusId = wanted == null ? null : wanted.campusId();
            if (before == after && beforeScope == afterScope && Objects.equals(beforeCampusId, afterCampusId)) {
                continue;
            }
            changes.add(PermissionChangeLog.forUser(
                            batchId,
                            actorUserId,
                            userId,
                            code,
                            PermissionState.of(before),
                            PermissionState.of(after),
                            reason)
                    .withScopes(beforeScope, afterScope)
                    .withCampuses(beforeCampusId, afterCampusId));
            if (after == null) {
                toDelete.add(existing);
            } else if (existing == null) {
                toSave.add(UserPermissionOverride.create(
                        userId, code, after, afterScope, afterCampusId, reason, actorUserId));
            } else {
                existing.change(after, afterScope, afterCampusId, reason, actorUserId);
                toSave.add(existing);
            }
        }
        userPermissionOverrideRepository.deleteAll(toDelete);
        userPermissionOverrideRepository.flush();
        userPermissionOverrideRepository.saveAll(toSave);
        permissionChangeLogRepository.saveAll(changes);
    }

    private UserAccount findAccount(Long userId) {
        return userAccountRepository.findById(userId).orElseThrow(() -> GlobalException.of(ApiCode.ACCOUNT_NOT_FOUND));
    }

    // Keycloak là nguồn role (ADR-0011); bỏ qua role không thuộc CareNest. Cộng TEAM_LEADER khi đang là tổ trưởng
    // để ngoại lệ từng người áp được cho quyền của cột này (AUTH-23)
    private Set<CareNestRole> rolesOf(UserAccount account) {
        Set<CareNestRole> loginRoles;
        try {
            loginRoles = identityProviderClient.getRealmRoleNames(account.getKeycloakUserId()).stream()
                    .map(CareNestRole::fromKeycloakRole)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(CareNestRole.class)));
        } catch (IdentityProviderException ex) {
            throw GlobalException.withCause(ApiCode.IDENTITY_PROVIDER_UNAVAILABLE, ex);
        }
        return EffectivePermissionResolver.permissionRoles(
                loginRoles, teamLeaderAssignmentChecker.isActiveTeamLeader(account.getId(), loginRoles));
    }

    private static Set<Permission> toEnumSet(List<Permission> permissions) {
        return permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
    }

    /** Ngoại lệ được yêu cầu cho một mã: GRANT/REVOKE, phạm vi và điểm trường giao cụ thể (chỉ GRANT có). */
    private record Requested(PermissionEffect effect, PermissionScope scope, Long campusId) {}

    private static PermissionState onOff(boolean on) {
        return on ? PermissionState.ON : PermissionState.OFF;
    }
}
