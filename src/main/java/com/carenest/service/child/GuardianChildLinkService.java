package com.carenest.service.child;

import com.carenest.repository.child.GuardianChildLinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Liên kết phụ huynh–trẻ (nguồn scope phụ huynh, AUTH-04); không gọi ngược policy (MODULE_MAP). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianChildLinkService {

    private final GuardianChildLinkRepository guardianChildLinkRepository;

    public boolean isGuardianOf(Long userId, Long childId) {
        return guardianChildLinkRepository.existsByUserIdAndChildId(userId, childId);
    }

    public List<Long> findChildIdsOfGuardian(Long userId) {
        return guardianChildLinkRepository.findChildIdsByUserId(userId);
    }
}
