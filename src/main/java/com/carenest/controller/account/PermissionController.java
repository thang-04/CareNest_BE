package com.carenest.controller.account;

import com.carenest.dto.account.PermissionChangeLogResponse;
import com.carenest.dto.account.PermissionResponse;
import com.carenest.dto.account.RolePermissionResponse;
import com.carenest.dto.account.UpdatePermissionOverridesRequest;
import com.carenest.dto.account.UpdateRolePermissionsRequest;
import com.carenest.dto.account.UserPermissionOverridesResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.PermissionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.ResponseJson;
import com.carenest.utils.enums.CareNestRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Toàn bộ chỉ PRINCIPAL (PERMISSION_MANAGE không giao được — AUTH-11)
@RestController
@RequiredArgsConstructor
@Slf4j
public class PermissionController {
    private static final String LOG_PREFIX = "[PermissionController]";

    private final PermissionService permissionService;

    @GetMapping("/permissions")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<List<PermissionResponse>>> getPermissionCatalog(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getPermissionCatalog|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<PermissionResponse> catalog = permissionService.getPermissionCatalog();
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get permission catalog success", catalog);
    }

    @GetMapping("/role-permissions")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<List<RolePermissionResponse>>> getRolePermissions(CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getRolePermissions|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        List<RolePermissionResponse> roles = permissionService.getRolePermissions();
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get role permissions success", roles);
    }

    @GetMapping("/role-permissions/{role}")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<RolePermissionResponse>> getRolePermission(
            @PathVariable CareNestRole role, CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getRolePermission|userId=" + currentUser.userId() + "|role=" + role;
        log.info("{}|START", logPrefix);
        RolePermissionResponse response = permissionService.getRolePermissions(role);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get role permissions success", response);
    }

    @PutMapping("/role-permissions/{role}")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<RolePermissionResponse>> updateRolePermissions(
            @PathVariable CareNestRole role,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|updateRolePermissions|userId=" + currentUser.userId() + "|role=" + role;
        log.info("{}|START", logPrefix);
        RolePermissionResponse response = permissionService.updateRolePermissions(currentUser, role, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update role permissions success", response);
    }

    @GetMapping("/accounts/{userId}/permission-overrides")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<UserPermissionOverridesResponse>> getPermissionOverrides(
            @PathVariable Long userId, CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|getPermissionOverrides|actorUserId=" + currentUser.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        UserPermissionOverridesResponse response = permissionService.getPermissionOverrides(userId);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get permission overrides success", response);
    }

    @PutMapping("/accounts/{userId}/permission-overrides")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<UserPermissionOverridesResponse>> updatePermissionOverrides(
            @PathVariable Long userId,
            @Valid @RequestBody UpdatePermissionOverridesRequest request,
            CurrentUser currentUser) {
        String logPrefix =
                LOG_PREFIX + "|updatePermissionOverrides|actorUserId=" + currentUser.userId() + "|userId=" + userId;
        log.info("{}|START", logPrefix);
        UserPermissionOverridesResponse response =
                permissionService.updatePermissionOverrides(currentUser, userId, request);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Update permission overrides success", response);
    }

    @GetMapping("/permission-change-logs")
    @PreAuthorize("@permissionGuard.has('PERMISSION_MANAGE')")
    public ResponseEntity<ResponseJson<PageResponse<PermissionChangeLogResponse>>> getPermissionChangeLogs(
            @RequestParam(required = false) CareNestRole targetRole,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            CurrentUser currentUser) {
        String logPrefix = LOG_PREFIX + "|getPermissionChangeLogs|userId=" + currentUser.userId() + "|page=" + page;
        log.info("{}|START", logPrefix);
        PageResponse<PermissionChangeLogResponse> logs =
                permissionService.getPermissionChangeLogs(targetRole, targetUserId, page, size);
        log.info("{}|END", logPrefix);
        return ResponseJson.toJsonWithData(ApiCode.SUCCESSFUL, "Get permission change logs success", logs);
    }
}
