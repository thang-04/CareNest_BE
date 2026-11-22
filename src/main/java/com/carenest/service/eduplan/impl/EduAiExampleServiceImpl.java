package com.carenest.service.eduplan.impl;

import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.organization.AgeGroupResponse;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduTeamPlan;
import com.carenest.repository.eduplan.EduContentThemeTickRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduTeamPlanRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.service.eduplan.EduAiExampleService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.enums.EduPlanStatus;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class EduAiExampleServiceImpl implements EduAiExampleService {
    private static final String LOG_PREFIX = "[EduAiExampleServiceImpl]";
    private final EduTeamPlanRepository plans;
    private final EduThemeRepository themes;
    private final EduPlanGoalRepository goals;
    private final EduGoalContentRepository contents;
    private final EduContentThemeTickRepository ticks;
    private final SchoolStructureService structure;

    private final boolean enabled;

    public EduAiExampleServiceImpl(
            EduTeamPlanRepository plans,
            EduThemeRepository themes,
            EduPlanGoalRepository goals,
            EduGoalContentRepository contents,
            EduContentThemeTickRepository ticks,
            SchoolStructureService structure,
            @Value("${carenest.edu-plan.ai-examples-enabled:false}") boolean enabled) {
        this.plans = plans;
        this.themes = themes;
        this.goals = goals;
        this.contents = contents;
        this.ticks = ticks;
        this.structure = structure;
        this.enabled = enabled;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Example> examples(YearPlanDetailResponse target) {
        String logPrefix = LOG_PREFIX + "|examples|planId=" + target.id();
        log.info("{}|START", logPrefix);
        try {
            List<Example> result = select(target);
            log.info("{}|END|count={}", logPrefix, result.size());
            return result;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    private List<Example> select(YearPlanDetailResponse target) {
        if (!enabled || target.themes() == null || target.themes().isEmpty()) return List.of();
        Map<Long, AgeGroupResponse> ages = structure.findAgeGroups().stream()
                .collect(Collectors.toMap(AgeGroupResponse::id, Function.identity()));
        List<EduTeamPlan> sources = new ArrayList<>(plans.findApprovedForExamples());
        sources.removeIf(p -> p.isDisabled() || p.getStatus() != EduPlanStatus.APPROVED);
        sources.sort(Comparator.comparingInt(p -> sameAge(target.ageGroupId(), p.getAgeGroupId(), ages) ? 0 : 1));
        List<Example> result = new ArrayList<>();
        Map<Long, Integer> count = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (EduTeamPlan source : sources) {
            if (result.size() >= 60) break;
            var sourceThemes = themes.findAllByYearPlanIdOrderBySortOrderAsc(source.getId());
            var relevant = sourceThemes.stream().filter(t -> target.themes().stream()
                    .anyMatch(wanted -> near(wanted.name(), t.getName()))).toList();
            if (relevant.isEmpty()) continue;
            Map<Long, EduPlanGoal> rows = goals.findAllByYearPlanIdOrderBySortOrderAsc(source.getId()).stream()
                    .collect(Collectors.toMap(EduPlanGoal::getId, Function.identity()));
            if (rows.isEmpty()) continue;
            Map<Long, EduGoalContent> lines = contents.findAllByGoalRowIdInOrderBySortOrderAsc(rows.keySet()).stream()
                    .collect(Collectors.toMap(EduGoalContent::getId, Function.identity()));
            Map<Long, String> names = relevant.stream().collect(Collectors.toMap(t -> t.getId(), t -> t.getName()));
            for (var tick : ticks.findAllByThemeIdIn(names.keySet()).stream()
                    .sorted(Comparator.comparing(com.carenest.entity.eduplan.EduContentThemeTick::getThemeId)
                            .thenComparing(com.carenest.entity.eduplan.EduContentThemeTick::getContentId)).toList()) {
                EduGoalContent line = lines.get(tick.getContentId());
                if (line == null || line.getText() == null || line.getText().isBlank()) continue;
                EduPlanGoal row = rows.get(line.getGoalRowId());
                if (row == null) continue;
                for (var wanted : target.themes()) {
                    if (!near(wanted.name(), names.get(tick.getThemeId())) || count.getOrDefault(wanted.id(), 0) >= 5
                            || result.size() >= 60) continue;
                    String key = wanted.id() + ":" + row.getDomainId() + ":" + normalized(line.getText());
                    if (!seen.add(key)) continue;
                    result.add(new Example(wanted.id(), row.getDomainId(), names.get(tick.getThemeId()), line.getText()));
                    count.merge(wanted.id(), 1, Integer::sum);
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean sameAge(Long a, Long b, Map<Long, AgeGroupResponse> ages) {
        if (Objects.equals(a, b)) return true;
        AgeGroupResponse x = ages.get(a), y = ages.get(b);
        return x != null && y != null && x.ageFromMonths() != null && x.ageToMonths() != null
                && Objects.equals(x.ageFromMonths(), y.ageFromMonths()) && Objects.equals(x.ageToMonths(), y.ageToMonths());
    }

    static boolean near(String a, String b) {
        String x = normalized(a), y = normalized(b);
        return !x.isBlank() && !y.isBlank() && (x.equals(y) || x.contains(y) || y.contains(x));
    }

    private static String normalized(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
