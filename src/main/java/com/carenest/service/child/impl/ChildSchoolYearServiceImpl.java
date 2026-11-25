package com.carenest.service.child.impl;

import com.carenest.dto.child.ChildSchoolYearResponse;
import com.carenest.dto.organization.SchoolYearDetailResponse;
import com.carenest.entity.child.ClassPlacement;
import com.carenest.entity.child.Enrollment;
import com.carenest.entity.child.PlannedAdmission;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ClassPlacementRepository;
import com.carenest.repository.child.EnrollmentRepository;
import com.carenest.repository.child.PlannedAdmissionRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildSchoolYearService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.PlannedAdmissionStatus;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildSchoolYearServiceImpl implements ChildSchoolYearService {
    private static final String LOG_PREFIX = "[ChildSchoolYearServiceImpl]";
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassPlacementRepository placementRepository;
    private final PlannedAdmissionRepository admissionRepository;
    private final ClassroomService classroomService;
    private final SchoolYearService schoolYearService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ChildSchoolYearResponse> getChildSchoolYears(CurrentUser currentUser, Long childId) {
        String logPrefix =
                LOG_PREFIX + "|getChildSchoolYears|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            List<ChildSchoolYearResponse> years = visibleYears(childId);
            log.info("{}|years={}|END", logPrefix, years.size());
            return years;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChildSchoolYearResponse requireChildYear(CurrentUser currentUser, Long childId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|requireChildYear|userId=" + currentUser.userId() + "|childId=" + childId
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            checkParent(currentUser, childId);
            // Năm trẻ không học hoặc không được thấy ⇒ như không có năm đó (giống GET năm học)
            ChildSchoolYearResponse year = yearOf(childId, schoolYearId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
            log.info("{}|END", logPrefix);
            return year;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChildSchoolYearResponse> findChildYear(Long childId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|findChildYear|childId=" + childId + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            Optional<ChildSchoolYearResponse> year = yearOf(childId, schoolYearId);
            log.info("{}|END", logPrefix);
            return year;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private Optional<ChildSchoolYearResponse> yearOf(Long childId, Long schoolYearId) {
        return visibleYears(childId).stream()
                .filter(y -> y.schoolYearId().equals(schoolYearId))
                .findFirst();
    }

    // AUTH-04: chỉ phụ huynh đã liên kết với trẻ
    private void checkParent(CurrentUser user, Long childId) {
        if (!user.hasRole(CareNestRole.PARENT) || !guardianChildLinkService.isGuardianOf(user.userId(), childId)) {
            throw GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN);
        }
    }

    private List<ChildSchoolYearResponse> visibleYears(Long childId) {
        // Lớp theo năm: lớp đã học (vào sau cùng trong năm) trước, rồi lớp được xếp / tiếp nhận cho năm mới
        List<Long> classroomIds = new ArrayList<>();
        enrollmentRepository.findChildDuring(childId, EARLIEST, LATEST).stream()
                .sorted(Comparator.comparing(Enrollment::getValidFrom).reversed())
                .map(Enrollment::getClassroomId)
                .forEach(classroomIds::add);
        placementRepository.findAllByChildId(childId).stream()
                .filter(p -> p.getAction().keepsChildInClass() && p.getToClassroomId() != null)
                .map(ClassPlacement::getToClassroomId)
                .forEach(classroomIds::add);
        admissionRepository
                .findByChildIdInAndStatusIn(
                        List.of(childId), List.of(PlannedAdmissionStatus.PENDING, PlannedAdmissionStatus.PLACED))
                .stream()
                .map(PlannedAdmission::getClassroomId)
                .filter(Objects::nonNull)
                .forEach(classroomIds::add);

        Map<Long, String> classByYear = new LinkedHashMap<>();
        for (Long classroomId : classroomIds) {
            classroomService
                    .findClassroomInfo(classroomId)
                    .ifPresent(c -> classByYear.putIfAbsent(c.schoolYearId(), c.name()));
        }
        LocalDate today = LocalDate.now(clock);
        return classByYear.entrySet().stream()
                .map(e -> schoolYearService
                        .findSchoolYearDetail(e.getKey())
                        .filter(y -> isVisible(y, today))
                        .map(y -> toResponse(y, e.getValue())))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(ChildSchoolYearResponse::startDate).reversed())
                .toList();
    }

    // CFG-YR-05: năm sắp diễn ra chỉ hiện từ ngày mở chuẩn bị
    private static boolean isVisible(SchoolYearDetailResponse year, LocalDate today) {
        if (year.status() != SchoolYearStatus.PLANNED) {
            return true;
        }
        LocalDate prep = year.prepDate() != null ? year.prepDate() : year.startDate();
        return !today.isBefore(prep);
    }

    private static ChildSchoolYearResponse toResponse(SchoolYearDetailResponse year, String className) {
        return new ChildSchoolYearResponse(
                year.id(), year.name(), year.startDate(), year.endDate(), year.status(), className);
    }
}
