package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.child.ChildProfileResponse;
import com.carenest.dto.child.ChildSummaryResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.entity.child.Child;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.child.ChildMapper;
import com.carenest.repository.child.ChildRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.ChildAccessPolicy;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildServiceImpl implements ChildService {
    private static final String LOG_PREFIX = "[ChildServiceImpl]";

    private final ChildRepository childRepository;
    private final EnrollmentService enrollmentService;
    private final ChildAccessPolicy childAccessPolicy;
    private final ChildMapper childMapper;
    private final StaffAccessPolicy staffAccessPolicy;
    private final ClassroomService classroomService;
    private final GuardianChildLinkService guardianChildLinkService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChildSummaryResponse> searchChildren(
            CurrentUser actor, Long campusId, Long classroomId, String keyword, int page, int size) {
        String logPrefix = LOG_PREFIX + "|searchChildren|userId=" + actor.userId() + "|campusId=" + campusId
                + "|classroomId=" + classroomId;
        log.info("{}|START", logPrefix);
        try {
            LocalDate today = LocalDate.now(clock);
            Collection<Long> classroomIds = scopedClassroomIds(actor, campusId, classroomId);
            String text = keyword == null ? "" : keyword.trim();
            PageRequest pageable =
                    PageRequest.of(page, size, Sort.by("fullName").and(Sort.by("id")));
            Page<Child> children;
            if (classroomIds == null) {
                children = childRepository.findByFullNameContainingIgnoreCase(text, pageable);
            } else if (classroomIds.isEmpty()) {
                children = Page.empty(pageable);
            } else {
                children = childRepository.searchEnrolled(
                        classroomIds, today, "%" + text.toLowerCase(Locale.ROOT) + "%", pageable);
            }

            List<Long> childIds =
                    children.getContent().stream().map(Child::getId).toList();
            Map<Long, Long> classroomByChild = enrollmentService.findActiveClassroomIdByChildIds(childIds, today);
            Map<Long, Long> campusByClassroom = classroomService.findCampusIds(classroomByChild.values());
            Map<Long, Long> guardianCounts = guardianChildLinkService.countGuardiansByChildIds(childIds);
            PageResponse<ChildSummaryResponse> response = PageResponse.from(children, child -> {
                Long currentClassroom = classroomByChild.get(child.getId());
                return new ChildSummaryResponse(
                        child.getId(),
                        child.getFullName(),
                        child.getDateOfBirth(),
                        currentClassroom,
                        currentClassroom == null ? null : campusByClassroom.get(currentClassroom),
                        guardianCounts.getOrDefault(child.getId(), 0L));
            });
            log.info("{}|total={}|END", logPrefix, children.getTotalElements());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

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

    // null = không giới hạn lớp (HT không lọc); PHT luôn giới hạn trong lớp của campus mình (AUTH-08)
    private Collection<Long> scopedClassroomIds(CurrentUser actor, Long campusId, Long classroomId) {
        boolean wholeSchool = staffAccessPolicy.managesWholeSchool(actor);
        Set<Long> managed = wholeSchool ? Set.of() : staffAccessPolicy.managedCampusIds(actor);
        if (classroomId != null) {
            Long classroomCampus = classroomService.findCampusId(classroomId).orElse(null);
            if (!wholeSchool && (classroomCampus == null || !managed.contains(classroomCampus))) {
                throw GlobalException.of(ApiCode.CLASS_ACCESS_DENIED);
            }
            return List.of(classroomId);
        }
        if (campusId != null) {
            if (!wholeSchool && !managed.contains(campusId)) {
                throw GlobalException.of(ApiCode.CAMPUS_ACCESS_DENIED);
            }
            return classroomService.findClassroomIdsOfCampuses(Set.of(campusId));
        }
        return wholeSchool ? null : classroomService.findClassroomIdsOfCampuses(managed);
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
