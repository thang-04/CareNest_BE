package com.carenest.service.child.impl;

import com.carenest.dto.child.GuardianLinkResponse;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildGuardianRepository;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.utils.ApiCode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final ChildGuardianRepository childGuardianRepository;

    @Override
    public boolean isGuardianOf(Long userId, Long childId) {
        String logPrefix = LOG_PREFIX + "|isGuardianOf|userId=" + userId + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            boolean guardian = childGuardianRepository.existsByUserIdAndChildId(userId, childId);
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
            List<Long> childIds = childGuardianRepository.findLinkedChildIds(userId);
            log.info("{}|END", logPrefix);
            return childIds;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<GuardianLinkResponse> findLinksOfChild(Long childId) {
        String logPrefix = LOG_PREFIX + "|findLinksOfChild|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            List<GuardianLinkResponse> links = childGuardianRepository.findLinkedOfChild(childId).stream()
                    .map(GuardianChildLinkServiceImpl::toResponse)
                    .toList();
            log.info("{}|END", logPrefix);
            return links;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<GuardianLinkResponse> findLinksOfChildren(Collection<Long> childIds) {
        String logPrefix = LOG_PREFIX + "|findLinksOfChildren|children=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            List<GuardianLinkResponse> links = childIds.isEmpty()
                    ? List.of()
                    : childGuardianRepository.findLinkedOfChildren(childIds).stream()
                            .map(GuardianChildLinkServiceImpl::toResponse)
                            .toList();
            log.info("{}|count={}|END", logPrefix, links.size());
            return links;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public Map<Long, Long> countGuardiansByChildIds(Collection<Long> childIds) {
        String logPrefix = LOG_PREFIX + "|countGuardiansByChildIds|children=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, Long> counts = new HashMap<>();
            if (!childIds.isEmpty()) {
                for (Object[] row : childGuardianRepository.countLinkedByChildIds(childIds)) {
                    counts.put((Long) row[0], (Long) row[1]);
                }
            }
            log.info("{}|END", logPrefix);
            return counts;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteLink(Long userId, Long childId) {
        String logPrefix = LOG_PREFIX + "|deleteLink|userId=" + userId + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            ChildGuardian guardian = childGuardianRepository
                    .findByUserIdAndChildId(userId, childId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.GUARDIAN_LINK_NOT_FOUND));
            guardian.unlinkAccount();
            childGuardianRepository.flush();
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static GuardianLinkResponse toResponse(ChildGuardian guardian) {
        return new GuardianLinkResponse(
                guardian.getUserId(), guardian.getChildId(), guardian.getRelationship(), guardian.getLinkedAt());
    }
}
