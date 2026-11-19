package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.EduDurationPolicyRequest;
import com.carenest.dto.eduplan.EduDurationPolicyResponse;
import com.carenest.entity.eduplan.EduSettings;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduSettingsRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.eduplan.EduDurationPolicyService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class EduDurationPolicyServiceImpl implements EduDurationPolicyService {
    private static final String LOG_PREFIX = "[EduDurationPolicyServiceImpl]";
    private final EduSettingsRepository settingsRepository;
    private final EduPlanAccessPolicy accessPolicy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private record StoredPolicy(EduDurationPolicyRequest.Limits theme, EduDurationPolicyRequest.Limits branch) {}

    @Override
    @Transactional(readOnly = true)
    public EduDurationPolicyResponse getDurationPolicy(CurrentUser user) {
        String logPrefix = LOG_PREFIX + "|getDurationPolicy|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            if (!accessPolicy.hasPermission(user, EduPermission.GOALS_VIEW))
                throw GlobalException.of(ApiCode.EDU_CONFIG_FORBIDDEN);
            EduSettings settings = settingsRepository
                    .findById(EduSettings.SINGLETON_ID)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            EduDurationPolicyResponse result = response(settings, user);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EduDurationPolicyResponse saveDurationPolicy(CurrentUser user, EduDurationPolicyRequest request) {
        String logPrefix = LOG_PREFIX + "|saveDurationPolicy|userId=" + user.userId();
        log.info("{}|START", logPrefix);
        try {
            if (!accessPolicy.canConfigureWorkflow(user)) throw GlobalException.of(ApiCode.EDU_CONFIG_FORBIDDEN);
            validate(request.theme());
            validate(request.branch());
            EduSettings settings =
                    settingsRepository.findForUpdate().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            if (request.revision() == null || request.revision() != settings.getDurationRevision())
                throw GlobalException.of(ApiCode.DURATION_POLICY_STALE);
            String json = objectMapper.writeValueAsString(new StoredPolicy(request.theme(), request.branch()));
            if (!objectMapper.readTree(settings.getDurationPolicy()).equals(objectMapper.readTree(json)))
                settings.updateDurationPolicy(json, user.userId(), clock.instant());
            EduDurationPolicyResponse result = response(settings, user);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EduDurationPolicyResponse getEffectivePolicy(Long schoolYearId, Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|getEffectivePolicy";
        log.info("{}|START", logPrefix);
        try {
            // Chỉ đọc để hiển thị/xem trước: không khóa FOR SHARE (Postgres từ chối trong giao dịch readOnly)
            EduSettings settings = settingsRepository
                    .findById((short) 1)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            EduDurationPolicyResponse result = response(settings, null);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String snapshot() {
        String logPrefix = LOG_PREFIX + "|snapshot";
        log.info("{}|START", logPrefix);
        try {
            EduSettings settings =
                    settingsRepository.findForShare().orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            var node = (tools.jackson.databind.node.ObjectNode) objectMapper.readTree(settings.getDurationPolicy());
            node.put("mode", "BOUNDED");
            node.put("revision", settings.getDurationRevision());
            String result = objectMapper.writeValueAsString(node);
            log.info("{}|END", logPrefix);
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private static void validate(EduDurationPolicyRequest.Limits limits) {
        if (limits == null || limits.minWeeks() < 1 || limits.maxWeeks() < limits.minWeeks())
            throw GlobalException.of(ApiCode.DURATION_POLICY_INVALID);
    }

    private EduDurationPolicyResponse response(EduSettings settings, CurrentUser user) {
        StoredPolicy policy = objectMapper.readValue(settings.getDurationPolicy(), StoredPolicy.class);
        return new EduDurationPolicyResponse(
                settings.getDurationRevision(),
                policy.theme(),
                policy.branch(),
                user != null && accessPolicy.canConfigureWorkflow(user),
                settings.getUpdatedAt());
    }
}
