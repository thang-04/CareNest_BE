package com.carenest.service.child;

import com.carenest.dto.child.GuardianLinkResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Liên kết phụ huynh–trẻ (nguồn scope phụ huynh, AUTH-04) = phụ huynh trên hồ sơ trẻ đã có tài khoản
 * ({@code child_guardians.user_id}); không gọi ngược policy (MODULE_MAP). Tạo liên kết đi qua tiếp nhận / kích hoạt
 * tài khoản phụ huynh ({@code ChildIntakeService}).
 */
public interface GuardianChildLinkService {

    boolean isGuardianOf(Long userId, Long childId);

    List<Long> findChildIdsOfGuardian(Long userId);

    List<GuardianLinkResponse> findLinksOfChild(Long childId);

    /** Như {@link #findLinksOfChild} cho nhiều trẻ trong một truy vấn (màn Tài khoản phụ huynh). */
    List<GuardianLinkResponse> findLinksOfChildren(Collection<Long> childIds);

    /** childId → số phụ huynh đang liên kết (trẻ không có phụ huynh không có trong map). */
    Map<Long, Long> countGuardiansByChildIds(Collection<Long> childIds);

    /** Gỡ liên kết; tài khoản phụ huynh và thông tin phụ huynh trên hồ sơ giữ nguyên (AUTH-19). */
    void deleteLink(Long userId, Long childId);
}
