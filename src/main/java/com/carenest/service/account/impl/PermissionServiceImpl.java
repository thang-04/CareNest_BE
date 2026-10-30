package com.carenest.service.account.impl;

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
import com.carenest.security.authorization.EffectivePermissionResolver;
import com.carenest.service.account.PermissionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.PermissionEffect;
import com.carenest.utils.enums.PermissionState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final Sort CHANGE_LOG_ORDER = Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("id"));

    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final PermissionChangeLogRepository permissionChangeLogRepository;
    private final UserAccountRepository userAccountRepository;
    private final IdentityProviderClient identityProviderClient;
    private final PermissionMapper permissionMapper;
    private final TransactionTemplate transactionTemplate;

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
            // AUTH-11: quyền ADMIN/PRINCIPAL/PARENT cố định trong code, không sửa qua API
            if (!Permission.isConfigurableRole(role)) {
                throw GlobalException.of(ApiCode.ROLE_PERMISSIONS_FIXED);
            }
            Set<Permission> requested = toEnumSet(request.permissions());
            for (Permission permission : requested) {
                if (!permission.isConfigurableFor(role)) {
                    throw GlobalException.of(ApiCode.PERMISSION_NOT_CONFIGURABLE_FOR_ROLE, permission, role);
                }
            }

            List<RolePermission> currentRows = rolePermissionRepository.findByRole(role);
            Set<Permission> currentlyOn = EffectivePermissionResolver.resolve(Set.of(role), currentRows, List.of());
            UUID batchId = UUID.randomUUID();
            List<PermissionChangeLog> changes = new ArrayList<>();
            for (Permission permission : Permission.configurablePermissionsOf(role)) {
                boolean before = currentlyOn.contains(permission);
                boolean after = requested.contains(permission);
                if (before != after) {
                    changes.add(PermissionChangeLog.forRole(
                            batchId,
                            actor.userId(),
                            role,
                            permission.name(),
                            onOff(before),
                            onOff(after),
                            request.reason()));
                }
            }

            // Dòng không còn được bật (kể cả mã cũ đã bỏ khỏi enum) bị xóa; dòng mới được thêm
            Set<String> requestedCodes =
                    requested.stream().map(Permission::name).collect(Collectors.toSet());
            List<RolePermission> removed = currentRows.stream()
                    .filter(row -> !requestedCodes.contains(row.getPermissionCode()))
                    .toList();
            Set<String> existingCodes =
                    currentRows.stream().map(RolePermission::getPermissionCode).collect(Collectors.toSet());
            List<RolePermission> added = requestedCodes.stream()
                    .filter(code -> !existingCodes.contains(code))
                    .map(code -> RolePermission.create(role, code, actor.userId()))
                    .toList();
            rolePermissionRepository.deleteAll(removed);
            rolePermissionRepository.flush();
            rolePermissionRepository.saveAll(added);
            permissionChangeLogRepository.saveAll(changes);

            RolePermissionResponse response = toRoleResponse(role, rolePermissionRepository.findByRole(role));
            log.info("{}|changed={}|END", logPrefix, changes.size());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
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
            Map<Permission, PermissionEffect> requested = toRequestedOverrides(request.overrides(), roles);

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
            PageResponse<PermissionChangeLogResponse> response =
                    PageResponse.from(logs, permissionMapper::toChangeLogResponse);
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
                Permission.configurablePermissionsOf(role).stream().sorted().toList(),
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

    private Map<Permission, PermissionEffect> toRequestedOverrides(List<OverrideItem> items, Set<CareNestRole> roles) {
        Map<Permission, PermissionEffect> requested = new EnumMap<>(Permission.class);
        for (OverrideItem item : items) {
            if (requested.put(item.permission(), item.effect()) != null) {
                throw GlobalException.of(ApiCode.PERMISSION_DUPLICATED, item.permission());
            }
            if (!EffectivePermissionResolver.isConfigurableForAny(item.permission(), roles)) {
                throw GlobalException.of(ApiCode.PERMISSION_NOT_CONFIGURABLE_FOR_ACCOUNT, item.permission());
            }
        }
        return requested;
    }

    private void replaceOverrides(
            Long actorUserId, Long userId, Map<Permission, PermissionEffect> requested, String reason) {
        Map<String, UserPermissionOverride> current = userPermissionOverrideRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        UserPermissionOverride::getPermissionCode,
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new));
        Map<String, PermissionEffect> requestedByCode = new LinkedHashMap<>();
        requested.forEach((permission, effect) -> requestedByCode.put(permission.name(), effect));

        Set<String> codes = new LinkedHashSet<>(current.keySet());
        codes.addAll(requestedByCode.keySet());
        UUID batchId = UUID.randomUUID();
        List<PermissionChangeLog> changes = new ArrayList<>();
        List<UserPermissionOverride> toDelete = new ArrayList<>();
        List<UserPermissionOverride> toSave = new ArrayList<>();
        for (String code : codes) {
            UserPermissionOverride existing = current.get(code);
            PermissionEffect before = existing == null ? null : existing.getEffect();
            PermissionEffect after = requestedByCode.get(code);
            if (before == after) {
                continue;
            }
            changes.add(PermissionChangeLog.forUser(
                    batchId, actorUserId, userId, code, PermissionState.of(before), PermissionState.of(after), reason));
            if (after == null) {
                toDelete.add(existing);
            } else if (existing == null) {
                toSave.add(UserPermissionOverride.create(userId, code, after, reason, actorUserId));
            } else {
                existing.change(after, reason, actorUserId);
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

    // Keycloak là nguồn role (ADR-0011); bỏ qua role không thuộc CareNest
    private Set<CareNestRole> rolesOf(UserAccount account) {
        try {
            return identityProviderClient.getRealmRoleNames(account.getKeycloakUserId()).stream()
                    .map(CareNestRole::fromKeycloakRole)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(CareNestRole.class)));
        } catch (IdentityProviderException ex) {
            throw GlobalException.withCause(ApiCode.IDENTITY_PROVIDER_UNAVAILABLE, ex);
        }
    }

    private static Set<Permission> toEnumSet(List<Permission> permissions) {
        return permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
    }

    private static PermissionState onOff(boolean on) {
        return on ? PermissionState.ON : PermissionState.OFF;
    }
}
