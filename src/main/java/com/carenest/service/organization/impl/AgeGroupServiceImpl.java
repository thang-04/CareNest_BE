package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AgeGroupRequest;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.Classroom;
import com.carenest.entity.organization.SchoolYear;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.SchoolYearStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AgeGroupServiceImpl implements AgeGroupService {
    /** Sĩ số tối đa theo luật của nhóm lớn nhất (mẫu giáo 5–6 tuổi, V176). */
    private static final int LEGAL_MAX_CLASS_SIZE = 35;

    private static final String LOG_PREFIX = "[AgeGroupServiceImpl]";

    private final AgeGroupRepository ageGroupRepository;
    private final ClassroomRepository classroomRepository;
    private final SchoolConfigAccess schoolConfigAccess;
    private final SchoolStructureMapper schoolStructureMapper;
    private final SchoolYearRepository schoolYearRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AgeGroupDetailResponse> getAgeGroups() {
        String logPrefix = LOG_PREFIX + "|getAgeGroups";
        log.info("{}|START", logPrefix);
        try {
            List<AgeGroupDetailResponse> response = ageGroupRepository.findAll().stream()
                    .sorted(ageOrder())
                    .map(schoolStructureMapper::toDetailResponse)
                    .toList();
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgeGroupDetailResponse> getStandardAgeGroupCatalog() {
        return ageGroupRepository.findAllByStandardGroupTrueOrderBySortOrderAsc().stream()
                .map(schoolStructureMapper::toDetailResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgeGroupDetailResponse> getYearAgeGroups(Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getYearAgeGroups|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            List<AgeGroupDetailResponse> response = yearDetails(schoolYearId);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgeGroupDetailResponse> getYearAgeGroups(CurrentUser currentUser, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|getYearAgeGroups|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            // CFG-YR-05: năm chưa tới ngày mở chuẩn bị chỉ HT thấy
            schoolConfigAccess.checkYearVisible(currentUser, findYear(schoolYearId));
            List<AgeGroupDetailResponse> response = yearDetails(schoolYearId);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AgeGroupDetailResponse createAgeGroup(CurrentUser currentUser, AgeGroupRequest request, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|createAgeGroup|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            SchoolYear year = openYear(schoolYearId);
            if (year.getStatus() != SchoolYearStatus.PLANNED) {
                throw GlobalException.of(
                        ApiCode.AGE_GROUP_CREATE_YEAR_NOT_PLANNED,
                        year.getName(),
                        year.getStatus().name());
            }
            AgeGroup ageGroup = standardGroup(request.standardAgeGroupId());
            AgeGroupRequest normalized = standardRequest(ageGroup, request);
            List<AgeGroupDetailResponse> inYear = yearDetails(schoolYearId);
            if (ageGroupRepository.isInYear(schoolYearId, ageGroup.getId())) {
                throw GlobalException.of(ApiCode.AGE_GROUP_NAME_EXISTS);
            }
            checkNameInYear(inYear, normalized.name().trim(), null);
            checkOverlapInYear(inYear, normalized.ageFromMonths(), normalized.ageToMonths(), null);
            ageGroupRepository.addToYear(schoolYearId, ageGroup.getId());
            saveYearInfo(year, ageGroup, normalized);
            ageGroupRepository.flush();
            AgeGroupDetailResponse response = detailInYear(schoolYearId, ageGroup.getId());
            log.info("{}|ageGroupId={}|END", logPrefix, ageGroup.getId());
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteAgeGroup(CurrentUser currentUser, Long ageGroupId, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|deleteAgeGroup|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            SchoolYear year = openYear(schoolYearId);
            if (year.getStatus() != SchoolYearStatus.PLANNED) {
                throw GlobalException.of(
                        ApiCode.AGE_GROUP_MEMBERSHIP_YEAR_NOT_PLANNED,
                        year.getName(),
                        year.getStatus().name());
            }
            if (!ageGroupRepository.isInYear(schoolYearId, ageGroupId)) {
                throw GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND);
            }
            // Chỉ xét lớp, tổ trưởng của năm đang chọn; năm khác vẫn giữ nhóm này
            if (ageGroupRepository.countClassesInYear(ageGroupId, schoolYearId) > 0) {
                throw GlobalException.of(ApiCode.AGE_GROUP_HAS_DEPENDENTS, "lớp ở năm học " + year.getName());
            }
            var leaderId = ageGroupRepository.findLeaderInYear(ageGroupId, schoolYearId);
            if (leaderId.isPresent()) {
                String name = fullName(leaderId.get());
                throw GlobalException.of(
                        ApiCode.AGE_GROUP_HAS_DEPENDENTS,
                        "tổ trưởng" + (name == null ? "" : " " + name) + " ở năm học " + year.getName());
            }
            ageGroupRepository.removeFromYear(schoolYearId, ageGroupId);
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void copyYearAgeGroups(Long sourceYearId, Long targetYearId) {
        String logPrefix =
                LOG_PREFIX + "|copyYearAgeGroups|sourceYearId=" + sourceYearId + "|targetYearId=" + targetYearId;
        log.info("{}|START", logPrefix);
        try {
            if (sourceYearId != null) {
                ageGroupRepository.copyYearAgeGroups(sourceYearId, targetYearId);
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AgeGroupDetailResponse createAgeGroup(CurrentUser currentUser, AgeGroupRequest request) {
        String logPrefix = LOG_PREFIX + "|createAgeGroup|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            validate(request, null);
            // API cũ không kèm năm học: vẫn không được vượt sĩ số tối đa theo luật của nhóm lớn nhất (Điều lệ GDMN)
            if (request.maxClassSize() != null && request.maxClassSize() > LEGAL_MAX_CLASS_SIZE) {
                throw GlobalException.of(ApiCode.AGE_GROUP_MAX_EXCEEDS_STANDARD, request.name(), LEGAL_MAX_CLASS_SIZE);
            }
            short sortOrder = (short) (ageGroupRepository.findMaxSortOrder() + 1);
            AgeGroup ageGroup = AgeGroup.create(nextCode(sortOrder), sortOrder);
            applyRequest(ageGroup, request);
            // Gọi không kèm năm học (client cũ): như API theo năm, chỉ thêm vào năm sắp diễn ra; năm đang hoạt động /
            // đã
            // kết thúc không đổi
            List<SchoolYear> plannedYears = plannedYears();
            if (plannedYears.isEmpty()) {
                SchoolYear latest = schoolYearRepository.findAllByOrderByStartDateDesc().stream()
                        .findFirst()
                        .orElseThrow(() -> GlobalException.of(ApiCode.NO_ACTIVE_SCHOOL_YEAR));
                throw GlobalException.of(
                        ApiCode.AGE_GROUP_CREATE_YEAR_NOT_PLANNED,
                        latest.getName(),
                        latest.getStatus().name());
            }
            ageGroup = ageGroupRepository.save(ageGroup);
            for (SchoolYear year : plannedYears) {
                ageGroupRepository.addToYear(year.getId(), ageGroup.getId());
            }
            log.info("{}|ageGroupId={}|END", logPrefix, ageGroup.getId());
            return schoolStructureMapper.toDetailResponse(ageGroup);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AgeGroupDetailResponse updateAgeGroup(
            CurrentUser currentUser, Long ageGroupId, AgeGroupRequest request, Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|updateAgeGroup|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId
                + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            SchoolYear year = openYear(schoolYearId);
            if (!ageGroupRepository.isInYear(schoolYearId, ageGroupId)) {
                throw GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND);
            }
            AgeGroup ageGroup = ageGroupRepository
                    .findById(ageGroupId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
            AgeGroupRequest normalized = ageGroup.isStandardGroup() ? standardRequest(ageGroup, request) : request;
            if (normalized.ageToMonths() <= normalized.ageFromMonths()) {
                throw GlobalException.of(ApiCode.AGE_RANGE_INVALID);
            }
            // Cần chốt 19/11/2026: sửa ở năm nào chỉ năm đó đổi; tên, độ tuổi không trùng trong cùng năm
            List<AgeGroupDetailResponse> inYear = yearDetails(schoolYearId);
            checkNameInYear(inYear, normalized.name().trim(), ageGroupId);
            checkOverlapInYear(inYear, normalized.ageFromMonths(), normalized.ageToMonths(), ageGroupId);
            checkMaxClassSize(ageGroupId, normalized.maxClassSize(), schoolYearId);
            saveYearInfo(year, ageGroup, normalized);
            ageGroupRepository.flush();
            AgeGroupDetailResponse response = detailInYear(schoolYearId, ageGroupId);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AgeGroupDetailResponse updateAgeGroup(CurrentUser currentUser, Long ageGroupId, AgeGroupRequest request) {
        String logPrefix = LOG_PREFIX + "|updateAgeGroup|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            // Gọi không kèm năm (client cũ): sửa theo năm đang hoạt động
            var active = schoolYearRepository.findFirstByStatus(SchoolYearStatus.ACTIVE);
            if (active.isPresent() && ageGroupRepository.isInYear(active.get().getId(), ageGroupId)) {
                AgeGroupDetailResponse response = updateAgeGroup(
                        currentUser, ageGroupId, request, active.get().getId());
                log.info("{}|END", logPrefix);
                return response;
            }
            // Nhóm không ở năm đang hoạt động: sửa ở năm sắp diễn ra chứa nhóm; bảng chung (năm khác dùng) giữ nguyên
            SchoolYear planned = plannedYears().stream()
                    .filter(y -> ageGroupRepository.isInYear(y.getId(), ageGroupId))
                    .findFirst()
                    .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
            AgeGroupDetailResponse response = updateAgeGroup(currentUser, ageGroupId, request, planned.getId());
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public void deleteAgeGroup(CurrentUser currentUser, Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|deleteAgeGroup|userId=" + currentUser.userId() + "|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            AgeGroup ageGroup = ageGroupRepository
                    .findById(ageGroupId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
            // Nhóm chuẩn của Bộ là danh mục dùng chung: không xóa qua API này
            if (ageGroup.isStandardGroup()) {
                throw GlobalException.of(ApiCode.AGE_GROUP_HAS_DEPENDENTS, "danh mục chuẩn của Bộ GD&ĐT");
            }
            // Nhóm tuổi dùng chung mọi năm học: xóa là mất ở cả năm khác, nên lỗi nêu rõ năm học và người đang dùng
            if (classroomRepository.countByAgeGroupId(ageGroupId) > 0) {
                throw GlobalException.of(
                        ApiCode.AGE_GROUP_HAS_DEPENDENTS,
                        "lớp"
                                + inYear(ageGroupRepository
                                        .findLatestClassYearId(ageGroupId)
                                        .orElse(null)));
            }
            var leader = ageGroupRepository.findLatestLeader(ageGroupId);
            if (leader.isPresent()) {
                String name = fullName(leader.get().getUserId());
                throw GlobalException.of(
                        ApiCode.AGE_GROUP_HAS_DEPENDENTS,
                        "tổ trưởng" + (name == null ? "" : " " + name)
                                + inYear(leader.get().getSchoolYearId()));
            }
            try {
                ageGroupRepository.delete(ageGroup);
                ageGroupRepository.flush();
            } catch (DataIntegrityViolationException integrityEx) {
                // Kế hoạch giáo dục, kết quả mong đợi... còn tham chiếu nhóm tuổi này
                throw GlobalException.withCause(ApiCode.AGE_GROUP_HAS_DEPENDENTS, integrityEx, "dữ liệu liên quan");
            }
            log.info("{}|END", logPrefix);
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private String fullName(Long userId) {
        return roomRepository.findUserNames(Set.of(userId)).stream()
                .map(RoomRepository.UserName::getFullName)
                .findFirst()
                .orElse(null);
    }

    // Năm sắp diễn ra, sớm nhất trước
    private List<SchoolYear> plannedYears() {
        return schoolYearRepository.findAllByOrderByStartDateDesc().stream()
                .filter(y -> y.getStatus() == SchoolYearStatus.PLANNED)
                .sorted(Comparator.comparing(SchoolYear::getStartDate))
                .toList();
    }

    private SchoolYear findYear(Long schoolYearId) {
        return schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
    }

    private SchoolYear openYear(Long schoolYearId) {
        SchoolYear year = findYear(schoolYearId);
        if (year.getStatus() == SchoolYearStatus.CLOSED) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_CLOSED, year.getName());
        }
        return year;
    }

    // V148/V154 đã backfill mọi năm còn mở. Danh sách rỗng vì vậy là trạng thái hợp lệ
    // (HT đã bỏ hết nhóm khỏi năm), không được tự rơi về toàn bộ danh mục.
    private List<AgeGroup> yearGroups(Long schoolYearId) {
        List<Long> ids = ageGroupRepository.findYearAgeGroupIds(schoolYearId);
        return ids.isEmpty() ? List.of() : ageGroupRepository.findAllById(ids);
    }

    // Nhóm tuổi của năm kèm thông tin riêng của năm (V154), theo độ tuổi
    private List<AgeGroupDetailResponse> yearDetails(Long schoolYearId) {
        var info = YearAgeGroups.infoOf(ageGroupRepository, schoolYearId);
        return yearGroups(schoolYearId).stream()
                .map(schoolStructureMapper::toDetailResponse)
                .map(g -> YearAgeGroups.overlay(g, info))
                .sorted(Comparator.comparing((AgeGroupDetailResponse g) ->
                                g.ageFromMonths() == null ? Integer.MAX_VALUE : g.ageFromMonths())
                        .thenComparing(AgeGroupDetailResponse::sortOrder))
                .toList();
    }

    private AgeGroupDetailResponse detailInYear(Long schoolYearId, Long ageGroupId) {
        return yearDetails(schoolYearId).stream()
                .filter(g -> g.id().equals(ageGroupId))
                .findFirst()
                .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
    }

    // Lưu thông tin cho năm; năm đang hoạt động thì cả bảng chung (module khác đọc bảng chung)
    private void saveYearInfo(SchoolYear year, AgeGroup ageGroup, AgeGroupRequest request) {
        ageGroupRepository.updateYearInfo(
                year.getId(),
                ageGroup.getId(),
                request.name().trim(),
                request.shortName().trim(),
                request.ageFromMonths().shortValue(),
                request.ageToMonths().shortValue(),
                YearAgeGroups.joinSessions(request.mealSessions()),
                request.maxClassSize().shortValue(),
                request.nutritionNote() == null ? "" : request.nutritionNote().trim());
        if (year.getStatus() == SchoolYearStatus.ACTIVE) {
            applyRequest(ageGroup, request);
        }
    }

    private static void checkNameInYear(List<AgeGroupDetailResponse> groups, String name, Long excludeId) {
        if (groups.stream()
                .anyMatch(g -> !g.id().equals(excludeId)
                        && g.name() != null
                        && g.name().equalsIgnoreCase(name))) {
            throw GlobalException.of(ApiCode.AGE_GROUP_NAME_EXISTS);
        }
    }

    private static void checkOverlapInYear(List<AgeGroupDetailResponse> groups, int from, int to, Long excludeId) {
        // Khoảng [from, to): hai nhóm trùng khi from1 < to2 và from2 < to1
        groups.stream()
                .filter(g -> excludeId == null || !g.id().equals(excludeId))
                .filter(g -> g.ageFromMonths() != null && g.ageToMonths() != null)
                .filter(g -> from < g.ageToMonths() && g.ageFromMonths() < to)
                .findFirst()
                .ifPresent(g -> {
                    throw GlobalException.of(
                            ApiCode.AGE_RANGE_OVERLAP,
                            g.shortName() + " (" + g.ageFromMonths() + "–" + g.ageToMonths() + " tháng)");
                });
    }

    private AgeGroup standardGroup(Long standardAgeGroupId) {
        if (standardAgeGroupId == null) {
            throw GlobalException.of(ApiCode.AGE_GROUP_STANDARD_REQUIRED);
        }
        AgeGroup group = ageGroupRepository
                .findById(standardAgeGroupId)
                .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_STANDARD_INVALID));
        if (!group.isStandardGroup()
                || group.getAgeFromMonths() == null
                || group.getAgeToMonths() == null
                || group.getMaxClassSize() == null) {
            throw GlobalException.of(ApiCode.AGE_GROUP_STANDARD_INVALID);
        }
        return group;
    }

    private static AgeGroupRequest standardRequest(AgeGroup standard, AgeGroupRequest request) {
        int legalMax = standard.getMaxClassSize();
        if (request.maxClassSize() > legalMax) {
            throw GlobalException.of(ApiCode.AGE_GROUP_MAX_EXCEEDS_STANDARD, standard.getName(), legalMax);
        }
        return new AgeGroupRequest(
                standard.getName(),
                standard.getShortName(),
                standard.getAgeFromMonths().intValue(),
                standard.getAgeToMonths().intValue(),
                request.mealSessions(),
                request.maxClassSize(),
                request.nutritionNote(),
                standard.getId());
    }

    private String inYear(Long schoolYearId) {
        return schoolYearId == null
                ? ""
                : schoolYearRepository
                        .findById(schoolYearId)
                        .map(y -> " ở năm học " + y.getName())
                        .orElse("");
    }

    private void validate(AgeGroupRequest request, Long excludeId) {
        if (request.ageToMonths() <= request.ageFromMonths()) {
            throw GlobalException.of(ApiCode.AGE_RANGE_INVALID);
        }
        if (ageGroupRepository.existsName(request.name().trim(), excludeId)) {
            throw GlobalException.of(ApiCode.AGE_GROUP_NAME_EXISTS);
        }
        // Khoảng [from, to): hai nhóm trùng khi from1 < to2 và from2 < to1
        ageGroupRepository.findAll().stream()
                .filter(g -> excludeId == null || !g.getId().equals(excludeId))
                .filter(g -> g.getAgeFromMonths() != null && g.getAgeToMonths() != null)
                .filter(g ->
                        request.ageFromMonths() < g.getAgeToMonths() && g.getAgeFromMonths() < request.ageToMonths())
                .findFirst()
                .ifPresent(g -> {
                    throw GlobalException.of(
                            ApiCode.AGE_RANGE_OVERLAP,
                            g.getShortName() + " (" + g.getAgeFromMonths() + "–" + g.getAgeToMonths() + " tháng)");
                });
    }

    // Không hạ sĩ số tối đa xuống dưới sĩ số/số trẻ của lớp năm đó thuộc nhóm tuổi
    private void checkMaxClassSize(Long ageGroupId, int maxClassSize, Long schoolYearId) {
        List<Classroom> classes = classroomRepository.findOpenYearClassesByAgeGroupId(ageGroupId).stream()
                .filter(c -> schoolYearId == null || schoolYearId.equals(c.getSchoolYearId()))
                .toList();
        if (classes.isEmpty()) {
            return;
        }
        Map<Long, Long> childCounts = classroomRepository
                .countActiveChildren(classes.stream().map(Classroom::getId).toList(), schoolConfigAccess.today())
                .stream()
                .collect(Collectors.toMap(
                        ClassroomRepository.ClassroomCount::getClassroomId,
                        ClassroomRepository.ClassroomCount::getTotal));
        String over = classes.stream()
                .filter(c ->
                        Math.max(c.getCapacity() == null ? 0 : c.getCapacity(), childCounts.getOrDefault(c.getId(), 0L))
                                > maxClassSize)
                .map(Classroom::getName)
                .collect(Collectors.joining(", "));
        if (!over.isEmpty()) {
            throw GlobalException.of(ApiCode.AGE_GROUP_MAX_SIZE_CONFLICT, over, maxClassSize);
        }
    }

    private static void applyRequest(AgeGroup ageGroup, AgeGroupRequest request) {
        ageGroup.changeInfo(
                request.name().trim(),
                request.shortName().trim(),
                request.ageFromMonths().shortValue(),
                request.ageToMonths().shortValue(),
                request.mealSessions(),
                request.maxClassSize().shortValue(),
                request.nutritionNote() == null ? "" : request.nutritionNote().trim());
    }

    private String nextCode(short sortOrder) {
        String code = "ag-" + sortOrder;
        int suffix = 1;
        while (ageGroupRepository.existsByCode(code)) {
            code = "ag-" + sortOrder + "-" + suffix++;
        }
        return code;
    }

    private static Comparator<AgeGroup> ageOrder() {
        return Comparator.comparing(
                        (AgeGroup g) -> g.getAgeFromMonths() == null ? Short.MAX_VALUE : g.getAgeFromMonths())
                .thenComparing(AgeGroup::getSortOrder);
    }
}
