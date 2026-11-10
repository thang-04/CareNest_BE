package com.carenest.service.observation.impl;

import com.carenest.entity.observation.CriteriaOption;
import com.carenest.entity.observation.Observation;
import com.carenest.entity.observation.ObservationCriteria;
import com.carenest.entity.observation.ObservationValue;
import com.carenest.integration.ai.dto.SummaryDraftInput.CriteriaFact;
import com.carenest.integration.ai.dto.SummaryDraftInput.NoteFact;
import com.carenest.integration.ai.dto.SummaryDraftInput.WeeklyCriteriaFact;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tính facts bằng code (nguyên tắc: hệ thống tạo facts, AI chỉ diễn giải). Đếm số ngày theo từng lựa chọn của mỗi
 * tiêu chí, đếm theo tuần, chọn ghi chú gần nhất trong giới hạn để giữ prompt nhỏ.
 */
final class SummaryFactsAggregator {

    static final int MAX_NOTES = 20;
    static final int MAX_NOTE_LENGTH = 300;
    static final String CRITERIA_REF_PREFIX = "CRITERIA-";
    static final String NOTE_REF_PREFIX = "NOTE-";

    private final List<ObservationCriteria> criteria;
    private final Map<Long, CriteriaOption> optionsById;
    private final Map<Long, List<CriteriaOption>> optionsByCriteria;

    SummaryFactsAggregator(List<ObservationCriteria> criteria, List<CriteriaOption> options) {
        this.criteria = criteria.stream()
                .sorted(Comparator.comparingInt(ObservationCriteria::getSortOrder)
                        .thenComparing(ObservationCriteria::getId))
                .toList();
        this.optionsById = options.stream().collect(Collectors.toMap(CriteriaOption::getId, Function.identity()));
        this.optionsByCriteria = options.stream()
                .sorted(Comparator.comparingInt(CriteriaOption::getSortOrder).thenComparing(CriteriaOption::getId))
                .collect(Collectors.groupingBy(CriteriaOption::getCriteriaId, LinkedHashMap::new, Collectors.toList()));
    }

    static int ageInMonths(LocalDate dateOfBirth, LocalDate at) {
        return (int) Period.between(dateOfBirth, at).toTotalMonths();
    }

    /** Chỉ giữ tiêu chí có ít nhất một ghi nhận trong kỳ. */
    List<CriteriaFact> criteriaFacts(List<Observation> observations) {
        List<CriteriaFact> facts = new ArrayList<>();
        for (ObservationCriteria item : criteria) {
            Map<String, Integer> counts = countOptions(item, observations);
            if (counts.values().stream().anyMatch(count -> count > 0)) {
                facts.add(new CriteriaFact(CRITERIA_REF_PREFIX + item.getCode(), item.getName(), counts));
            }
        }
        return facts;
    }

    List<WeeklyCriteriaFact> weeklyFacts(List<Observation> observations) {
        Map<LocalDate, List<Observation>> byWeek = observations.stream()
                .collect(Collectors.groupingBy(
                        observation ->
                                observation.getObservedOn().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        TreeMap::new,
                        Collectors.toList()));
        return byWeek.entrySet().stream()
                .map(entry -> new WeeklyCriteriaFact(entry.getKey(), criteriaFacts(entry.getValue())))
                .toList();
    }

    /** Ghi chú gần nhất trước, cắt độ dài; text đã được ẩn danh. */
    static List<NoteFact> noteFacts(List<Observation> observations, NotePseudonymizer pseudonymizer) {
        return observations.stream()
                .filter(observation ->
                        observation.getNote() != null && !observation.getNote().isBlank())
                .sorted(Comparator.comparing(Observation::getObservedOn).reversed())
                .limit(MAX_NOTES)
                .map(observation -> new NoteFact(
                        NOTE_REF_PREFIX + observation.getId(),
                        observation.getObservedOn(),
                        truncate(pseudonymizer.apply(observation.getNote()))))
                .toList();
    }

    /**
     * Tiêu chí cần chú ý: điểm trung bình thấp hơn điểm giữa thang của tiêu chí đó. Dùng để dựng truy vấn RAG,
     * không đưa ra kết luận nào cho GV.
     */
    List<String> lowScoringCriteriaNames(List<Observation> observations) {
        List<String> names = new ArrayList<>();
        for (ObservationCriteria item : criteria) {
            List<CriteriaOption> options = optionsByCriteria.getOrDefault(item.getId(), List.of());
            List<Integer> scores = observations.stream()
                    .flatMap(observation -> observation.getValues().stream())
                    .filter(value -> value.getCriteriaId().equals(item.getId()))
                    .map(value -> optionsById.get(value.getOptionId()))
                    .filter(option -> option != null)
                    .map(CriteriaOption::getScore)
                    .toList();
            if (scores.isEmpty() || options.isEmpty()) {
                continue;
            }
            int min = options.stream().mapToInt(CriteriaOption::getScore).min().orElse(0);
            int max = options.stream().mapToInt(CriteriaOption::getScore).max().orElse(0);
            double average =
                    scores.stream().mapToInt(Integer::intValue).average().orElse(max);
            if (average < (min + max) / 2.0) {
                names.add(item.getName());
            }
        }
        return names;
    }

    static String ageGroupLabel(int ageMonths) {
        if (ageMonths < 36) {
            return "24-36 tháng";
        }
        if (ageMonths < 48) {
            return "36-48 tháng";
        }
        return ageMonths < 60 ? "48-60 tháng" : "60-72 tháng";
    }

    private Map<String, Integer> countOptions(ObservationCriteria item, List<Observation> observations) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        optionsByCriteria.getOrDefault(item.getId(), List.of()).forEach(option -> counts.put(option.getLabel(), 0));
        for (Observation observation : observations) {
            for (ObservationValue value : observation.getValues()) {
                CriteriaOption option = optionsById.get(value.getOptionId());
                if (value.getCriteriaId().equals(item.getId()) && option != null) {
                    counts.merge(option.getLabel(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_NOTE_LENGTH ? text : text.substring(0, MAX_NOTE_LENGTH) + "…";
    }
}
