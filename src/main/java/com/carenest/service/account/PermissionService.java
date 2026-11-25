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
import com.carenest.utils.enums.Permission;
import java.util.List;

/** Cấu hình quyền: ma trận theo role, ngoại lệ từng người và lịch sử thay đổi (ADR-0017). */
public interface PermissionService {

    List<PermissionResponse> getPermissionCatalog();

    List<RolePermissionResponse> getRolePermissions();

    RolePermissionResponse getRolePermissions(CareNestRole role);

    RolePermissionResponse updateRolePermissions(
            CurrentUser actor, CareNestRole role, UpdateRolePermissionsRequest request);

    /** EDU-110 WF1: lưu nhiều vai trò trong một giao dịch, tự suy chuỗi soạn/duyệt; trả ma trận như GET. */
    List<RolePermissionResponse> updateRolePermissionsBulk(
            CurrentUser actor, com.carenest.dto.account.BulkUpdateRolePermissionsRequest request);

    /** EDU-110 BR4.3: vi phạm luật soạn/duyệt của ô tick hiện tại. */
    com.carenest.dto.account.EduWorkflowIssuesResponse getEduWorkflowIssues();

    /**
     * EDU-110 WF2 (BR4.1–4.2): chuyển chuỗi đang chạy sang ô tick 8 mã EDU một lần, ghi lịch sử lý do
     * {@code EDU-110 chuyển cấu hình} (actor = HT hiệu lực). Trả mô tả kết quả để log; ném lỗi ⇒ hoàn tác.
     */
    String migrateEduRoleMatrix(java.time.Instant now);

    UserPermissionOverridesResponse getPermissionOverrides(Long userId);

    UserPermissionOverridesResponse updatePermissionOverrides(
            CurrentUser actor, Long userId, UpdatePermissionOverridesRequest request);

    /** Xóa mọi ngoại lệ của tài khoản kèm lịch sử (đổi/thu hồi vai trò, AUTH-25); chạy trong transaction người gọi. */
    void clearPermissionOverrides(Long actorUserId, Long userId, String reason);

    PageResponse<PermissionChangeLogResponse> getPermissionChangeLogs(
            CareNestRole targetRole, Long targetUserId, int page, int size);

    /** Quyền hiệu lực của một nhân sự theo role cấu hình được (ma trận + ngoại lệ), dùng chọn người nhận thông báo. */
    boolean hasEffectivePermission(Long userId, CareNestRole role, Permission permission);
}
