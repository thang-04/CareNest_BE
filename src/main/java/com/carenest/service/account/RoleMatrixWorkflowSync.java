package com.carenest.service.account;

import com.carenest.dto.account.EduWorkflowIssuesResponse;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Permission;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * EDU-110: chuỗi soạn/duyệt kế hoạch giáo dục suy từ ma trận quyền. Khai ở account, cài ở eduplan để giữ chiều phụ
 * thuộc eduplan → account (ArchitectureRulesTest cấm vòng giữa các slice service).
 */
public interface RoleMatrixWorkflowSync {

    /** Lấy khóa ghi cài đặt giáo dục trước khi đọc ma trận/policy (NFR1.5). */
    void lockWorkflowSettings();

    /** Vi phạm BR1.1–BR1.5 của ma trận (vai trò → mã đang bật); rỗng khi hợp lệ. */
    EduWorkflowIssuesResponse validate(Map<CareNestRole, Set<Permission>> matrix);

    /** BR4.1: ô tick 8 mã EDU của 4 vai trò kế hoạch giáo dục theo policy đang chạy (creators ⇒ EDIT, reviewers ⇒ APPROVE). */
    Map<CareNestRole, Set<Permission>> ticksFromCurrentPolicy();

    /** HT có phân công hiệu lực hôm nay (id nhỏ nhất khi có nhiều); rỗng khi không có. */
    java.util.Optional<Long> activePrincipalUserId(Instant now);

    /** Ghi chuỗi suy ra (BR3.1–3.2) cho mọi loại hợp lệ; trả số loại có đổi. */
    int apply(Map<CareNestRole, Set<Permission>> matrix, Long actorUserId, Instant now);
}
