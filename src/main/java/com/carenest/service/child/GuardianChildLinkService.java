package com.carenest.service.child;

import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.utils.enums.GuardianRelationship;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Liên kết phụ huynh–trẻ (nguồn scope phụ huynh, AUTH-04); không gọi ngược policy (MODULE_MAP). */
public interface GuardianChildLinkService {

    boolean isGuardianOf(Long userId, Long childId);

    List<Long> findChildIdsOfGuardian(Long userId);

    List<GuardianLinkResponse> findLinksOfChild(Long childId);

    /** childId → số phụ huynh đang liên kết (trẻ không có phụ huynh không có trong map). */
    Map<Long, Long> countGuardiansByChildIds(Collection<Long> childIds);

    /** Đã liên kết ⇒ 409. Kiểm quyền người thao tác ở feature account. */
    GuardianLinkResponse createLink(Long userId, Long childId, GuardianRelationship relationship);

    /** Gỡ liên kết; tài khoản phụ huynh giữ nguyên (AUTH-19). */
    void deleteLink(Long userId, Long childId);
}
