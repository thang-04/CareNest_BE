package com.carenest.service.child.impl;

import com.carenest.repository.child.GuardianChildLinkRepository;
import com.carenest.service.child.GuardianChildLinkService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianChildLinkServiceImpl implements GuardianChildLinkService {
    private static final String LOG_PREFIX = "[GuardianChildLinkServiceImpl]";

    private final GuardianChildLinkRepository guardianChildLinkRepository;

    @Override
    public boolean isGuardianOf(Long userId, Long childId) {
        String logPrefix = LOG_PREFIX + "|isGuardianOf|userId=" + userId + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            boolean guardian = guardianChildLinkRepository.existsByUserIdAndChildId(userId, childId);
            log.info("{}|END", logPrefix);
            return guardian;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<Long> findChildIdsOfGuardian(Long userId) {
        String logPrefix = LOG_PREFIX + "|findChildIdsOfGuardian|userId=" + userId;
        log.info("{}|START", logPrefix);
        try {
            List<Long> childIds = guardianChildLinkRepository.findChildIdsByUserId(userId);
            log.info("{}|END", logPrefix);
            return childIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
