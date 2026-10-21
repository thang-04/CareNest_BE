package com.carenest.service.child;

import java.util.List;

/** Liên kết phụ huynh–trẻ (nguồn scope phụ huynh, AUTH-04); không gọi ngược policy (MODULE_MAP). */
public interface GuardianChildLinkService {

    boolean isGuardianOf(Long userId, Long childId);

    List<Long> findChildIdsOfGuardian(Long userId);
}
