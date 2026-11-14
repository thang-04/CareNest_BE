package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.health.HealthTrendContent;
import com.carenest.dto.health.HealthTrendContent.AttentionPoint;
import com.carenest.dto.health.HealthTrendContent.FollowUp;
import com.carenest.dto.health.PublishedHealthResponse.TrendWarning;
import com.carenest.utils.enums.HealthTrendFollowUpType;
import com.carenest.utils.enums.HealthTrendWarningCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthTrendContentRulesTest {
    private static final TrendWarning DROP =
            new TrendWarning(HealthTrendWarningCode.WEIGHT_DROP, HealthGrowth.WARNING, "Cân nặng giảm 0.5 kg.");
    private static final TrendWarning STALL =
            new TrendWarning(HealthTrendWarningCode.HEIGHT_STALL, HealthGrowth.WARNING, "Chiều cao chưa tăng.");
    private static final TrendWarning NORMAL =
            new TrendWarning(HealthTrendWarningCode.NORMAL, HealthGrowth.INFO, "Bình thường.");
    private static final Set<HealthTrendFollowUpType> ALLOWED =
            EnumSet.of(HealthTrendFollowUpType.RECHECK_NEXT_ROUND, HealthTrendFollowUpType.TALK_TO_PARENT);

    @Test
    @DisplayName("HLT-21: JSON hợp lệ ⇒ đủ 5 mục; bản văn ghép có các khối")
    void validOutput() {
        HealthTrendContent c = parse("""
                {"overview": "Cân nặng giảm nhẹ, chiều cao tăng đều.",
                 "attentionPoints": [{"warningCode": "WEIGHT_DROP", "text": "Cân nặng giảm 0,5 kg so với lần trước."}],
                 "positives": ["Chiều cao tăng 1,5 cm."],
                 "followUps": [{"type": "TALK_TO_PARENT", "text": "Trao đổi với phụ huynh về bữa ăn ở nhà."}],
                 "limitations": "Mới có 2 lần đo."}
                """, List.of(DROP)).orElseThrow();

        assertThat(c.overview()).isEqualTo("Cân nặng giảm nhẹ, chiều cao tăng đều.");
        assertThat(c.attentionPoints())
                .containsExactly(new AttentionPoint(
                        HealthTrendWarningCode.WEIGHT_DROP, "Cân nặng giảm 0,5 kg so với lần trước."));
        assertThat(c.followUps())
                .containsExactly(new FollowUp(
                        HealthTrendFollowUpType.TALK_TO_PARENT, "Trao đổi với phụ huynh về bữa ăn ở nhà."));
        assertThat(c.limitations()).isEqualTo("Mới có 2 lần đo.");
        assertThat(HealthTrendContentRules.render(c))
                .startsWith("Tổng quan: Cân nặng giảm nhẹ")
                .contains("Cần chú ý:\n- Cân nặng giảm 0,5 kg")
                .contains("Gợi ý theo dõi:\n- Trao đổi")
                .endsWith("Lưu ý về dữ liệu: Mới có 2 lần đo.");
    }

    @Test
    @DisplayName("HLT-21: mã cảnh báo lạ hoặc trùng bị bỏ; cảnh báo AI bỏ sót ⇒ dùng nguyên câu cảnh báo")
    void attentionPointsFollowWarnings() {
        HealthTrendContent c = parse("""
                {"overview": "Tổng quan.",
                 "attentionPoints": [
                   {"warningCode": "WEIGHT_DROP", "text": "Câu một."},
                   {"warningCode": "WEIGHT_DROP", "text": "Câu trùng."},
                   {"warningCode": "WFA_LOW", "text": "Cảnh báo tự thêm."},
                   {"warningCode": "KHONG_CO", "text": "Mã lạ."}]}
                """, List.of(DROP, STALL)).orElseThrow();

        assertThat(c.attentionPoints())
                .containsExactly(
                        new AttentionPoint(HealthTrendWarningCode.WEIGHT_DROP, "Câu một."),
                        new AttentionPoint(HealthTrendWarningCode.HEIGHT_STALL, "Chiều cao chưa tăng."));
        assertThat(c.positives()).isEmpty();
        assertThat(c.followUps()).isEmpty();
        assertThat(c.limitations()).isNull();
    }

    @Test
    @DisplayName("HLT-21: chỉ cảnh báo bình thường ⇒ không có điểm cần chú ý")
    void normalHasNoAttentionPoints() {
        HealthTrendContent c = parse("""
                {"overview": "Các chỉ số tăng đều.",
                 "attentionPoints": [{"warningCode": "NORMAL", "text": "Bình thường."}]}
                """, List.of(NORMAL)).orElseThrow();

        assertThat(c.attentionPoints()).isEmpty();
    }

    @Test
    @DisplayName("HLT-21: loại gợi ý không được phép (đo lại khi kỳ đã đóng) hoặc trùng loại bị bỏ")
    void followUpsLimitedToAllowed() {
        HealthTrendContent c = parse("""
                {"overview": "Tổng quan.",
                 "followUps": [
                   {"type": "REMEASURE", "text": "Đo lại."},
                   {"type": "RECHECK_NEXT_ROUND", "text": "Theo dõi kỳ sau."},
                   {"type": "RECHECK_NEXT_ROUND", "text": "Trùng."},
                   {"type": "REFER_HEALTH_STAFF", "text": "Đưa trẻ đi khám."}]}
                """, List.of(DROP)).orElseThrow();

        assertThat(c.followUps())
                .containsExactly(new FollowUp(HealthTrendFollowUpType.RECHECK_NEXT_ROUND, "Theo dõi kỳ sau."));
    }

    @Test
    @DisplayName("HLT-04: mục có ngôn ngữ chẩn đoán hoặc quá dài bị bỏ; giới hạn dữ liệu có từ cấm ⇒ null")
    void forbiddenOrLongItemsDropped() {
        String longText = "a".repeat(HealthTrendContentRules.MAX_ITEM + 1);
        HealthTrendContent c = parse("""
                {"overview": "Tổng quan.",
                 "positives": ["Trẻ không mắc bệnh gì.", "%s", "Ăn tốt.", "Ngủ đủ.", "Vui vẻ."],
                 "limitations": "Cần chẩn đoán thêm."}
                """.formatted(longText), List.of(NORMAL)).orElseThrow();

        assertThat(c.positives()).containsExactly("Ăn tốt.", "Ngủ đủ.", "Vui vẻ.");
        assertThat(c.limitations()).isNull();
    }

    @Test
    @DisplayName("HLT-04, AI-03: tổng quan có từ cấm, thiếu tổng quan, JSON hỏng ⇒ bỏ cả bản")
    void invalidOverviewRejected() {
        assertThat(parse("{\"overview\": \"Trẻ có dấu hiệu rối loạn tăng trưởng.\"}", List.of(DROP)))
                .isEmpty();
        assertThat(parse("{\"positives\": [\"Ăn tốt.\"]}", List.of(DROP))).isEmpty();
        assertThat(parse("Trẻ tăng cân đều.", List.of(DROP))).isEmpty();
        assertThat(parse("{\"overview\": \"Cắt", List.of(DROP))).isEmpty();
    }

    @Test
    @DisplayName("HLT-21: tổng quan quá dài ⇒ cắt ở dấu kết câu trong giới hạn; không có dấu câu ⇒ bỏ cả bản")
    void longOverviewCutAtSentence() {
        String first = "Câu đầu tiên.";
        String overview = first + " " + "x".repeat(HealthTrendContentRules.MAX_OVERVIEW);
        HealthTrendContent c =
                parse("{\"overview\": \"" + overview + "\"}", List.of(NORMAL)).orElseThrow();
        assertThat(c.overview()).isEqualTo(first);

        String noStop = "x".repeat(HealthTrendContentRules.MAX_OVERVIEW + 1);
        assertThat(parse("{\"overview\": \"" + noStop + "\"}", List.of(NORMAL))).isEmpty();
    }

    @Test
    @DisplayName("AI-03: JSON bọc trong ``` vẫn đọc được")
    void fencedJson() {
        assertThat(parse("```json\n{\"overview\": \"Tăng đều.\"}\n```", List.of(NORMAL)))
                .get()
                .extracting(HealthTrendContent::overview)
                .isEqualTo("Tăng đều.");
    }

    @Test
    @DisplayName("HLT-21: bản cho phụ huynh bỏ gợi ý theo dõi")
    void forParentDropsFollowUps() {
        HealthTrendContent c = new HealthTrendContent(
                "Tổng quan.",
                List.of(),
                List.of("Ăn tốt."),
                List.of(new FollowUp(HealthTrendFollowUpType.TALK_TO_PARENT, "Trao đổi.")),
                null);

        assertThat(c.forParent().followUps()).isEmpty();
        assertThat(HealthTrendContentRules.render(c.forParent())).doesNotContain("Gợi ý theo dõi");
    }

    private static Optional<HealthTrendContent> parse(String raw, List<TrendWarning> warnings) {
        return HealthTrendContentRules.fromAi(raw, warnings, ALLOWED);
    }
}
