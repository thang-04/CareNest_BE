package com.carenest.service.health.impl;

import static com.carenest.service.health.impl.HealthExamCatalog.ACTION_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.BEHAVIOR_NOTE;
import static com.carenest.service.health.impl.HealthExamCatalog.CLINICAL_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.DEVELOPMENT_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.FLAG_HEALTH_ISSUE;
import static com.carenest.service.health.impl.HealthExamCatalog.FLAG_NORMAL;
import static com.carenest.service.health.impl.HealthExamCatalog.FLAG_TB_RISK;
import static com.carenest.service.health.impl.HealthExamCatalog.FOLLOW_UP_FINDING_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.HEAD_STATUS_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.HISTORY_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.IMMUNIZATION_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.ISSUE_KIND_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.NO;
import static com.carenest.service.health.impl.HealthExamCatalog.NUTRITION_STATUS_OPTIONS;
import static com.carenest.service.health.impl.HealthExamCatalog.VITAL_ITEMS;
import static com.carenest.service.health.impl.HealthExamCatalog.YES;
import static com.carenest.service.health.impl.HealthExamCatalog.labelOf;

import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamForm.HealthExamAddress;
import com.carenest.dto.health.HealthExamForm.HealthExamAdmin;
import com.carenest.dto.health.HealthExamForm.HealthExamCompanion;
import com.carenest.dto.health.HealthExamForm.HealthExamConclusion;
import com.carenest.dto.health.HealthExamForm.HealthExamDevelopment;
import com.carenest.dto.health.HealthExamForm.HealthExamHistory;
import com.carenest.dto.health.HealthExamForm.HealthExamImmunization;
import com.carenest.dto.health.HealthExamForm.HealthExamNutrition;
import com.carenest.dto.health.HealthMeasurementResponse.HealthMeasurementChange;
import com.carenest.service.health.impl.HealthExamCatalog.ChoiceItem;
import com.carenest.service.health.impl.HealthExamCatalog.HistoryItem;
import com.carenest.service.health.impl.HealthExamCatalog.VitalItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lịch sử sửa phiếu đã kết luận (HLT-13, như {@code diffExam} của mock FE): ô nào đổi giá trị hiển thị thì ghi tên ô tiếng Việt,
 * giá trị cũ, mới. Không so nguồn chi trả và bước đang nhập.
 */
final class HealthExamDiff {

    /** Giá trị một lần lưu: số đo của lần đo cộng phiếu đã chuẩn hóa. */
    record Snapshot(
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            HealthExamForm exam) {}

    private record Entry(String field, String label, String value) {}

    private HealthExamDiff() {}

    /** Rỗng khi không ô nào đổi. */
    static List<HealthMeasurementChange> changes(Snapshot before, Snapshot after) {
        Map<String, String> previous = new HashMap<>();
        entries(before).forEach(e -> previous.put(e.field(), e.value()));
        return entries(after).stream()
                .filter(e -> !Objects.equals(previous.get(e.field()), e.value()))
                .map(e -> new HealthMeasurementChange(e.field(), e.label(), previous.get(e.field()), e.value()))
                .toList();
    }

    /**
     * Giá trị hiển thị như BE giả: Có/Không, nhãn lựa chọn, CCCD che còn 3 số cuối, số kiểu vi-VN, ô trống ⇒ {@code ""}.
     * Ngày khám giữ yyyy-MM-dd vì FE tự đổi sang dd/MM/yyyy cho ô {@code date}; ngày tiếp xúc lao dd/MM/yyyy.
     */
    private static List<Entry> entries(Snapshot snapshot) {
        HealthExamForm e = snapshot.exam() == null ? HealthExamNormalizer.blank() : snapshot.exam();
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(
                "date",
                "Ngày khám",
                snapshot.date() == null ? "" : snapshot.date().toString()));
        out.add(new Entry("heightCm", "Chiều cao/chiều dài (cm)", number(snapshot.heightCm())));
        out.add(new Entry("weightKg", "Cân nặng (kg)", number(snapshot.weightKg())));
        out.add(new Entry("headCircumferenceCm", "Vòng đầu (cm)", number(snapshot.headCircumferenceCm())));
        admin(out, e.admin());
        history(out, e.history());
        for (VitalItem item : VITAL_ITEMS) {
            out.add(new Entry(
                    "exam.vitals." + item.key(),
                    item.label(),
                    number(item.value().apply(e.vitals()))));
            out.add(new Entry(
                    "exam.vitals." + item.statusKey(),
                    item.name() + ": trạng thái",
                    labelOf(item.options(), item.status().apply(e.vitals()))));
        }
        nutrition(out, e.nutrition());
        for (ChoiceItem<HealthExamDevelopment> item : DEVELOPMENT_ITEMS) {
            out.add(new Entry(
                    "exam.development." + item.key(),
                    item.label(),
                    labelOf(item.options(), item.value().apply(e.development()))));
        }
        for (ChoiceItem<HealthExamImmunization> item : IMMUNIZATION_ITEMS) {
            out.add(new Entry(
                    "exam.immunization." + item.key(),
                    "Tiêm chủng — " + item.label(),
                    labelOf(item.options(), item.value().apply(e.immunization()))));
        }
        Map<String, String> clinical = e.clinical() == null ? Map.of() : e.clinical();
        for (ChoiceItem<Map<String, String>> item : CLINICAL_ITEMS) {
            out.add(new Entry(
                    "exam.clinical." + item.key(),
                    item.label(),
                    labelOf(item.options(), item.value().apply(clinical))));
        }
        out.add(new Entry("exam.clinical.behaviorNote", "Ghi rõ (tâm lý, hành vi)", text(clinical.get(BEHAVIOR_NOTE))));
        conclusion(out, e.conclusion());
        return out;
    }

    private static void admin(List<Entry> out, HealthExamAdmin a) {
        HealthExamAddress address = a.address() == null ? new HealthExamAddress(null, null, null) : a.address();
        HealthExamCompanion companion = a.companion() == null ? HealthExamCatalog.blankCompanion() : a.companion();
        // Số định danh không hiện nguyên số trong lịch sử sửa
        out.add(new Entry("exam.admin.childIdNumber", "Số định danh của trẻ", maskId(a.childIdNumber())));
        out.add(new Entry("exam.admin.premature", "Sinh non", yesNo(a.premature())));
        out.add(new Entry(
                "exam.admin.gestationWeeks",
                "Tuần thai khi sinh",
                a.gestationWeeks() == null ? "" : number(BigDecimal.valueOf(a.gestationWeeks()))));
        out.add(new Entry("exam.admin.ethnicity", "Dân tộc", text(a.ethnicity())));
        out.add(new Entry("exam.admin.beneficiary", "Đối tượng", text(a.beneficiary())));
        out.add(new Entry("exam.admin.bloodType", "Nhóm máu", text(a.bloodType())));
        out.add(new Entry(
                "exam.admin.address",
                "Nơi ở hiện tại",
                Stream.of(address.street(), address.ward(), address.province())
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.joining(", "))));
        out.add(new Entry("exam.admin.hasCompanion", "Có người đi cùng", yesNo(a.hasCompanion())));
        out.add(new Entry("exam.admin.companion.fullName", "Người đi cùng", text(companion.fullName())));
        out.add(new Entry("exam.admin.companion.relation", "Quan hệ với trẻ", text(companion.relation())));
        out.add(new Entry("exam.admin.companion.phone", "Số điện thoại liên hệ", text(companion.phone())));
        out.add(new Entry("exam.admin.companion.idNumber", "Số định danh người đi cùng", maskId(companion.idNumber())));
    }

    private static void history(List<Entry> out, HealthExamHistory h) {
        for (HistoryItem item : HISTORY_ITEMS) {
            out.add(new Entry(
                    "exam.history." + item.key(),
                    item.label(),
                    yesNo(item.answer().apply(h))));
            if (item.textKey() != null) {
                out.add(new Entry(
                        "exam.history." + item.textKey(),
                        item.textLabel(),
                        text(item.text().apply(h))));
            }
            if (item.dateKey() != null) {
                out.add(new Entry(
                        "exam.history." + item.dateKey(),
                        item.dateLabel(),
                        HealthExamCatalog.formatDate(h.tbContactDate())));
            }
        }
    }

    private static void nutrition(List<Entry> out, HealthExamNutrition n) {
        List<String> statuses = n.statuses() == null ? List.of() : n.statuses();
        out.add(new Entry("exam.nutrition.lengthForAgeSd", "Chiều dài/Tuổi (SD)", number(n.lengthForAgeSd())));
        out.add(new Entry("exam.nutrition.weightForAgeSd", "Cân nặng/Tuổi (SD)", number(n.weightForAgeSd())));
        out.add(new Entry(
                "exam.nutrition.headStatus", "Vòng đầu: trạng thái", labelOf(HEAD_STATUS_OPTIONS, n.headStatus())));
        out.add(new Entry(
                "exam.nutrition.armCircumferenceMm", "Chu vi vòng cánh tay (mm)", number(n.armCircumferenceMm())));
        out.add(new Entry(
                "exam.nutrition.statuses",
                "Tình trạng dinh dưỡng",
                statuses.stream().map(s -> labelOf(NUTRITION_STATUS_OPTIONS, s)).collect(Collectors.joining(", "))));
    }

    private static void conclusion(List<Entry> out, HealthExamConclusion c) {
        List<String> flags = new ArrayList<>();
        if (c.normal()) {
            flags.add(FLAG_NORMAL);
        }
        if (c.tbRisk()) {
            flags.add(FLAG_TB_RISK);
        }
        if (c.healthIssue()) {
            flags.add(FLAG_HEALTH_ISSUE);
        }
        out.add(new Entry("exam.conclusion.flags", "Kết luận", String.join(", ", flags)));
        out.add(new Entry(
                "exam.conclusion.followUpFinding",
                "Kết luận so với vấn đề đang theo dõi",
                labelOf(FOLLOW_UP_FINDING_OPTIONS, c.followUpFinding())));
        out.add(new Entry("exam.conclusion.issueText", "Vấn đề sức khỏe", text(c.issueText())));
        out.add(new Entry(
                "exam.conclusion.issueKind", "Vấn đề mới hay đã biết", labelOf(ISSUE_KIND_OPTIONS, c.issueKind())));
        out.add(new Entry("exam.conclusion.action", "Hướng xử lý", labelOf(ACTION_OPTIONS, c.action())));
        out.add(new Entry("exam.conclusion.adviceText", "Lời tư vấn", text(c.adviceText())));
        out.add(new Entry("exam.conclusion.specialty", "Chuyên khoa", text(c.specialty())));
        out.add(new Entry("exam.conclusion.doctorName", "Bác sĩ kết luận", text(c.doctorName())));
        out.add(new Entry("exam.conclusion.parentOpinion", "Ý kiến phụ huynh", text(c.parentOpinion())));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    // Số như FE hiện (formatNumber vi-VN, tối đa 1 số lẻ): 98,5; 37; 1.200
    private static String number(BigDecimal value) {
        return value == null ? "" : HealthExamCatalog.formatDecimal(value, 1);
    }

    private static String yesNo(String value) {
        return YES.equals(value) ? "Có" : NO.equals(value) ? "Không" : "";
    }

    private static String maskId(String value) {
        return value == null || value.isEmpty() ? "" : "••••" + value.substring(Math.max(0, value.length() - 3));
    }
}
