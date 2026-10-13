package com.carenest.service.child.impl;

import com.carenest.repository.child.GuardianChildLinkRepository;
import com.carenest.service.child.GuardianChildLinkService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianChildLinkServiceImpl implements GuardianChildLinkService {

    private final GuardianChildLinkRepository guardianChildLinkRepository;

    @Override
    public boolean isGuardianOf(Long userId, Long childId) {
        return guardianChildLinkRepository.existsByUserIdAndChildId(userId, childId);
    }

    @Override
    public List<Long> findChildIdsOfGuardian(Long userId) {
        return guardianChildLinkRepository.findChildIdsByUserId(userId);
    }
}
