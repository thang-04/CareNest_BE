package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.EduConfigRequest;
import com.carenest.dto.eduplan.EduConfigResponse;
import com.carenest.dto.eduplan.ExpectedResultResponse;
import com.carenest.entity.eduplan.EduDomain;
import com.carenest.entity.eduplan.EduSettings;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.eduplan.EduYearPlanMapper;
import com.carenest.repository.eduplan.EduDomainRepository;
import com.carenest.repository.eduplan.EduExpectedResultRepository;
import com.carenest.repository.eduplan.EduSettingsRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.eduplan.EduConfigService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EduConfigServiceImpl implements EduConfigService {
    private static final String LOG_PREFIX = "[EduConfigServiceImpl]";
    private static final Set<String> SIGNER_ROLES = Set.of("TEAM_LEADER", "VICE_PRINCIPAL", "PRINCIPAL");
    private static final String VICE_PRINCIPAL = "VICE_PRINCIPAL";
    // Kế hoạch ngày lấy môn, đề tài từ dòng này nên không bỏ được
    private static final String LESSON_ROW_KEY = "hoc";
    private static final TypeReference<List<EduConfigResponse.WeekRow>> WEEK_ROWS = new TypeReference<>() {};

    private final EduDomainRepository domainRepository;
    private final EduExpectedResultRepository expectedResultRepository;
    private final EduSettingsRepository settingsRepository;
    private final SchoolStructureService schoolStructureService;
    private final EduPlanAccessPolicy accessPolicy;
    private final EduYearPlanMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public EduConfigResponse getEduConfig() {
        String logPrefix = LOG_PREFIX + "|getEduConfig";
        log.info("{}|START", logPrefix);
        try {
            EduConfigResponse config = toConfig();
            log.info("{}|END", logPrefix);
            return config;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    @Transactional
    public EduConfigResponse updateEduConfig(CurrentUser currentUser, EduConfigRequest request) {
        String logPrefix = LOG_PREFIX + "|updateEduConfig|userId=" + currentUser.userId();
        log.info("{}|START", logPrefix);
        try {
            if (!accessPolicy.hasPermission(currentUser, EduPermission.PLAN_CONFIG)) {
                throw GlobalException.of(ApiCode.EDU_CONFIG_FORBIDDEN);
            }
            EduConfigRequest.Signers signers = request.signers();
            requireSigners(signers);
            List<EduConfigResponse.WeekRow> weekRows = normalizeWeekRows(request.weekRows());
            List<EduDomain> domains = saveDomains(request.extraDomains());
            applyLabels(domains, request.domainLabels());

            EduSettings settings = settingsRepository
                    .findById(EduSettings.SINGLETON_ID)
                    .orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
            settings.update(
                    objectMapper.writeValueAsString(
                            new EduConfigResponse.Signers(signers.year(), signers.branch(), signers.week())),
                    objectMapper.writeValueAsString(weekRows),
                    currentUser.userId(),
                    clock.instant());

            EduConfigResponse config = toConfig();
            log.info("{}|END", logPrefix);
            return config;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override
    public List<ExpectedResultResponse> getExpectedResults(Long ageGroupId) {
        String logPrefix = LOG_PREFIX + "|getExpectedResults|ageGroupId=" + ageGroupId;
        log.info("{}|START", logPrefix);
        try {
            List<ExpectedResultResponse> results =
                    expectedResultRepository.findAllByAgeGroupIdOrderByDomainIdAscSortOrderAsc(ageGroupId).stream()
                            .map(mapper::toExpectedResult)
                            .toList();
            log.info("{}|END", logPrefix);
            return results;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private EduConfigResponse toConfig() {
        EduSettings settings = settingsRepository
                .findById(EduSettings.SINGLETON_ID)
                .orElseThrow(() -> GlobalException.of(ApiCode.CONFIG_MISSING));
        return new EduConfigResponse(
                domainRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                        .map(mapper::toConfigDomain)
                        .toList(),
                schoolStructureService.findSchoolYears(),
                schoolStructureService.findAgeGroups(),
                objectMapper.readValue(settings.getSigners(), EduConfigResponse.Signers.class),
                objectMapper.readValue(settings.getWeekRows(), WEEK_ROWS));
    }

    // EDU-11: kế hoạch năm luôn có Phó HT ký; chủ đề nhánh, hồ sơ chủ đề cần ít nhất một người duyệt
    private static void requireSigners(EduConfigRequest.Signers signers) {
        for (List<String> chain : List.of(signers.year(), signers.branch(), signers.week())) {
            if (!SIGNER_ROLES.containsAll(chain) || new HashSet<>(chain).size() != chain.size()) {
                throw GlobalException.of(ApiCode.SIGNERS_INVALID);
            }
        }
        if (!signers.year().contains(VICE_PRINCIPAL)) {
            throw GlobalException.of(ApiCode.YEAR_PLAN_SIGNER_REQUIRED);
        }
        if (signers.branch().isEmpty() || signers.week().isEmpty()) {
            throw GlobalException.of(ApiCode.CLASS_DOC_APPROVER_REQUIRED);
        }
    }

    private static List<EduConfigResponse.WeekRow> normalizeWeekRows(List<EduConfigRequest.WeekRow> rows) {
        Set<String> keys = new HashSet<>();
        List<EduConfigResponse.WeekRow> result = rows.stream()
                .map(r -> new EduConfigResponse.WeekRow(
                        r.key().trim(),
                        r.label().trim(),
                        // Mặc định cả tuần thì dòng đó cũng được gộp
                        r.canMerge() || r.allWeek(),
                        r.allWeek(),
                        r.extraLabel() == null || r.extraLabel().isBlank()
                                ? null
                                : r.extraLabel().trim()))
                .toList();
        if (!result.stream().allMatch(r -> keys.add(r.key()))) {
            throw GlobalException.of(ApiCode.WEEK_ROW_KEY_DUPLICATE);
        }
        if (!keys.contains(LESSON_ROW_KEY)) {
            throw GlobalException.of(ApiCode.LESSON_ROW_REQUIRED);
        }
        return result;
    }

    /** Lĩnh vực thêm: bật theo thứ tự yêu cầu sau 5 lĩnh vực chuẩn, tạo mới nếu chưa có, ẩn lĩnh vực bị bỏ. */
    private List<EduDomain> saveDomains(List<String> requestedExtras) {
        List<EduDomain> domains = domainRepository.findAllByOrderBySortOrderAsc();
        Set<String> standardNames = domains.stream()
                .filter(EduDomain::isStandard)
                .map(d -> d.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> extras = requestedExtras.stream().map(String::trim).toList();
        Set<String> seen = new HashSet<>();
        for (String name : extras) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (standardNames.contains(lower) || !seen.add(lower)) {
                throw GlobalException.of(ApiCode.DOMAIN_NAME_EXISTS, name);
            }
        }
        Map<String, EduDomain> extraByName = domains.stream()
                .filter(d -> !d.isStandard())
                .collect(Collectors.toMap(EduDomain::getName, Function.identity()));
        short base = (short) (standardNames.size() + 1);
        for (int i = 0; i < extras.size(); i++) {
            short order = (short) (base + i);
            EduDomain existing = extraByName.remove(extras.get(i));
            if (existing == null) {
                domainRepository.save(EduDomain.createExtra(extras.get(i), order));
            } else {
                existing.placeExtra(true, order);
            }
        }
        extraByName.values().forEach(d -> d.placeExtra(false, d.getSortOrder()));
        return domainRepository.findAllByOrderBySortOrderAsc();
    }

    // Tên hiển thị để trống hoặc trùng tên gốc thì dùng lại tên gốc
    private static void applyLabels(List<EduDomain> domains, Map<String, String> labels) {
        for (EduDomain domain : domains) {
            if (!labels.containsKey(domain.getName())) {
                continue;
            }
            String label = labels.get(domain.getName());
            String clean = label == null ? "" : label.trim();
            domain.rename(clean.isEmpty() || clean.equals(domain.getName()) ? null : clean);
        }
    }
}
