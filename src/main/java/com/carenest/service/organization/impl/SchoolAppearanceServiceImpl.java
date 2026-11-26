package com.carenest.service.organization.impl;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.organization.AppearanceRequest;
import com.carenest.dto.organization.AppearanceResponse;
import com.carenest.entity.organization.SchoolAppearance;
import com.carenest.exception.GlobalException;
import com.carenest.repository.organization.SchoolAppearanceRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.SchoolAppearanceService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AppearanceMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SchoolAppearanceServiceImpl implements SchoolAppearanceService {
    private static final String LOG_PREFIX = "[SchoolAppearanceServiceImpl]";

    // Mã tranh của Web (CareNest_FE src/models/appearance/scenes.js); thêm tranh mới thì thêm mã ở đây
    static final List<String> SCENE_IDS = List.of(
            "T0", "T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10", "T11", "T12", "T13", "T14", "T15", "T16",
            "S1", "S3", "S8");
    private static final String DEFAULT_SCENE_ID = "T0";

    private final SchoolAppearanceRepository schoolAppearanceRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AppearanceResponse getAppearance() {
        String logPrefix = LOG_PREFIX + "|getAppearance";
        log.info("{}|START", logPrefix);
        try {
            AppearanceResponse response = schoolAppearanceRepository
                    .findById(SchoolAppearance.SINGLETON_ID)
                    .map(this::toResponse)
                    .orElseGet(SchoolAppearanceServiceImpl::defaultResponse);
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public AppearanceResponse saveAppearance(CurrentUser currentUser, AppearanceRequest request) {
        String logPrefix = LOG_PREFIX + "|saveAppearance|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            List<String> enabled = new LinkedHashSet<>(request.enabledSceneIds())
                    .stream().filter(SCENE_IDS::contains).toList();
            if (enabled.isEmpty()) {
                throw invalid("enabledSceneIds", "Cần bật ít nhất một tranh");
            }
            String pinned = request.pinnedSceneId();
            if (request.mode() == AppearanceMode.FIXED && (pinned == null || !enabled.contains(pinned))) {
                throw invalid("pinnedSceneId", "Chọn một tranh đang bật để ghim");
            }
            if (pinned == null || !SCENE_IDS.contains(pinned)) {
                pinned = enabled.getFirst();
            }
            SchoolAppearance row = schoolAppearanceRepository
                    .findById(SchoolAppearance.SINGLETON_ID)
                    .orElseGet(SchoolAppearance::create);
            row.apply(request.mode(), pinned, enabled, currentUser.userId(), Instant.now(clock));
            AppearanceResponse response = toResponse(schoolAppearanceRepository.saveAndFlush(row));
            log.info("{}|END", logPrefix);
            return response;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static GlobalException invalid(String field, String message) {
        return GlobalException.withFieldErrors(
                ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse(field, message)));
    }

    private static AppearanceResponse defaultResponse() {
        return new AppearanceResponse(AppearanceMode.AUTO, DEFAULT_SCENE_ID, SCENE_IDS, null, null);
    }

    private AppearanceResponse toResponse(SchoolAppearance row) {
        String updatedBy = row.getUpdatedBy() == null
                ? null
                : schoolAppearanceRepository
                        .findUserFullName(row.getUpdatedBy())
                        .orElse(null);
        return new AppearanceResponse(
                row.getMode(), row.getPinnedSceneId(), row.getEnabledSceneIds(), updatedBy, row.getUpdatedAt());
    }
}
