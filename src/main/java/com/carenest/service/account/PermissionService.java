package com.carenest.service.account;

import com.carenest.dto.account.PermissionChangeLogResponse;
import com.carenest.dto.account.PermissionResponse;
import com.carenest.dto.account.RolePermissionResponse;
import com.carenest.dto.account.UpdatePermissionOverridesRequest;
import com.carenest.dto.account.UpdateRolePermissionsRequest;
import com.carenest.dto.account.UserPermissionOverridesResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.security.CurrentUser;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;

/** Cấu hình quyền: ma trận theo role, ngoại lệ từng người và lịch sử thay đổi (ADR-0017). */
public interface PermissionService {

    List<PermissionResponse> getPermissionCatalog();

    List<RolePermissionResponse> getRolePermissions();

    RolePermissionResponse getRolePermissions(CareNestRole role);

    RolePermissionResponse updateRolePermissions(
            CurrentUser actor, CareNestRole role, UpdateRolePermissionsRequest request);

    UserPermissionOverridesResponse getPermissionOverrides(Long userId);

    UserPermissionOverridesResponse updatePermissionOverrides(
            CurrentUser actor, Long userId, UpdatePermissionOverridesRequest request);

    PageResponse<PermissionChangeLogResponse> getPermissionChangeLogs(
            CareNestRole targetRole, Long targetUserId, int page, int size);
}
