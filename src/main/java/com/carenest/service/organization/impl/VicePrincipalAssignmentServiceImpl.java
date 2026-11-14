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
            LocalDate end = endBeforeNextYear(open, year);
            // AUTH-26: mỗi điểm trường tối đa một PHT trong khoảng phân công mới
            if (campusId != null
                    && staffAssignmentRepository.existsOtherVicePrincipalAtCampusOpenEnded(
                            campusId, userId, start, end)) {
                Long holderId = Boolean.TRUE.equals(request.swap()) && current != null && current.getCampusId() != null
                        ? swapPartner(campusId, userId, start, year)
                        : null;
                if (holderId == null) {
                    throw GlobalException.of(ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);
                }
                swap(year, start, userId, campusId, holderId, current.getCampusId(), currentUser.userId());
                VicePrincipalAssignmentResponse swapped = toResponse(year);
                log.info("{}|swappedWith={}|END", logPrefix, holderId);
                return swapped;
            }
            closeOpen(open, year, start);
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

    // Phân công đã đặt cho năm sau (bắt đầu sau năm này) giữ nguyên; phân công mới dừng trước nó (null = không hạn)
    private static LocalDate endBeforeNextYear(List<StaffAssignment> open, SchoolYear year) {
        return open.stream()
                .map(StaffAssignment::getValidFrom)
                .filter(from -> from.isAfter(year.getEndDate()))
                .min(LocalDate::compareTo)
                .map(next -> next.minusDays(1))
                .orElse(null);
    }

    // Phân công của năm này: đã có hiệu lực thì kết thúc trước ngày áp dụng (giữ lịch sử), chưa có hiệu lực thì xóa
    private void closeOpen(List<StaffAssignment> open, SchoolYear year, LocalDate start) {
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
    }

    // Người đổi chỗ: đúng một PHT khác đang giữ điểm trường đích trong năm này, tài khoản còn hoạt động
    private Long swapPartner(Long campusId, Long userId, LocalDate start, SchoolYear year) {
        List<Long> holders =
                staffAssignmentRepository.findOtherVicePrincipalIdsAtCampus(campusId, userId, start, year.getEndDate());
        if (holders.size() != 1) {
            return null;
        }
        if (!staffAssignmentRepository.findInactiveUserIds(Set.copyOf(holders)).isEmpty()) {
            throw GlobalException.of(ApiCode.VICE_PRINCIPAL_ACCOUNT_LOCKED);
        }
        return holders.get(0);
    }

    /**
     * AUTH-26: hai PHT đổi điểm trường cho nhau trong một giao dịch. Kết thúc phân công năm này của cả hai trước rồi
     * mới tạo hai phân công mới; sau khi đổi mà điểm trường vẫn trùng người khác (vd. phân công đã đặt cho năm sau)
     * thì từ chối và không lưu gì.
     */
    private void swap(
            SchoolYear year,
            LocalDate start,
            Long userId,
            Long campusId,
            Long holderId,
            Long oldCampusId,
            Long actorId) {
        List<StaffAssignment> mine = staffAssignmentRepository.findOpenVicePrincipalAssignments(userId, start);
        List<StaffAssignment> theirs = staffAssignmentRepository.findOpenVicePrincipalAssignments(holderId, start);
        LocalDate myEnd = endBeforeNextYear(mine, year);
        LocalDate theirEnd = endBeforeNextYear(theirs, year);
        closeOpen(mine, year, start);
        closeOpen(theirs, year, start);
        staffAssignmentRepository.flush();
        if (staffAssignmentRepository.existsOtherVicePrincipalAtCampusOpenEnded(campusId, userId, start, myEnd)
                || staffAssignmentRepository.existsOtherVicePrincipalAtCampusOpenEnded(
                        oldCampusId, holderId, start, theirEnd)) {
            throw GlobalException.of(ApiCode.VICE_PRINCIPAL_CAMPUS_TAKEN);
        }
        staffAssignmentRepository.save(StaffAssignment.vicePrincipal(userId, campusId, start, myEnd, actorId));
        staffAssignmentRepository.save(StaffAssignment.vicePrincipal(holderId, oldCampusId, start, theirEnd, actorId));
        staffAssignmentRepository.flush();
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
            // Ngày mở chuẩn bị không được chồng lên năm trước (dữ liệu cũ thiếu ngày này)
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
