package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.integration.ai.dto.SummaryDraftContent;
import com.carenest.integration.ai.dto.SummaryDraftContent.EvidencedPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryContentRendererTest {

    @Test
    void removesEvidenceCodesThatModelEmbedsInText() {
        assertThat(SummaryContentRenderer.withoutEvidenceCodes("Ăn hết suất, tự xúc cơm gọn gàng (NOTE-17)."))
                .isEqualTo("Ăn hết suất, tự xúc cơm gọn gàng.");
        assertThat(SummaryContentRenderer.withoutEvidenceCodes(
                        "Giấc ngủ trưa muộn (NOTE-13); cần tiếp tục quan sát [CRITERIA-SLEEPING, NOTE-14]"))
                .isEqualTo("Giấc ngủ trưa muộn; cần tiếp tục quan sát");
    }

    @Test
    void keepsTextWithoutCodesUnchanged() {
        assertThat(SummaryContentRenderer.withoutEvidenceCodes("Tham gia hoạt động tốt trong 4/5 ngày."))
                .isEqualTo("Tham gia hoạt động tốt trong 4/5 ngày.");
    }

    @Test
    void rendersSectionsForTeacher() {
        SummaryDraftContent draft = new SummaryDraftContent(
                "Trẻ tham gia tích cực.",
                List.of(new EvidencedPoint("Ăn hết suất (NOTE-17).", List.of("NOTE-17"))),
                List.of(new EvidencedPoint("Ngủ trưa muộn.", List.of("CRITERIA-SLEEPING"))),
                List.of());

        assertThat(SummaryContentRenderer.fromDraft(draft))
                .isEqualTo(
                        "Trẻ tham gia tích cực.\n\nĐiểm mạnh:\n- Ăn hết suất.\n\nCần tiếp tục quan sát:\n- Ngủ trưa muộn.");
    }
}
