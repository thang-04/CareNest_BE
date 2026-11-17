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
    public List<AgeGroupDetailResponse> getYearAgeGroups(Long schoolYearId) {
        String logPrefix = LOG_PREFIX + "|getYearAgeGroups|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            List<AgeGroupDetailResponse> response = yearGroups(schoolYearId).stream()
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
    public AgeGroupDetailResponse createAgeGroup(CurrentUser currentUser, AgeGroupRequest request, Long schoolYearId) {
        String logPrefix =
                LOG_PREFIX + "|createAgeGroup|userId=" + currentUser.userId() + "|schoolYearId=" + schoolYearId;
        log.info("{}|START", logPrefix);
        try {
            schoolConfigAccess.checkPrincipal(currentUser);
            openYear(schoolYearId);
            ensureYearMembers(schoolYearId);
            if (request.ageToMonths() <= request.ageFromMonths()) {
                throw GlobalException.of(ApiCode.AGE_RANGE_INVALID);
            }
            var existing = ageGroupRepository.findFirstByNameIgnoreCase(request.name().trim());
            if (existing.isPresent() && ageGroupRepository.isInYear(schoolYearId, existing.get().getId())) {
                throw GlobalException.of(ApiCode.AGE_GROUP_NAME_EXISTS);
            }
            // Trùng tên nhóm có ở năm khác: dùng lại nhóm đó (giữ mã cho kế hoạch, thực đơn); trùng tuổi chỉ xét năm này
            AgeGroup ageGroup = existing.orElse(null);
            int from = ageGroup == null ? request.ageFromMonths() : ageGroup.getAgeFromMonths();
            int to = ageGroup == null ? request.ageToMonths() : ageGroup.getAgeToMonths();
            checkOverlap(yearGroups(schoolYearId), from, to, ageGroup == null ? null : ageGroup.getId());
            if (ageGroup == null) {
                short sortOrder = (short) (ageGroupRepository.findMaxSortOrder() + 1);
                ageGroup = AgeGroup.create(nextCode(sortOrder), sortOrder);
                applyRequest(ageGroup, request);
                ageGroup = ageGroupRepository.save(ageGroup);
            }
            ageGroupRepository.addToYear(schoolYearId, ageGroup.getId());
            log.info("{}|ageGroupId={}|END", logPrefix, ageGroup.getId());
            return schoolStructureMapper.toDetailResponse(ageGroup);
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
            ensureYearMembers(schoolYearId);
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
            if (sourceYearId == null || ageGroupRepository.findYearAgeGroupIds(sourceYearId).isEmpty()) {
                ageGroupRepository.addAllToYear(targetYearId);
            } else {
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
            short sortOrder = (short) (ageGroupRepository.findMaxSortOrder() + 1);
            AgeGroup ageGroup = AgeGroup.create(nextCode(sortOrder), sortOrder);
            applyRequest(ageGroup, request);
            ageGroup = ageGroupRepository.save(ageGroup);
            // Gọi không kèm năm học (client cũ): nhóm có ở mọi năm còn mở như trước
            ageGroupRepository.addToOpenYears(ageGroup.getId());
            log.info("{}|ageGroupId={}|END", logPrefix, ageGroup.getId());
            return schoolStructureMapper.toDetailResponse(ageGroup);
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
            AgeGroup ageGroup = ageGroupRepository
                    .findById(ageGroupId)
                    .orElseThrow(() -> GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));
            validate(request, ageGroupId);
            checkMaxClassSize(ageGroupId, request.maxClassSize());
            applyRequest(ageGroup, request);
            ageGroupRepository.flush();
            log.info("{}|END", logPrefix);
            return schoolStructureMapper.toDetailResponse(ageGroup);
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
            // Nhóm tuổi dùng chung mọi năm học: xóa là mất ở cả năm khác, nên lỗi nêu rõ năm học và người đang dùng
            if (classroomRepository.countByAgeGroupId(ageGroupId) > 0) {
                throw GlobalException.of(
                        ApiCode.AGE_GROUP_HAS_DEPENDENTS,
                        "lớp" + inYear(ageGroupRepository.findLatestClassYearId(ageGroupId).orElse(null)));
            }
            var leader = ageGroupRepository.findLatestLeader(ageGroupId);
            if (leader.isPresent()) {
                String name = fullName(leader.get().getUserId());
                throw GlobalException.of(
                        ApiCode.AGE_GROUP_HAS_DEPENDENTS,
                        "tổ trưởng" + (name == null ? "" : " " + name) + inYear(leader.get().getSchoolYearId()));
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

    private SchoolYear openYear(Long schoolYearId) {
        SchoolYear year = schoolYearRepository
                .findById(schoolYearId)
                .orElseThrow(() -> GlobalException.of(ApiCode.SCHOOL_YEAR_NOT_FOUND));
        if (year.getStatus() == SchoolYearStatus.CLOSED) {
            throw GlobalException.of(ApiCode.SCHOOL_YEAR_CLOSED, year.getName());
        }
        return year;
    }

    // Năm chưa có danh sách riêng (tạo trước V144 hoặc dữ liệu thử) thấy cả danh mục
    private List<AgeGroup> yearGroups(Long schoolYearId) {
        List<Long> ids = ageGroupRepository.findYearAgeGroupIds(schoolYearId);
        return ids.isEmpty() ? ageGroupRepository.findAll() : ageGroupRepository.findAllById(ids);
    }

    private void ensureYearMembers(Long schoolYearId) {
        if (ageGroupRepository.findYearAgeGroupIds(schoolYearId).isEmpty()) {
            ageGroupRepository.addAllToYear(schoolYearId);
        }
    }

    private static void checkOverlap(List<AgeGroup> groups, int from, int to, Long excludeId) {
        // Khoảng [from, to): hai nhóm trùng khi from1 < to2 và from2 < to1
        groups.stream()
                .filter(g -> excludeId == null || !g.getId().equals(excludeId))
                .filter(g -> g.getAgeFromMonths() != null && g.getAgeToMonths() != null)
                .filter(g -> from < g.getAgeToMonths() && g.getAgeFromMonths() < to)
                .findFirst()
                .ifPresent(g -> {
                    throw GlobalException.of(
                            ApiCode.AGE_RANGE_OVERLAP,
                            g.getShortName() + " (" + g.getAgeFromMonths() + "–" + g.getAgeToMonths()
                                    + " tháng)");
                });
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

    // Không hạ sĩ số tối đa xuống dưới sĩ số/số trẻ của lớp năm chưa kết thúc thuộc nhóm tuổi
    private void checkMaxClassSize(Long ageGroupId, int maxClassSize) {
        List<Classroom> classes = classroomRepository.findOpenYearClassesByAgeGroupId(ageGroupId);
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
