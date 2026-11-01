package com.carenest.service.organization.impl;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.AgeGroupRequest;
import com.carenest.entity.organization.AgeGroup;
import com.carenest.entity.organization.Classroom;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.utils.ApiCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
            if (classroomRepository.countByAgeGroupId(ageGroupId) > 0) {
                throw GlobalException.of(ApiCode.AGE_GROUP_HAS_DEPENDENTS, "lớp");
            }
            if (ageGroupRepository.countStaffAssignments(ageGroupId) > 0) {
                throw GlobalException.of(ApiCode.AGE_GROUP_HAS_DEPENDENTS, "tổ trưởng");
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
