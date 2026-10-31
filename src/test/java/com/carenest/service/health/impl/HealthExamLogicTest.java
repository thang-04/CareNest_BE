package com.carenest.service.health.impl;

import static com.carenest.utils.enums.HealthConclusionMode.FOLLOW_UP;
import static com.carenest.utils.enums.HealthConclusionMode.STANDARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.HealthExamForm;
import com.carenest.dto.health.HealthExamForm.HealthExamAddress;
import com.carenest.dto.health.HealthExamForm.HealthExamAdmin;
import com.carenest.dto.health.HealthExamForm.HealthExamCompanion;
import com.carenest.dto.health.HealthExamForm.HealthExamConclusion;
import com.carenest.dto.health.HealthExamForm.HealthExamDevelopment;
import com.carenest.dto.health.HealthExamForm.HealthExamHistory;
import com.carenest.dto.health.HealthExamForm.HealthExamImmunization;
import com.carenest.dto.health.HealthExamForm.HealthExamNutrition;
import com.carenest.dto.health.HealthExamForm.HealthExamVitals;
import com.carenest.dto.health.HealthExamReference;
import com.carenest.dto.health.HealthExamReviewPoints;
import com.carenest.dto.health.HealthExamReviewPoints.HealthExamOtherPoint;
import com.carenest.dto.health.HealthExamReviewPoints.HealthExamVitalPoint;
import com.carenest.dto.health.HealthMeasurementResponse.HealthMeasurementChange;
import com.carenest.utils.enums.HealthConclusionMode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HealthExamLogicTest {

    private static final LocalDate DOB = LocalDate.of(2023, 6, 15);
    // 40 tháng tuổi: nhóm "trẻ 3–5 tuổi", còn đo vòng đầu, không hỏi tự kỷ
    private static final LocalDate EXAM_DATE = LocalDate.of(2026, 10, 20);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final LocalDate CAMPAIGN_START = LocalDate.of(2026, 10, 1);
    private static final LocalDate CAMPAIGN_END = LocalDate.of(2026, 10, 31);
    private static final HealthExamReference AT_40_MONTHS = HealthExamReview.reference(DOB, EXAM_DATE);

    // ---------- Kiểm khi lưu kết luận ----------

    @Test
    @DisplayName("HLT-08: phiếu STANDARD đủ thông tin ⇒ không lỗi")
    void completeStandardFormHasNoErrors() {
        assertThat(new Case().errors()).isEmpty();
    }

    @Test
    @DisplayName("Ngày khám: trống, ở tương lai, ngoài khoảng ngày của đợt")
    void examDateRules() {
        Case c = new Case();
        c.date = null;
        assertThat(c.errors()).containsExactly(entry("date", "Chọn ngày khám"));
        c.date = TODAY.plusDays(1);
        assertThat(c.errors()).containsExactly(entry("date", "Ngày khám không được ở tương lai"));
        c.date = LocalDate.of(2026, 9, 30);
        assertThat(c.errors()).containsExactly(entry("date", "Chọn ngày khám trong khoảng ngày khám của đợt"));
    }

    @Test
    @DisplayName("Sinh tồn: thiếu ⇒ lỗi từng ô theo thứ tự mẫu; ngoài khoảng (sau làm tròn) ⇒ câu có khoảng")
    void vitalsRequiredAndRanged() {
        Case c = new Case();
        c.draft.temperature = null;
        c.draft.temperatureStatus = "";
        c.draft.pulse = null;
        c.draft.respiratory = null;
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.vitals.temperatureC", "Nhập nhiệt độ"),
                        entry("exam.vitals.temperatureStatus", "Chọn trạng thái"),
                        entry("exam.vitals.pulse", "Nhập mạch"),
                        entry("exam.vitals.respiratoryRate", "Nhập nhịp thở"));

        c.draft.temperature = new BigDecimal("42.06");
        c.draft.temperatureStatus = "FEVER";
        c.draft.pulse = new BigDecimal("221");
        c.draft.respiratory = new BigDecimal("24");
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.vitals.temperatureC", "Nhập nhiệt độ từ 34 đến 42 °C"),
                        entry("exam.vitals.pulse", "Nhập mạch từ 40 đến 220 lần/phút"));
    }

    @Test
    @DisplayName("Chiều cao, cân nặng, vòng đầu so khoảng trên số làm tròn 0,1; quá 60 tháng không đo vòng đầu")
    void measurementsRoundedBeforeRangeCheck() {
        Case c = new Case();
        c.height = new BigDecimal("150.04");
        c.weight = null;
        c.head = new BigDecimal("29.94");
        assertThat(c.errors())
                .containsExactly(
                        entry("weightKg", "Nhập cân nặng"),
                        entry("headCircumferenceCm", "Nhập vòng đầu từ 30 đến 60 cm"));

        c.height = new BigDecimal("150.05");
        c.weight = new BigDecimal("15");
        c.head = new BigDecimal("29.95");
        assertThat(c.errors()).containsExactly(entry("heightCm", "Nhập chiều cao/chiều dài từ 40 đến 150 cm"));

        c.height = new BigDecimal("110");
        c.head = null;
        c.dob = EXAM_DATE.minusMonths(61);
        assertThat(c.errors()).isEmpty();
    }

    @Test
    @DisplayName("Dinh dưỡng: tình trạng bắt buộc, \"Bình thường\" không đi cùng mục khác; SD, vòng cánh tay có khoảng")
    void nutritionRules() {
        Case c = new Case();
        c.draft.statuses = List.of("NORMAL", "ANEMIA_SIGNS");
        assertThat(c.errors())
                .containsExactly(entry("exam.nutrition.statuses", "\"Bình thường\" không chọn cùng các mục khác"));
        c.draft.statuses = List.of();
        assertThat(c.errors()).containsExactly(entry("exam.nutrition.statuses", "Chọn tình trạng dinh dưỡng"));

        c.draft.statuses = List.of("NORMAL");
        c.draft.lengthSd = new BigDecimal("7");
        c.draft.arm = new BigDecimal("40");
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.nutrition.lengthForAgeSd", "Nhập chiều dài/tuổi (SD) từ -6 đến 6"),
                        entry("exam.nutrition.armCircumferenceMm", "Nhập chu vi vòng cánh tay từ 50 đến 300 mm"));
    }

    @Test
    @DisplayName("STANDARD: chưa chọn kết luận; chuyển khám thiếu chuyên khoa; Bình thường đi cùng kết luận khác")
    void standardConclusionRules() {
        Case c = new Case();
        c.draft.normal = false;
        assertThat(c.errors()).containsExactly(entry("exam.conclusion.flags", "Chọn kết luận của bác sĩ"));

        c.draft.healthIssue = true;
        c.draft.issueText = "Cận thị";
        c.draft.action = "REFERRAL";
        assertThat(c.errors())
                .containsExactly(entry("exam.conclusion.specialty", "Ghi chuyên khoa bác sĩ đề nghị, ví dụ Mắt"));

        c.draft.normal = true;
        c.draft.action = "NONE";
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.conclusion.flags", "\"Bình thường\" không chọn cùng các kết luận khác"),
                        entry(
                                "exam.conclusion.action",
                                "Có nguy cơ lao hoặc có vấn đề sức khỏe: chọn Tư vấn hoặc Chuyển khám chuyên khoa"));
    }

    @Test
    @DisplayName("STANDARD: chỉ trẻ có vấn đề đã biết mới bắt buộc chọn vấn đề mới hay đã biết")
    void issueKindRequiredOnlyWithKnownCondition() {
        Case c = new Case();
        c.draft.normal = false;
        c.draft.healthIssue = true;
        c.draft.issueText = "Cận thị";
        c.draft.action = "ADVICE";
        c.draft.adviceText = "Đeo kính đúng độ";
        assertThat(c.errors()).isEmpty();

        c.knownCondition = true;
        assertThat(c.errors())
                .containsExactly(entry("exam.conclusion.issueKind", "Chọn vấn đề mới hay vấn đề đã biết"));
        c.draft.issueKind = "KNOWN";
        assertThat(c.errors()).isEmpty();
    }

    @Test
    @DisplayName("FOLLOW_UP: chọn câu so với vấn đề đang theo dõi; nguy cơ lao không đi cùng Chưa ghi nhận thêm")
    void followUpConclusionRules() {
        Case c = new Case();
        c.mode = FOLLOW_UP;
        c.draft.followUpFinding = "";
        assertThat(c.errors())
                .containsExactly(entry(
                        "exam.conclusion.followUpFinding", "Chọn kết luận của bác sĩ so với vấn đề đang theo dõi"));

        c.draft.followUpFinding = "NO_NEW_FINDING";
        c.draft.tbRisk = true;
        assertThat(c.errors())
                .containsExactly(entry(
                        "exam.conclusion.followUpFinding",
                        "Đã tích nguy cơ lao: chọn \"Tư vấn\" hoặc \"Có thêm bất thường mới\""));

        c.draft.followUpFinding = "ADVICE";
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.conclusion.issueText", "Ghi rõ vấn đề bác sĩ ghi nhận"),
                        entry("exam.conclusion.adviceText", "Ghi lời tư vấn của bác sĩ"));

        c.draft.followUpFinding = "NEW_FINDING";
        assertThat(c.errors())
                .containsExactly(entry("exam.conclusion.issueText", "Ghi rõ bất thường mới bác sĩ ghi nhận"));

        // Chuyên khoa không bắt buộc ở mẫu FOLLOW_UP
        c.draft.issueText = "Viêm tai giữa";
        assertThat(c.errors()).isEmpty();
    }

    @Test
    @DisplayName("Có người đi cùng và kết luận cần theo dõi sát ⇒ bắt buộc ý kiến phụ huynh")
    void parentOpinionRequiredWhenNeeded() {
        Case c = new Case();
        c.draft.hasCompanion = "YES";
        c.draft.guardianIndex = 0;
        c.draft.normal = false;
        c.draft.tbRisk = true;
        c.draft.action = "ADVICE";
        c.draft.adviceText = "Chụp X-quang phổi";
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.conclusion.parentOpinion", "Ghi ý kiến của phụ huynh (người đi cùng) về kết luận"));
        c.draft.parentOpinion = "Gia đình đồng ý đưa trẻ đi khám";
        assertThat(c.errors()).isEmpty();

        // Mẫu FOLLOW_UP luôn ở mức cần theo dõi sát
        c.mode = FOLLOW_UP;
        c.draft.followUpFinding = "ADVICE";
        c.draft.issueText = "Ho kéo dài";
        c.draft.parentOpinion = "";
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.conclusion.parentOpinion", "Ghi ý kiến của phụ huynh (người đi cùng) về kết luận"));
    }

    @Test
    @DisplayName("Người đi cùng: chưa chọn có/không; phụ huynh ngoài danh sách của hồ sơ")
    void companionRules() {
        Case c = new Case();
        c.draft.hasCompanion = "";
        assertThat(c.errors()).containsExactly(entry("exam.admin.hasCompanion", "Chọn có người đi cùng trẻ hay không"));
        c.draft.hasCompanion = "YES";
        c.draft.guardianIndex = 2;
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.admin.companion", "Chọn người đi cùng trong danh sách phụ huynh của hồ sơ trẻ"));
    }

    @Test
    @DisplayName("Tiền sử: Có thì ghi tên bệnh; ngày tiếp xúc lao không sau ngày khám (dd/MM/yyyy)")
    void historyRules() {
        Case c = new Case();
        c.draft.self = "YES";
        c.draft.family = "";
        c.draft.tbContact = "YES";
        c.draft.tbContactDate = LocalDate.of(2026, 10, 22);
        assertThat(c.errors())
                .containsExactly(
                        entry("exam.history.selfText", "Ghi rõ tên bệnh"),
                        entry("exam.history.family", "Chọn tiền sử gia đình"),
                        entry("exam.history.tbContactDate", "Ngày tiếp xúc không được sau ngày khám 20/10/2026"));
    }

    @Test
    @DisplayName("HLT-13: sửa phiếu đã kết luận phải ghi lý do; lý do tối đa 300 ký tự")
    void reasonRequiredWhenReconcluding() {
        Case c = new Case();
        c.alreadyConcluded = true;
        c.reason = "   ";
        assertThat(c.errors()).containsExactly(entry("reason", "Nhập lý do sửa phiếu đã có kết luận"));
        c.reason = "x".repeat(301);
        assertThat(c.errors()).containsExactly(entry("reason", "Tối đa 300 ký tự"));
        c.reason = "Bác sĩ đo lại nhiệt độ";
        assertThat(c.errors()).isEmpty();
    }

    @Test
    @DisplayName("Nhiều lỗi ⇒ khóa theo thứ tự kiểm của BE giả (FE mở bước của ô lỗi)")
    void errorKeysFollowMockOrder() {
        Case c = new Case();
        c.date = null;
        c.draft.hasCompanion = null;
        c.draft.pulse = null;
        c.height = null;
        c.draft.mental = "";
        c.draft.clinical.put("gait", "LIMPING");
        c.draft.doctorName = " ";
        assertThat(c.errors().keySet())
                .containsExactly(
                        "date",
                        "exam.admin.hasCompanion",
                        "exam.vitals.pulse",
                        "heightCm",
                        "exam.development.mental",
                        "exam.clinical.gait",
                        "exam.conclusion.doctorName");
        assertThat(c.errors()).containsEntry("exam.clinical.gait", "Chọn một kết quả");
    }

    // ---------- Chuẩn hóa ----------

    @Test
    @DisplayName("Chuẩn hóa FOLLOW_UP: bỏ cờ kết luận, loại vấn đề, hướng xử lý; vấn đề, chuyên khoa giữ theo lựa chọn")
    void followUpNormalization() {
        Draft d = new Draft();
        d.healthIssue = true;
        d.issueKind = "NEW";
        d.action = "REFERRAL";
        d.followUpFinding = "ADVICE";
        d.issueText = "  Ho  ";
        d.adviceText = "Uống nhiều nước";
        d.specialty = "Hô hấp";
        HealthExamConclusion c = normalize(d, FOLLOW_UP, false).conclusion();
        assertThat(c.normal()).isFalse();
        assertThat(c.healthIssue()).isFalse();
        assertThat(c.issueKind()).isEmpty();
        assertThat(c.action()).isEmpty();
        assertThat(c.followUpFinding()).isEqualTo("ADVICE");
        assertThat(c.issueText()).isEqualTo("Ho");
        assertThat(c.adviceText()).isEqualTo("Uống nhiều nước");
        assertThat(c.specialty()).isEmpty();

        d.followUpFinding = "NEW_FINDING";
        assertThat(normalize(d, FOLLOW_UP, false).conclusion())
                .extracting(HealthExamConclusion::issueText, HealthExamConclusion::specialty)
                .containsExactly("Ho", "Hô hấp");

        d.followUpFinding = "NO_NEW_FINDING";
        assertThat(normalize(d, FOLLOW_UP, false).conclusion())
                .extracting(HealthExamConclusion::issueText, HealthExamConclusion::specialty)
                .containsExactly("", "");

        d.followUpFinding = "MAYBE";
        assertThat(normalize(d, FOLLOW_UP, false).conclusion().followUpFinding())
                .isEmpty();
    }

    @Test
    @DisplayName("Chuẩn hóa STANDARD: bỏ followUpFinding; loại vấn đề chỉ giữ khi hỏi mới hay đã biết")
    void standardNormalization() {
        Draft d = new Draft();
        d.normal = false;
        d.healthIssue = true;
        d.issueText = "Sâu răng";
        d.issueKind = "KNOWN";
        d.action = "ADVICE";
        d.specialty = "Răng hàm mặt";
        d.followUpFinding = "NEW_FINDING";
        HealthExamConclusion c = normalize(d, STANDARD, true).conclusion();
        assertThat(c.followUpFinding()).isEmpty();
        assertThat(c.issueKind()).isEqualTo("KNOWN");
        assertThat(c.action()).isEqualTo("ADVICE");
        assertThat(c.specialty()).isEqualTo("Răng hàm mặt");

        assertThat(normalize(d, STANDARD, false).conclusion().issueKind()).isEmpty();
        d.action = "REFERRAL";
        assertThat(normalize(d, STANDARD, true).conclusion().issueKind()).isEmpty();
        d.action = "advice";
        assertThat(normalize(d, STANDARD, true).conclusion().action()).isEmpty();
    }

    @Test
    @DisplayName("Ý kiến phụ huynh chỉ giữ khi có người đi cùng và kết luận cần theo dõi sát")
    void parentOpinionClearedWhenNotNeeded() {
        Draft d = new Draft();
        d.hasCompanion = "YES";
        d.guardianIndex = 0;
        d.parentOpinion = "Gia đình đồng ý";
        HealthExamForm normalResult = normalize(d, STANDARD, false);
        assertThat(normalResult.conclusion().parentOpinion()).isEmpty();
        assertThat(HealthExamNormalizer.needsParentOpinion(normalResult, STANDARD))
                .isFalse();

        d.normal = false;
        d.healthIssue = true;
        d.action = "REFERRAL";
        HealthExamForm referral = normalize(d, STANDARD, false);
        assertThat(referral.conclusion().parentOpinion()).isEqualTo("Gia đình đồng ý");
        assertThat(HealthExamNormalizer.needsParentOpinion(referral, STANDARD)).isTrue();

        assertThat(normalize(d, FOLLOW_UP, false).conclusion().parentOpinion()).isEqualTo("Gia đình đồng ý");

        d.hasCompanion = "NO";
        HealthExamForm alone = normalize(d, STANDARD, false);
        assertThat(alone.conclusion().parentOpinion()).isEmpty();
        assertThat(alone.admin().companion()).isEqualTo(new HealthExamCompanion(null, "", "", "", ""));
    }

    @Test
    @DisplayName("Theo tuổi: quá 60 tháng bỏ trạng thái vòng đầu; câu tự kỷ chỉ giữ ở 16–30 tháng")
    void ageGatedFields() {
        Draft d = new Draft();
        d.headStatus = "LARGE";
        d.autismRisk = "YES";
        HealthExamForm at61 = HealthExamNormalizer.normalize(
                d.build(), STANDARD, HealthExamReview.reference(DOB, DOB.plusMonths(61)), false);
        assertThat(at61.nutrition().headStatus()).isEmpty();
        assertThat(at61.development().autismRisk()).isEmpty();

        HealthExamForm at24 = HealthExamNormalizer.normalize(
                d.build(), STANDARD, HealthExamReview.reference(DOB, DOB.plusMonths(24)), false);
        assertThat(at24.nutrition().headStatus()).isEqualTo("LARGE");
        assertThat(at24.development().autismRisk()).isEqualTo("YES");
    }

    @Test
    @DisplayName(
            "Chuẩn hóa chung: mã lạ ⇒ \"\", số tròn HALF_UP, cắt độ dài, số định danh bỏ khoảng trắng, đủ 24 mục VI")
    void generalNormalization() {
        Draft d = new Draft();
        d.temperature = new BigDecimal("37.45");
        d.temperatureStatus = "HOT";
        d.pulse = new BigDecimal("99.5");
        d.respiratory = new BigDecimal("24.49");
        d.lengthSd = new BigDecimal("-1.25");
        d.arm = new BigDecimal("135.5");
        d.statuses = Arrays.asList("ANEMIA_SIGNS", "BAD", null, "ANEMIA_SIGNS", " MALNUTRITION");
        d.clinical = new HashMap<>(Map.of("skinColor", "BLUE", "palms", "PALE", "behaviorNote", "  Hay khóc  "));
        d.doctorName = "B".repeat(120);
        d.childIdNumber = " 0012 3456 7890 ";
        d.step = 9;

        HealthExamForm exam = normalize(d, STANDARD, false);

        assertThat(exam.vitals().temperatureC()).isEqualTo(new BigDecimal("37.5"));
        assertThat(exam.vitals().temperatureStatus()).isEmpty();
        assertThat(exam.vitals().pulse()).isEqualTo(new BigDecimal("100"));
        assertThat(exam.vitals().respiratoryRate()).isEqualTo(new BigDecimal("24"));
        assertThat(exam.nutrition().lengthForAgeSd()).isEqualTo(new BigDecimal("-1.3"));
        assertThat(exam.nutrition().armCircumferenceMm()).isEqualTo(new BigDecimal("136"));
        assertThat(exam.nutrition().statuses()).containsExactly("ANEMIA_SIGNS", "MALNUTRITION");
        assertThat(exam.clinical()).isInstanceOf(LinkedHashMap.class);
        assertThat(exam.clinical().keySet()).containsExactlyElementsOf(clinicalKeysWithNote());
        assertThat(exam.clinical())
                .containsEntry("skinColor", "")
                .containsEntry("palms", "PALE")
                .containsEntry("gait", "")
                .containsEntry("behaviorNote", "Hay khóc");
        assertThat(exam.conclusion().doctorName()).hasSize(100);
        assertThat(exam.admin().childIdNumber()).isEqualTo("001234567890");
        assertThat(exam.step()).isEqualTo(4);
    }

    @Test
    @DisplayName("Phiếu null ⇒ mọi ô trống, không lấy mặc định của phiếu mới")
    void nullFormNormalizesToBlank() {
        HealthExamForm exam = HealthExamNormalizer.normalize(null, STANDARD, AT_40_MONTHS, false);
        assertThat(exam.step()).isZero();
        assertThat(exam.admin().hasCompanion()).isEmpty();
        assertThat(exam.admin().address()).isEqualTo(new HealthExamAddress("", "", ""));
        assertThat(exam.admin().companion()).isEqualTo(new HealthExamCompanion(null, "", "", "", ""));
        assertThat(exam.history().self()).isEmpty();
        assertThat(exam.vitals().temperatureC()).isNull();
        assertThat(exam.nutrition().statuses()).isEmpty();
        assertThat(exam.clinical()).hasSize(25).containsEntry("skinColor", "").containsEntry("prolongedSadness", "");
        assertThat(exam.conclusion().action()).isEmpty();
        assertThat(exam.conclusion().normal()).isFalse();
    }

    @Test
    @DisplayName("Phiếu mới: phần VI chọn sẵn bình thường (2 câu tâm lý, hành vi là Không), phần khác để trống")
    void templateDefaults() {
        HealthExamForm template = HealthExamCatalog.template();
        assertThat(HealthExamCatalog.clinicalKeys()).hasSize(24);
        assertThat(template.step()).isZero();
        assertThat(template.clinical().keySet()).containsExactlyElementsOf(clinicalKeysWithNote());
        assertThat(template.clinical())
                .containsEntry("skinColor", "PINK")
                .containsEntry("palms", "NORMAL")
                .containsEntry("neckMass", "NO")
                .containsEntry("prolongedSadness", "NO")
                .containsEntry("selfHarmRisk", "NO")
                .containsEntry("behaviorNote", "");
        assertThat(template.admin().companion()).isEqualTo(new HealthExamCompanion(null, "", "", "", ""));
        assertThat(template.history().self()).isEmpty();
        assertThat(template.conclusion().doctorName()).isEmpty();
    }

    // ---------- Mốc tham khảo theo tuổi ----------

    @ParameterizedTest(name = "{0} tháng ⇒ {1}")
    @CsvSource({
        "11, trẻ dưới 12 tháng, 100, 180, 30, 53",
        "12, trẻ 12–35 tháng, 98, 140, 22, 37",
        "35, trẻ 12–35 tháng, 98, 140, 22, 37",
        "36, trẻ 3–5 tuổi, 80, 120, 20, 28",
        "71, trẻ 3–5 tuổi, 80, 120, 20, 28",
        "72, trẻ từ 6 tuổi, 75, 118, 18, 25"
    })
    @DisplayName("Nhóm tuổi của khoảng mạch, nhịp thở")
    void vitalBands(int months, String label, int pulseMin, int pulseMax, int respMin, int respMax) {
        HealthExamReference ref = HealthExamReview.reference(DOB, DOB.plusMonths(months));
        assertThat(ref.ageMonths()).isEqualTo(months);
        assertThat(ref.vitals().label()).isEqualTo(label);
        assertThat(ref.vitals().pulse().min()).isEqualTo(pulseMin);
        assertThat(ref.vitals().pulse().max()).isEqualTo(pulseMax);
        assertThat(ref.vitals().respiratoryRate().min()).isEqualTo(respMin);
        assertThat(ref.vitals().respiratoryRate().max()).isEqualTo(respMax);
        assertThat(ref.vitals().temperature().feverFrom()).isEqualByComparingTo("37.5");
        assertThat(ref.vitals().temperature().hypothermiaBelow()).isEqualByComparingTo("36");
        assertThat(ref.label())
                .isEqualTo(
                        "ngưỡng tham khảo tạm (mạch, nhịp thở theo AHA PALS 2020; nhiệt độ đo ở nách) — chờ bác sĩ xác nhận");
    }

    @Test
    @DisplayName("Tuổi tháng tròn; vòng đầu tới 60 tháng; câu tự kỷ 16–30 tháng")
    void ageGates() {
        assertThat(HealthExamReview.ageMonths(LocalDate.of(2023, 1, 31), LocalDate.of(2023, 2, 28)))
                .isZero();
        assertThat(HealthExamReview.ageMonths(LocalDate.of(2023, 1, 31), LocalDate.of(2023, 3, 1)))
                .isEqualTo(1);
        assertThat(HealthExamReview.ageMonths(DOB, DOB.plusMonths(12).minusDays(1)))
                .isEqualTo(11);

        HealthExamReference at60 = HealthExamReview.reference(DOB, DOB.plusMonths(60));
        assertThat(at60.headApplies()).isTrue();
        assertThat(at60.head().low()).isEqualByComparingTo("47.66");
        assertThat(at60.head().high()).isEqualByComparingTo("53.16");
        HealthExamReference at61 = HealthExamReview.reference(DOB, DOB.plusMonths(61));
        assertThat(at61.headApplies()).isFalse();
        assertThat(at61.head()).isNull();

        assertThat(HealthExamReview.reference(DOB, DOB.plusMonths(15)).autismQuestion())
                .isFalse();
        assertThat(HealthExamReview.reference(DOB, DOB.plusMonths(16)).autismQuestion())
                .isTrue();
        assertThat(HealthExamReview.reference(DOB, DOB.plusMonths(30)).autismQuestion())
                .isTrue();
        assertThat(HealthExamReview.reference(DOB, DOB.plusMonths(31)).autismQuestion())
                .isFalse();
    }

    // ---------- Điểm cần bác sĩ xem lại ----------

    @Test
    @DisplayName("Sinh tồn: sốt theo số, mạch nhanh theo nhóm tuổi, nhịp thở bất thường theo lựa chọn của bác sĩ")
    void vitalReviewPoints() {
        Draft d = new Draft();
        d.temperature = new BigDecimal("38.5");
        d.temperatureStatus = "FEVER";
        d.pulse = new BigDecimal("150");
        d.pulseStatus = "FAST";
        d.respiratory = new BigDecimal("24");
        d.respiratoryStatus = "FAST";
        HealthExamReviewPoints points = HealthExamReview.points(normalize(d, STANDARD, false), AT_40_MONTHS);

        assertThat(points.vitals())
                .extracting(HealthExamVitalPoint::key, HealthExamVitalPoint::text)
                .containsExactly(
                        tuple("temperatureC", "Nhiệt độ 38,5 °C: sốt (từ 37,5 °C)"),
                        tuple("pulse", "Mạch 150 lần/phút, trên khoảng tham khảo 80–120 của trẻ 3–5 tuổi"),
                        tuple("respiratoryRate", "Nhịp thở: thở nhanh (theo bác sĩ)"));
        assertThat(points.vitalsHint().count()).isEqualTo(3);
        assertThat(points.vitalsHint().total()).isEqualTo(3);
        assertThat(points.vitalsHint().level()).isEqualTo("ALERT");
        assertThat(points.vitalsHint().text())
                .isEqualTo("Gợi ý: nhiều dấu hiệu sinh tồn bất thường, bác sĩ xem lại trước khi kết luận");
        assertThat(points.reference()).isEqualTo(AT_40_MONTHS.label());

        d.temperature = new BigDecimal("35.4");
        d.temperatureStatus = "HYPOTHERMIA";
        d.pulse = new BigDecimal("70");
        d.pulseStatus = "NORMAL";
        d.respiratoryStatus = "NORMAL";
        HealthExamReviewPoints low = HealthExamReview.points(normalize(d, STANDARD, false), AT_40_MONTHS);
        assertThat(low.vitals())
                .extracting(HealthExamVitalPoint::text)
                .containsExactly(
                        "Nhiệt độ 35,4 °C: hạ thân nhiệt (dưới 36 °C)",
                        "Mạch 70 lần/phút, dưới khoảng tham khảo 80–120 của trẻ 3–5 tuổi");
    }

    @Test
    @DisplayName("Gợi ý kết hợp: vòng đầu nhỏ + phát triển tinh thần chưa bình thường ⇒ cân nhắc chuyển khám")
    void combinedHintWithTwoSigns() {
        Draft d = new Draft();
        d.headStatus = "SMALL";
        d.mental = "NO";
        HealthExamReviewPoints points = HealthExamReview.points(normalize(d, STANDARD, false), AT_40_MONTHS);
        assertThat(points.combined().signs())
                .containsExactly("Vòng đầu nhỏ", "Phát triển tinh thần chưa bình thường theo độ tuổi");
        assertThat(points.combined().total()).isEqualTo(3);
        assertThat(points.combined().level()).isEqualTo("REFERRAL");
        assertThat(points.combined().text()).isEqualTo("Gợi ý: cân nhắc chuyển khám chuyên khoa");
        assertThat(points.vitals()).isEmpty();
        assertThat(points.vitalsHint().level()).isEqualTo("NONE");
        assertThat(points.vitalsHint().text()).isEmpty();
    }

    @Test
    @DisplayName("Mục khác theo thứ tự: tiền sử, dinh dưỡng, tự kỷ, tiêm chủng, lâm sàng, ghi chú hành vi")
    void otherPointsOrder() {
        LocalDate dob = EXAM_DATE.minusMonths(24);
        HealthExamReference at24 = HealthExamReview.reference(dob, EXAM_DATE);
        Draft d = new Draft();
        d.self = "YES";
        d.selfText = " Hen phế quản ";
        d.tbContact = "YES";
        d.tbContactDate = LocalDate.of(2026, 9, 1);
        d.statuses = List.of("ANEMIA_SIGNS");
        d.autismRisk = "YES";
        d.bcg = "NO";
        d.clinical.put("skinColor", "PALE");
        d.clinical.put("prolongedSadness", "YES");
        d.clinical.put("behaviorNote", "Hay khóc khi xa mẹ");

        HealthExamReviewPoints points =
                HealthExamReview.points(HealthExamNormalizer.normalize(d.build(), STANDARD, at24, false), at24);

        assertThat(points.others())
                .extracting(HealthExamOtherPoint::text)
                .containsExactly(
                        "Tiền sử bản thân: Có (Hen phế quản)",
                        "Tiền sử tiếp xúc với người bệnh lao: Có",
                        "Tình trạng dinh dưỡng: Dấu hiệu thiếu máu",
                        "Trẻ có nguy cơ tự kỷ (với trẻ từ 16–30 tháng tuổi): Có",
                        "Tiêm chủng — Lao (sơ sinh): Không",
                        "Màu sắc da: Nhợt",
                        "Có biểu hiện buồn bã, lo âu, mất ngủ kéo dài không: Có",
                        "Ghi rõ (tâm lý, hành vi): Hay khóc khi xa mẹ");
        assertThat(points.combined().level()).isEqualTo("NONE");
        assertThat(points.combined().total()).isEqualTo(3);
    }

    // ---------- Lịch sử sửa ----------

    @Test
    @DisplayName("HLT-13: sửa nhiệt độ và số định danh ⇒ ghi ô đổi, giá trị hiển thị, CCCD che còn 3 số cuối")
    void diffOfChangedVitalAndMaskedIdNumber() {
        Draft d = new Draft();
        d.childIdNumber = "001234567890";
        d.temperature = new BigDecimal("37");
        HealthExamForm before = normalize(d, STANDARD, false);
        d.childIdNumber = "001234567999";
        d.temperature = new BigDecimal("38.5");
        d.temperatureStatus = "FEVER";
        HealthExamForm after = normalize(d, STANDARD, false);

        List<HealthMeasurementChange> changes = HealthExamDiff.changes(snapshot(before), snapshot(after));

        assertThat(changes)
                .extracting(
                        HealthMeasurementChange::field,
                        HealthMeasurementChange::label,
                        HealthMeasurementChange::from,
                        HealthMeasurementChange::to)
                .containsExactly(
                        tuple("exam.admin.childIdNumber", "Số định danh của trẻ", "••••890", "••••999"),
                        tuple("exam.vitals.temperatureC", "Nhiệt độ (°C)", "37", "38,5"),
                        tuple("exam.vitals.temperatureStatus", "Nhiệt độ: trạng thái", "Bình thường", "Sốt"));
        assertThat(HealthExamDiff.changes(snapshot(after), snapshot(after))).isEmpty();
    }

    @Test
    @DisplayName("Lịch sử sửa: ngày khám yyyy-MM-dd (FE tự đổi), ngày tiếp xúc dd/MM/yyyy, ô trống là \"\"")
    void diffDisplayFormats() {
        Draft d = new Draft();
        HealthExamForm before = normalize(d, STANDARD, false);
        d.tbContact = "YES";
        d.tbContactDate = LocalDate.of(2026, 9, 5);
        HealthExamForm after = normalize(d, STANDARD, false);

        List<HealthMeasurementChange> changes = HealthExamDiff.changes(
                new HealthExamDiff.Snapshot(EXAM_DATE, new BigDecimal("98.5"), new BigDecimal("15.2"), null, before),
                new HealthExamDiff.Snapshot(
                        EXAM_DATE.plusDays(1), new BigDecimal("99.0"), new BigDecimal("15.2"), null, after));

        assertThat(changes)
                .extracting(HealthMeasurementChange::field, HealthMeasurementChange::from, HealthMeasurementChange::to)
                .containsExactly(
                        tuple("date", "2026-10-20", "2026-10-21"),
                        tuple("heightCm", "98,5", "99"),
                        tuple("exam.history.tbContact", "Không", "Có"),
                        tuple("exam.history.tbContactDate", "", "05/09/2026"));
    }

    // ---------- Dữ liệu giả ----------

    private static HealthExamForm normalize(Draft draft, HealthConclusionMode mode, boolean knownCondition) {
        return HealthExamNormalizer.normalize(draft.build(), mode, AT_40_MONTHS, knownCondition);
    }

    private static HealthExamDiff.Snapshot snapshot(HealthExamForm exam) {
        return new HealthExamDiff.Snapshot(
                EXAM_DATE, new BigDecimal("98.5"), new BigDecimal("15.2"), new BigDecimal("49.0"), exam);
    }

    private static List<String> clinicalKeysWithNote() {
        List<String> keys = new ArrayList<>(HealthExamCatalog.clinicalKeys());
        keys.add("behaviorNote");
        return keys;
    }

    /** Phiếu gửi lên (chưa chuẩn hóa), mặc định là phiếu STANDARD hợp lệ của trẻ khỏe mạnh. */
    private static final class Draft {
        String childIdNumber = "";
        String hasCompanion = "NO";
        Integer guardianIndex;
        String self = "NO";
        String selfText = "";
        String family = "NO";
        String tbContact = "NO";
        LocalDate tbContactDate;
        BigDecimal temperature = new BigDecimal("36.8");
        String temperatureStatus = "NORMAL";
        BigDecimal pulse = new BigDecimal("100");
        String pulseStatus = "NORMAL";
        BigDecimal respiratory = new BigDecimal("24");
        String respiratoryStatus = "NORMAL";
        BigDecimal lengthSd;
        BigDecimal arm;
        String headStatus = "NORMAL";
        List<String> statuses = List.of("NORMAL");
        String mental = "YES";
        String motor = "YES";
        String autismRisk = "";
        String bcg = "YES";
        Map<String, String> clinical =
                new HashMap<>(HealthExamCatalog.template().clinical());
        boolean normal = true;
        boolean tbRisk;
        boolean healthIssue;
        String issueKind = "";
        String followUpFinding = "";
        String action = "NONE";
        String issueText = "";
        String adviceText = "";
        String specialty = "";
        String doctorName = "BS. Lê Thị Minh";
        String parentOpinion = "";
        Integer step = 2;

        HealthExamForm build() {
            return new HealthExamForm(
                    step,
                    new HealthExamAdmin(
                            childIdNumber,
                            "NO",
                            null,
                            "Kinh",
                            "Trẻ em dưới 6 tuổi",
                            "BHYT",
                            "",
                            new HealthExamAddress("Hà Nội", "Phường Láng", "Số 1 ngõ 2"),
                            hasCompanion,
                            new HealthExamCompanion(guardianIndex, "Trần Thị Mẹ", "Mẹ", "0912345678", "")),
                    new HealthExamHistory(self, selfText, family, "", tbContact, tbContactDate),
                    new HealthExamVitals(
                            temperature, temperatureStatus, pulse, pulseStatus, respiratory, respiratoryStatus),
                    new HealthExamNutrition(lengthSd, null, headStatus, arm, statuses),
                    new HealthExamDevelopment(mental, motor, autismRisk),
                    new HealthExamImmunization(bcg, "YES", "YES"),
                    clinical,
                    new HealthExamConclusion(
                            normal,
                            tbRisk,
                            healthIssue,
                            issueKind,
                            followUpFinding,
                            action,
                            issueText,
                            adviceText,
                            specialty,
                            doctorName,
                            parentOpinion));
        }
    }

    /** Một lần lưu kết luận: chuẩn hóa theo mẫu và tuổi rồi kiểm như service. */
    private static final class Case {
        final Draft draft = new Draft();
        HealthConclusionMode mode = STANDARD;
        LocalDate date = EXAM_DATE;
        LocalDate dob = DOB;
        BigDecimal height = new BigDecimal("98.5");
        BigDecimal weight = new BigDecimal("15.2");
        BigDecimal head = new BigDecimal("49.0");
        String reason = "";
        boolean alreadyConcluded;
        boolean knownCondition;

        Map<String, String> errors() {
            HealthExamReference reference = HealthExamReview.reference(dob, date == null ? EXAM_DATE : date);
            HealthExamForm exam = HealthExamNormalizer.normalize(draft.build(), mode, reference, knownCondition);
            List<FieldErrorResponse> errors = HealthExamValidator.validate(new HealthExamValidator.Input(
                    exam,
                    date,
                    height,
                    weight,
                    head,
                    reason,
                    alreadyConcluded,
                    mode,
                    reference,
                    TODAY,
                    dob,
                    CAMPAIGN_START,
                    CAMPAIGN_END,
                    2,
                    knownCondition));
            Map<String, String> byField = new LinkedHashMap<>();
            errors.forEach(e -> byField.put(e.field(), e.message()));
            assertThat(byField).hasSameSizeAs(errors);
            return byField;
        }
    }
}
