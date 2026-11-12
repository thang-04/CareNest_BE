package com.carenest.service.organization.impl;

import com.carenest.dto.organization.VicePrincipalAssignmentRequest;
import com.carenest.dto.organization.VicePrincipalAssignmentResponse;
import com.carenest.entity.organization.Campus;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.entity.organization.StaffAssignment;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.organization.VicePrincipalAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.SchoolYearStatus;
import com.carenest.utils.enums.StaffRole;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phân công PHT phụ trách điểm trường theo năm học. Năm đang chạy áp dụng từ hôm nay, năm sắp tới từ ngày bắt đầu
 * năm; phân công cũ kết thúc trước ngày đó (giữ lịch sử), phân công chưa có hiệu lực thì xóa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VicePrincipalAssignmentServiceImpl implements VicePrincipalAssignmentService {
    private static final String LOG_PREFIX = "[VicePrincipalAssignmentServiceImpl]";

    private final StaffAssignmentRepository staffAssignmentRepository;
    private final SchoolYearRepository schoolYearRepository;
    private final CampusRepository campusRepository;
    private final ClassroomRepository classroomRepository;
    private final SchoolConfigAccess schoolConfigAccess;
    private final StaffAccessPolicy staffAccessPolicy;
    private final SchoolStructureMapper schoolStructureMapper;

    @Override
    @Transactional(readOnly = true)
    public VicePrincipalAssignmentResponse getAssignments(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getAssignments|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-16: phân công PHT do HT quản lý toàn trường (StaffAccessPolicy)
            if (!staffAccessPolicy.managesWholeSchool(currentUser)) {
                throw GlobalException.of(ApiCode.STAFF_ASSIGNMENT_MANAGE_DENIED);
            }
            VicePrincipalAssignmentResponse response = toResponse(findYear(schoolYearId));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public VicePrincipalAssignmentResponse assign(
            CurrentUser currentUser, Long schoolYearId, Long userId, VicePrincipalAssignmentRequest request) {
        Long campusId = request.campusId();
        String logPrefix = LOG_PREFIX + "|assign|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId
                + "|vicePrincipalId=" + userId + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            staffAccessPolicy.checkCanAssign(currentUser, StaffRole.VICE_PRINCIPAL, campusId);
            SchoolYear year = findYear(schoolYearId);
            SchoolYearRules.checkOpen(year);
            if (staffAssignmentRepository.findVicePrincipalRows().stream()
                    .noneMatch(r -> r.getUserId().equals(userId))) {
                throw GlobalException.of(ApiCode.VICE_PRINCIPAL_INVALID);
            }
            if (!staffAssignmentRepository.findInactiveUserIds(Set.of(userId)).isEmpty()) {
                throw GlobalException.of(ApiCode.VICE_PRINCIPAL_ACCOUNT_LOCKED);
            }
            if (campusId != null) {
                Campus campus = campusRepository
                        .findById(campusId)
                        .orElseThrow(() -> GlobalException.of(ApiCode.CAMPUS_NOT_FOUND));
                if (!campus.isActive()) {
                    throw GlobalException.of(ApiCode.CAMPUS_INACTIVE);
                }
            }
            LocalDate start = effectiveStart(year);
            List<StaffAssignment> open = staffAssignmentRepository.findOpenVicePrincipalAssignments(userId, start);
            StaffAssignment current = open.stream()
                    .filter(a -> !a.getValidFrom().isAfter(start))
                    .findFirst()
                    .orElse(null);
            if (current != null && Objects.equals(current.getCampusId(), campusId)) {
                log.info("{}|unchanged|END", logPrefix);
                return toResponse(year);
            }
            // Phân công PHT theo năm học (Cần chốt 12/11/2026): hết hạn ở ngày kết thúc năm, năm sau HT phân công lại;
            // phân công đã đặt cho năm sau giữ nguyên
            LocalDate end = year.getEndDate();
            // AUTH-26: mỗi điểm trường tối đa một PHT trong khoảng phân công mới
            if (campusId != null
                    && staffAssignmentRepository.existsOtherVicePrincipalAtCampusOpenEnded(
                            campusId, userId, start, end)) {
                throw GlobalException.of(ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);
            }
            for (StaffAssignment a : open) {
                if (a.getValidFrom().isAfter(year.getEndDate())) {
                    continue;
                }
                if (a.getValidFrom().isBefore(start)) {
                    a.endBefore(start);
                } else {
                    staffAssignmentRepository.delete(a);
                }
            }
            if (campusId != null) {
                staffAssignmentRepository.save(
                        StaffAssignment.vicePrincipal(userId, campusId, start, end, currentUser.userId()));
            }
            staffAssignmentRepository.flush();
            VicePrincipalAssignmentResponse response = toResponse(year);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private VicePrincipalAssignmentResponse toResponse(SchoolYear year) {
        LocalDate from = year.getStatus() == SchoolYearStatus.CLOSED ? year.getStartDate() : effectiveStart(year);
        // Mỗi PHT lấy phân công mới nhất còn hiệu lực trong năm (năm đang chạy: từ hôm nay)
        Map<Long, StaffAssignment> latest = new LinkedHashMap<>();
        for (StaffAssignment a : staffAssignmentRepository.findVicePrincipalAssignments(from, year.getEndDate())) {
            latest.putIfAbsent(a.getUserId(), a);
        }
        List<VicePrincipalAssignmentResponse.Row> rows = staffAssignmentRepository.findVicePrincipalRows().stream()
                .map(r -> {
                    StaffAssignment a = latest.get(r.getUserId());
                    return new VicePrincipalAssignmentResponse.Row(
                            new VicePrincipalAssignmentResponse.VicePrincipal(
                                    r.getUserId(), r.getFullName(), r.getEmail(), r.getStatus()),
                            a == null
                                    ? null
                                    : new VicePrincipalAssignmentResponse.Assignment(
                                            a.getCampusId(), a.getValidFrom(), a.getValidTo(), a.getAssignedBy()));
                })
                .toList();
        return new VicePrincipalAssignmentResponse(
                schoolStructureMapper.toDetailResponse(year, classroomRepository.countBySchoolYearId(year.getId())),
                rows);
    }

    // Năm đang chạy: áp dụng từ hôm nay; năm sắp tới: từ ngày mở chuẩn bị (CFG-YR-05) để PHT năm mới chuẩn bị được
    private LocalDate effectiveStart(SchoolYear year) {
        LocalDate today = schoolConfigAccess.today();
        if (today.isAfter(year.getStartDate())) {
            return today;
        }
        if (year.getStatus() == SchoolYearStatus.PLANNED) {
            LocalDate prep = year.getPrepDate().isAfter(year.getStartDate()) ? year.getStartDate() : year.getPrepDate();
            // Ngày mở chuẩn bị luôn sau ngày kết thúc năm trước (2036); dữ liệu cũ thiếu ngày này thì vẫn không chồng
            // năm trước
            for (SchoolYear other : schoolYearRepository.findAllByOrderByStartDateDesc()) {
                if (other.getEndDate().isBefore(year.getStartDate()) && !prep.isAfter(other.getEndDate())) {
                    prep = other.getEndDate().plusDays(1);
                }
            }
            return today.isAfter(prep) ? today : prep;
        }
        return year.getStartDate();
    }

    private SchoolYear findYear(Long schoolYearId) {
        return schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
    }
}
