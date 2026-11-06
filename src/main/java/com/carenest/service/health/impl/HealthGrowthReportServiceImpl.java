package com.carenest.service.health.impl;

import com.carenest.dto.child.ChildBriefResponse;
import com.carenest.dto.health.HealthGrowthReportResponse;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.CampusOptionResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.health.HealthCheckAbsence;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.entity.health.HealthMeasurement;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckAbsenceRepository;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.child.ChildService;
import com.carenest.service.health.HealthGrowthReportService;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.CampusService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CampusStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.HealthExamStatus;
import com.carenest.utils.enums.HealthGrowthLevel;
import com.carenest.utils.enums.HeightForAgeStatus;
import com.carenest.utils.enums.WeightForAgeStatus;
import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

/**
 * Báo cáo tăng trưởng theo tháng, mẫu 5.1 (HLT-20): trẻ theo danh sách lớp của đợt/kỳ có ngày bắt đầu trong tháng, xếp
 * cân nặng/tuổi, chiều cao/tuổi, BMI/tuổi chuẩn WHO từ lần đo được tính mới nhất. Tính khi đọc, không lưu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthGrowthReportServiceImpl implements HealthGrowthReportService {
    private static final String LOG_PREFIX = "[HealthGrowthReportServiceImpl]";
    private static final Collator VI = Collator.getInstance(Locale.forLanguageTag("vi"));
    // Nhà trẻ là trẻ dưới 3 tuổi: nhóm tuổi bắt đầu dưới 36 tháng thuộc NT; nhóm chưa có tháng thì theo mã mặc định
    private static final int PRESCHOOL_FROM_MONTHS = 36;
    private static final Set<String> NURSERY_CODES = Set.of("ag-2");
    private static final Set<String> PRESCHOOL_CODES = Set.of("ag-3", "ag-4", "ag-5");

    private final HealthCheckRules rules;
    private final HealthCheckCampaignRepository campaignRepository;
    private final HealthMeasurementRepository measurementRepository;
    private final HealthCheckAbsenceRepository absenceRepository;
    private final SchoolStructureService schoolStructureService;
    private final AgeGroupService ageGroupService;
    private final ChildService childService;
    private final CampusService campusService;

    /** Một dòng trẻ trong danh sách lớp của một đợt/kỳ. */
    private record Entry(HealthCheckCampaign campaign, HealthCheckRules.Row row) {}

    /** Lớp của báo cáo kèm cấp học, nhóm tuổi và số đếm. */
    private record Line(
            ClassroomInfoResponse cls, AgeGroupDetailResponse group, HealthGrowthLevel level, Tally tally) {}

    @Override
    public HealthGrowthReportResponse getGrowthReport(CurrentUser currentUser, YearMonth month, Long campusId) {
        String logPrefix = LOG_PREFIX + "|getGrowthReport|userId=" + currentUser.userId() + "|month=" + month
                + "|campusId=" + campusId;
        log.info("{}|START", logPrefix);
        try {
            // HT mọi điểm trường, PHT điểm trường được phân công (AUTH-02); vai trò khác không xem
            if (!currentUser.hasRole(CareNestRole.PRINCIPAL) && !currentUser.hasRole(CareNestRole.VICE_PRINCIPAL)) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_ACCESS_DENIED);
            }
            if (month == null) {
                throw GlobalException.of(ApiCode.REQUIRED_FIELD_MISSING, "tháng");
            }
            if (campusId != null && !rules.canManage(currentUser, campusId)) {
                throw GlobalException.of(ApiCode.HEALTH_CHECK_ACCESS_DENIED);
            }
            List<CampusOptionResponse> inScope = schoolStructureService.listCampuses(currentUser).stream()
                    .filter(c -> campusId == null || c.id().equals(campusId))
                    .filter(c -> rules.canManage(currentUser, c.id()))
                    .toList();
            if (campusId != null && inScope.isEmpty()) {
                throw GlobalException.of(ApiCode.CAMPUS_NOT_FOUND);
            }
            Map<Long, List<HealthCheckCampaign>> campaignsByCampus = campaignsOf(inScope, month);
            // Điểm trường ngừng hoạt động chỉ còn dữ liệu cũ: hiện khi tháng có đợt/kỳ, hoặc khi lọc đúng điểm trường
            // đó
            List<CampusOptionResponse> campuses = inScope.stream()
                    .filter(c -> campusId != null
                            || campaignsByCampus.containsKey(c.id())
                            || campusService.findCampusStatus(c.id()).orElse(CampusStatus.ACTIVE)
                                    != CampusStatus.INACTIVE)
                    .toList();
            List<Long> campaignIds = campaignsByCampus.values().stream()
                    .flatMap(List::stream)
                    .map(HealthCheckCampaign::getId)
                    .toList();
            List<HealthMeasurement> measurements =
                    campaignIds.isEmpty() ? List.of() : measurementRepository.findByCampaignIdIn(campaignIds);
            List<HealthCheckAbsence> absences =
                    campaignIds.isEmpty() ? List.of() : absenceRepository.findByCampaignIdIn(campaignIds);

            // Danh sách lớp như màn đợt/kỳ, hợp theo trẻ trong từng điểm trường
            Map<Long, Map<Long, ClassroomInfoResponse>> classesByCampus = new LinkedHashMap<>();
            Map<Long, Map<Long, List<Entry>>> entriesByCampus = new LinkedHashMap<>();
            for (CampusOptionResponse campus : campuses) {
                Map<Long, ClassroomInfoResponse> classes = new LinkedHashMap<>();
                Map<Long, List<Entry>> entries = new LinkedHashMap<>();
                for (HealthCheckCampaign campaign : campaignsByCampus.getOrDefault(campus.id(), List.of())) {
                    for (ClassroomInfoResponse cls : rules.classesOf(campaign)) {
                        classes.putIfAbsent(cls.id(), cls);
                        for (HealthCheckRules.Row row : rules.rosterOf(campaign, cls, measurements, absences)) {
                            entries.computeIfAbsent(row.childId(), id -> new ArrayList<>())
                                    .add(new Entry(campaign, row));
                        }
                    }
                }
                classesByCampus.put(campus.id(), classes);
                entriesByCampus.put(campus.id(), entries);
            }
            Set<Long> childIds = new HashSet<>();
            entriesByCampus.values().forEach(e -> childIds.addAll(e.keySet()));
            Map<Long, ChildBriefResponse> children =
                    childIds.isEmpty() ? Map.of() : childService.findChildBriefs(childIds);
            Map<Long, AgeGroupDetailResponse> groups = ageGroupService.getAgeGroups().stream()
                    .collect(Collectors.toMap(AgeGroupDetailResponse::id, Function.identity()));

            List<HealthGrowthReportResponse.HealthGrowthCampus> blocks = new ArrayList<>();
            Tally preschool = new Tally();
            Tally nursery = new Tally();
            Tally total = new Tally();
            for (CampusOptionResponse campus : campuses) {
                List<Line> lines =
                        lines(classesByCampus.get(campus.id()), entriesByCampus.get(campus.id()), children, groups);
                Tally campusPreschool = sum(lines, HealthGrowthLevel.PRESCHOOL);
                Tally campusNursery = sum(lines, HealthGrowthLevel.NURSERY);
                Tally campusTotal = new Tally();
                lines.forEach(l -> campusTotal.add(l.tally()));
                preschool.add(campusPreschool);
                nursery.add(campusNursery);
                total.add(campusTotal);
                blocks.add(new HealthGrowthReportResponse.HealthGrowthCampus(
                        campus.id(),
                        campus.name(),
                        campaignsByCampus.getOrDefault(campus.id(), List.of()).stream()
                                .map(c -> new HealthGrowthReportResponse.HealthGrowthCampaign(
                                        c.getId(),
                                        c.getName(),
                                        c.getKind(),
                                        c.statusOn(rules.today()),
                                        c.getStartDate(),
                                        c.getEndDate()))
                                .toList(),
                        lines.stream()
                                .map(l -> new HealthGrowthReportResponse.HealthGrowthClass(
                                        l.cls().id(),
                                        l.cls().name(),
                                        l.group() == null ? null : l.group().name(),
                                        l.level(),
                                        l.tally().counts()))
                                .toList(),
                        campusPreschool.counts(),
                        campusNursery.counts(),
                        campusTotal.counts()));
            }
            HealthGrowthReportResponse report = new HealthGrowthReportResponse(
                    month.toString(), blocks, preschool.counts(), nursery.counts(), total.counts());
            log.info(
                    "{}|campuses={}|campaigns={}|children={}|END",
                    logPrefix,
                    campuses.size(),
                    campaignIds.size(),
                    childIds.size());
            return report;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }

    /** Đợt khám, kỳ cân đo chưa hủy có ngày bắt đầu trong tháng, theo ngày bắt đầu rồi id. */
    private Map<Long, List<HealthCheckCampaign>> campaignsOf(List<CampusOptionResponse> campuses, YearMonth month) {
        if (campuses.isEmpty()) {
            return Map.of();
        }
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        return campaignRepository
                .findByCampusIdInOrderByStartDateDescIdDesc(
                        campuses.stream().map(CampusOptionResponse::id).toList())
                .stream()
                .filter(c -> c.getCancelledAt() == null)
                .filter(c ->
                        !c.getStartDate().isBefore(first) && !c.getStartDate().isAfter(last))
                .sorted(Comparator.comparing(HealthCheckCampaign::getStartDate)
                        .thenComparing(HealthCheckCampaign::getId))
                .collect(Collectors.groupingBy(
                        HealthCheckCampaign::getCampusId, LinkedHashMap::new, Collectors.toList()));
    }

    /** Mỗi trẻ một lần: lớp của lần đo được tính mới nhất; chưa đo thì lớp ở đợt/kỳ mới nhất có trẻ. */
    private static List<Line> lines(
            Map<Long, ClassroomInfoResponse> classes,
            Map<Long, List<Entry>> entries,
            Map<Long, ChildBriefResponse> children,
            Map<Long, AgeGroupDetailResponse> groups) {
        Map<Long, Tally> tallies = new LinkedHashMap<>();
        classes.keySet().forEach(id -> tallies.put(id, new Tally()));
        for (Map.Entry<Long, List<Entry>> child : entries.entrySet()) {
            List<Entry> rows = child.getValue();
            Entry chosen = rows.stream()
                    .filter(e -> counted(e.row().measurement()))
                    .max(Comparator.comparing((Entry e) -> e.row().measurement().getDate())
                            .thenComparing(e -> e.row().measurement().getId()))
                    .orElseGet(() -> rows.stream()
                            .max(Comparator.comparing((Entry e) -> e.campaign().getStartDate())
                                    .thenComparing(e -> e.campaign().getId())
                                    .thenComparing(e -> e.row().measurement() != null))
                            .orElseThrow());
            Tally tally = tallies.computeIfAbsent(chosen.row().classId(), id -> new Tally());
            tally.enrolled++;
            HealthMeasurement record = chosen.row().measurement();
            if (counted(record)) {
                tally.count(record, children.get(child.getKey()));
            }
        }
        return classes.values().stream()
                .map(cls -> {
                    AgeGroupDetailResponse group = cls.ageGroupId() == null ? null : groups.get(cls.ageGroupId());
                    return new Line(cls, group, levelOf(group), tallies.get(cls.id()));
                })
                // MG theo nhóm tuổi lớn trước (5 tuổi, 4 tuổi, 3 tuổi), rồi NT, rồi lớp chưa có nhóm tuổi
                .sorted(Comparator.comparingInt((Line l) -> levelRank(l.level()))
                        .thenComparingInt(l -> l.level() == HealthGrowthLevel.PRESCHOOL ? -sortOrder(l.group()) : 0)
                        .thenComparing(l -> l.cls().name(), VI)
                        .thenComparing(l -> l.cls().id()))
                .toList();
    }

    /** Được cân đo: số đo kỳ cân đo, hoặc phiếu khám đã kết luận; phải có chiều cao, cân nặng (nháp không tính). */
    private static boolean counted(HealthMeasurement m) {
        return m != null
                && m.getHeightCm() != null
                && m.getWeightKg() != null
                && (!m.isExam() || m.getExamStatus() == HealthExamStatus.CONCLUDED);
    }

    static HealthGrowthLevel levelOf(AgeGroupDetailResponse group) {
        if (group == null) {
            return null;
        }
        if (group.ageFromMonths() != null) {
            return group.ageFromMonths() < PRESCHOOL_FROM_MONTHS
                    ? HealthGrowthLevel.NURSERY
                    : HealthGrowthLevel.PRESCHOOL;
        }
        String code = group.code() == null ? "" : group.code().trim().toLowerCase(Locale.ROOT);
        if (NURSERY_CODES.contains(code)) {
            return HealthGrowthLevel.NURSERY;
        }
        return PRESCHOOL_CODES.contains(code) ? HealthGrowthLevel.PRESCHOOL : null;
    }

    private static int levelRank(HealthGrowthLevel level) {
        if (level == HealthGrowthLevel.PRESCHOOL) {
            return 0;
        }
        return level == HealthGrowthLevel.NURSERY ? 1 : 2;
    }

    private static int sortOrder(AgeGroupDetailResponse group) {
        return group == null || group.sortOrder() == null ? 0 : group.sortOrder();
    }

    private static Tally sum(List<Line> lines, HealthGrowthLevel level) {
        Tally t = new Tally();
        lines.stream().filter(l -> l.level() == level).forEach(l -> t.add(l.tally()));
        return t;
    }

    /** Số đếm cộng dồn của một lớp/nhóm. */
    private static final class Tally {
        private int enrolled;
        private int measured;
        private int weightNormal;
        private int underweight;
        private int heightNormal;
        private int stunted;
        private int bmiNormal;
        private int wasted;
        private int overweight;
        private int obese;
        private int unclassified;

        // Tuổi tháng tròn tại ngày đo; chỉ số nào không xếp được thì trẻ tính là chưa xếp được
        private void count(HealthMeasurement m, ChildBriefResponse child) {
            measured++;
            Gender gender = child == null ? null : child.gender();
            Integer age = child == null || child.dateOfBirth() == null
                    ? null
                    : HealthRecords.ageMonths(child.dateOfBirth(), m.getDate());
            WhoWeightForAge.Result weight = age == null ? null : WhoWeightForAge.classify(gender, age, m.getWeightKg());
            WhoHeightForAge.Result height = age == null ? null : WhoHeightForAge.classify(gender, age, m.getHeightCm());
            WhoBmiForAge.Result bmi =
                    age == null ? null : WhoBmiForAge.classify(gender, age, m.getHeightCm(), m.getWeightKg());
            if (weight == null || height == null || bmi == null) {
                unclassified++;
            }
            if (weight != null) {
                if (weight.status() == WeightForAgeStatus.NORMAL) {
                    weightNormal++;
                } else {
                    underweight++;
                }
            }
            if (height != null) {
                if (height.status() == HeightForAgeStatus.NORMAL) {
                    heightNormal++;
                } else {
                    stunted++;
                }
            }
            if (bmi != null) {
                // Mẫu không có cột nguy cơ thừa cân: tính vào bình thường
                switch (bmi.status()) {
                    case NORMAL, OVERWEIGHT_RISK -> bmiNormal++;
                    case THINNESS, SEVERE_THINNESS -> wasted++;
                    case OVERWEIGHT -> overweight++;
                    case OBESE -> obese++;
                }
            }
        }

        private void add(Tally o) {
            enrolled += o.enrolled;
            measured += o.measured;
            weightNormal += o.weightNormal;
            underweight += o.underweight;
            heightNormal += o.heightNormal;
            stunted += o.stunted;
            bmiNormal += o.bmiNormal;
            wasted += o.wasted;
            overweight += o.overweight;
            obese += o.obese;
            unclassified += o.unclassified;
        }

        private HealthGrowthReportResponse.HealthGrowthCounts counts() {
            return new HealthGrowthReportResponse.HealthGrowthCounts(
                    enrolled,
                    measured,
                    weightNormal,
                    underweight,
                    heightNormal,
                    stunted,
                    bmiNormal,
                    wasted,
                    overweight,
                    obese,
                    unclassified);
        }
    }
}
