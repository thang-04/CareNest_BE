package com.carenest.service.health.impl;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.dto.organization.SchoolYearResponse;
import com.carenest.entity.health.HealthCheckAbsence;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckResult;
import com.carenest.utils.enums.HealthCheckStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Quy tắc dùng chung của đợt khám/kỳ cân đo: ngày, phạm vi xem/quản lý, lớp của đợt, danh sách trẻ và kết quả. */
@Component
@RequiredArgsConstructor
class HealthCheckRules {

    // Ngày bắt đầu cách đợt/kỳ khác dưới 2 tháng 15 ngày là "sát nhau" (user chốt 2026-10-31)
    private static final int MIN_GAP_MONTHS = 2;
    private static final int MIN_GAP_DAYS = 15;

    private final Clock clock;
    private final CampusAccessPolicy campusAccessPolicy;
    private final ClassroomAccessPolicy classroomAccessPolicy;
    private final StaffAssignmentService staffAssignmentService;
    private final ClassroomService classroomService;
    private final SchoolStructureService schoolStructureService;
    private final EnrollmentService enrollmentService;
    private final HealthCheckCampaignRepository campaignRepository;

    /** Một trẻ trong danh sách lớp của đợt/kỳ; {@code measurement} null khi chưa có kết quả. */
    record Row(Long childId, Long classId, HealthCheckResult result, HealthMeasurement measurement) {}

    LocalDate today() {
        return LocalDate.now(clock);
    }

    Instant now() {
        return clock.instant();
    }

    /** HT mọi điểm trường, PHT điểm trường được phân công (user chốt 2026-10-31). */
    boolean canManage(CurrentUser user, Long campusId) {
        return (user.hasRole(CareNestRole.PRINCIPAL) || user.hasRole(CareNestRole.VICE_PRINCIPAL))
                && campusAccessPolicy.canViewCampus(user, campusId);
    }

    /** Điểm trường người dùng xem được đợt/kỳ; null = mọi điểm trường (HT). GV: điểm trường của lớp mình. */
    Set<Long> visibleCampusIds(CurrentUser user) {
        LocalDate today = today();
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today)) {
            return null;
        }
        Set<Long> campusIds = new HashSet<>();
        if (user.hasRole(CareNestRole.VICE_PRINCIPAL)) {
            campusIds.addAll(
                    staffAssignmentService.findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today));
        }
        if (user.hasRole(CareNestRole.TEACHER)) {
            campusIds.addAll(staffAssignmentService.findActiveCampusIds(user.userId(), StaffRole.TEACHER, today));
        }
        return campusIds;
    }

    /** Ngoài phạm vi trả 404 như không tồn tại (không lộ đợt của điểm trường khác). */
    HealthCheckCampaign findVisible(CurrentUser user, Long campaignId) {
        HealthCheckCampaign campaign = campaignRepository
                .findById(campaignId)
                .orElseThrow(() -> GlobalException.of(ApiCode.HEALTH_CHECK_NOT_FOUND));
        Set<Long> visible = visibleCampusIds(user);
        if (visible != null && !visible.contains(campaign.getCampusId())) {
            throw GlobalException.of(ApiCode.HEALTH_CHECK_NOT_FOUND);
        }
        return campaign;
    }

    HealthCheckCampaign findManageable(CurrentUser user, Long campaignId) {
        HealthCheckCampaign campaign = findVisible(user, campaignId);
        if (!canManage(user, campaign.getCampusId())) {
            throw GlobalException.of(ApiCode.HEALTH_CHECK_ACCESS_DENIED);
        }
        return campaign;
    }

    /** G94-180/HLT-08: một ngày thuộc năm học gần nhất đã bắt đầu tính tới ngày đó; null khi trước mọi năm học. */
    Long schoolYearIdOf(LocalDate date) {
        return schoolStructureService.findSchoolYears().stream()
                .filter(y -> y.startDate() != null && !y.startDate().isAfter(date))
                .max(Comparator.comparing(SchoolYearResponse::startDate))
                .map(SchoolYearResponse::id)
                .orElse(null);
    }

    /** Lớp của điểm trường trong năm học của ngày bắt đầu đợt/kỳ. */
    List<ClassroomInfoResponse> classesOf(HealthCheckCampaign campaign) {
        Long yearId = schoolYearIdOf(campaign.getStartDate());
        if (yearId == null) {
            return List.of();
        }
        return classroomService.findYearClassrooms(yearId).stream()
                .filter(c -> c.campusId().equals(campaign.getCampusId()))
                .sorted(Comparator.comparing(ClassroomInfoResponse::name))
                .toList();
    }

    List<ClassroomInfoResponse> visibleClassesOf(CurrentUser user, HealthCheckCampaign campaign) {
        return classesOf(campaign).stream()
                .filter(c -> classroomAccessPolicy.canViewClassroom(user, c.id()))
                .toList();
    }

    /** HLT-09: GV đang được phân công lớp nhập kết quả cho lớp mình. */
    boolean canRecord(CurrentUser user, ClassroomInfoResponse cls) {
        return teaches(user, cls.id());
    }

    /** GV đang được phân công lớp (nhập phiếu khám, cập nhật phiếu theo dõi của trẻ lớp mình). */
    boolean teaches(CurrentUser user, Long classId) {
        return classId != null
                && user.hasRole(CareNestRole.TEACHER)
                && staffAssignmentService.hasActiveTeacherAssignment(user.userId(), classId, today());
    }

    /** Điểm trường BGH quản lý (xem phiếu theo dõi của trẻ không còn lớp); null = mọi điểm trường (HT), GV rỗng. */
    Set<Long> managedCampusIds(CurrentUser user) {
        LocalDate today = today();
        if (user.hasRole(CareNestRole.PRINCIPAL)
                && staffAssignmentService.hasActivePrincipalAssignment(user.userId(), today)) {
            return null;
        }
        return user.hasRole(CareNestRole.VICE_PRINCIPAL)
                ? Set.copyOf(staffAssignmentService.findActiveCampusIds(user.userId(), StaffRole.VICE_PRINCIPAL, today))
                : Set.of();
    }

    /**
     * Trẻ của lớp trong đợt/kỳ: chưa đóng ⇒ trẻ đang học lớp (tại hôm nay, trong khoảng ngày của đợt) và trẻ đã có kết quả;
     * đã đóng ⇒ trẻ có kết quả và trẻ ghi vắng lúc đóng.
     */
    List<Row> rosterOf(
            HealthCheckCampaign campaign,
            ClassroomInfoResponse cls,
            List<HealthMeasurement> measurements,
            List<HealthCheckAbsence> absences) {
        boolean closed = campaign.statusOn(today()) == HealthCheckStatus.CLOSED;
        Map<Long, HealthMeasurement> byChild = measurements.stream()
                .filter(m -> m.getCampaignId().equals(campaign.getId())
                        && m.getClassroomId().equals(cls.id()))
                .collect(Collectors.toMap(HealthMeasurement::getChildId, Function.identity()));
        Set<Long> absent = absences.stream()
                .filter(a -> a.getCampaignId().equals(campaign.getId())
                        && a.getClassroomId().equals(cls.id()))
                .map(HealthCheckAbsence::getChildId)
                .collect(Collectors.toSet());
        Set<Long> childIds = new LinkedHashSet<>();
        if (!closed) {
            enrollmentService
                    .findChildrenInClassroom(cls.id(), referenceDate(campaign))
                    .forEach(c -> childIds.add(c.id()));
        }
        childIds.addAll(byChild.keySet());
        if (closed) {
            childIds.addAll(absent);
        }
        return childIds.stream()
                .map(id -> new Row(
                        id,
                        cls.id(),
                        byChild.containsKey(id)
                                ? resultOf(byChild.get(id), closed)
                                : absent.contains(id) ? HealthCheckResult.ABSENT : HealthCheckResult.NOT_RECORDED,
                        byChild.get(id)))
                .toList();
    }

    /** Phiếu khám còn nháp chưa là kết quả: đang khám, đợt đã đóng thì chưa kết luận. */
    private static HealthCheckResult resultOf(HealthMeasurement m, boolean closed) {
        if (m.isDraft()) {
            return closed ? HealthCheckResult.NOT_CONCLUDED : HealthCheckResult.IN_PROGRESS;
        }
        return HealthCheckResult.RECORDED;
    }

    /** Ngày lấy sĩ số lớp: hôm nay, kẹp trong khoảng ngày của đợt/kỳ. */
    LocalDate referenceDate(HealthCheckCampaign campaign) {
        LocalDate today = today();
        if (today.isBefore(campaign.getStartDate())) {
            return campaign.getStartDate();
        }
        return today.isAfter(campaign.getEndDate()) ? campaign.getEndDate() : today;
    }

    /** Ngày bắt đầu sớm nhất không còn "sát" ngày date. */
    static LocalDate gapEndOf(LocalDate date) {
        return date.plusMonths(MIN_GAP_MONTHS).plusDays(MIN_GAP_DAYS);
    }

    static String kindLabel(HealthCheckKind kind) {
        return kind == HealthCheckKind.MEASUREMENT ? "kỳ cân đo" : "đợt khám";
    }
}
