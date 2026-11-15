package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.entity.child.Child;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.child.ChildMapper;
import com.carenest.repository.child.ChildAllergyRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildServiceImpl implements ChildService {
    private static final String LOG_PREFIX = "[ChildServiceImpl]";

    private final ChildRepository childRepository;
    private final ChildAllergyRepository allergyRepository;
    private final EnrollmentService enrollmentService;
    private final ChildAccessPolicy childAccessPolicy;
    private final ChildMapper childMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public boolean childExists(Long childId) {
        String logPrefix = LOG_PREFIX + "|childExists|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            boolean exists = childRepository.existsById(childId);
            log.info("{}|END", logPrefix);
            return exists;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ChildBriefResponse> findChildBriefs(Collection<Long> childIds) {
        String logPrefix = LOG_PREFIX + "|findChildBriefs|count=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            Map<Long, ChildBriefResponse> briefs = childRepository.findAllById(childIds).stream()
                    .collect(Collectors.toMap(
                            Child::getId,
                            c -> new ChildBriefResponse(
                                    c.getId(), c.getCode(), c.getFullName(), c.getDateOfBirth(), c.getGender())));
            log.info("{}|END", logPrefix);
            return briefs;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findChildIdsWithAllergies(Collection<Long> childIds) {
        String logPrefix = LOG_PREFIX + "|findChildIdsWithAllergies|count=" + childIds.size();
        log.info("{}|START", logPrefix);
        try {
            Set<Long> result =
                    childIds.isEmpty() ? Set.of() : Set.copyOf(allergyRepository.findChildIdsWithAllergy(childIds));
            log.info("{}|allergic={}|END", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChildProfileResponse getChildProfile(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|getChildProfile|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            // Kiểm phạm vi đọc TRƯỚC khi load: id ngoài phạm vi và id không tồn tại cùng trả 403 (chống dò id)
            childAccessPolicy.checkCanReadChild(currentUser, childId);
            Child child =
                    childRepository.findById(childId).orElseThrow(() -> GlobalException.of(ApiCode.CHILD_NOT_FOUND));
            Long currentClassroomId = enrollmentService.findActiveClassroomIds(childId, LocalDate.now(clock)).stream()
                    .findFirst()
                    .orElse(null);

            ChildProfileResponse profile = childMapper.toProfileResponse(child, currentClassroomId);
            log.info("{}|END", logPrefix);
            return profile;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
