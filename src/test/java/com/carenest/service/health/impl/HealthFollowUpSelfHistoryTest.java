package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.child.ChildMedicalHistory;
import com.carenest.utils.enums.DeclarationState;
import org.junit.jupiter.api.Test;

/** Unit test thuần (không Spring, không DB): tiền sử bản thân khi đóng phiếu theo dõi "vấn đề đã biết". */
class HealthFollowUpSelfHistoryTest {

    @Test // N: chưa có tiền sử ⇒ chỉ còn vấn đề mới
    void emptyHistoryBecomesCondition() {
        assertThat(HealthFollowUpServiceImpl.selfHistoryWith(self(DeclarationState.NONE_REPORTED, null), "Cận thị"))
                .isEqualTo("Cận thị");
    }

    @Test // N: vấn đề mới nối sau tiền sử cũ bằng "; "
    void newConditionIsAppended() {
        assertThat(HealthFollowUpServiceImpl.selfHistoryWith(self(DeclarationState.REPORTED, "Hen suyễn"), "Cận thị"))
                .isEqualTo("Hen suyễn; Cận thị");
    }

    @Test // B: đã có vấn đề đó (khác hoa thường, không dấu) ⇒ giữ nguyên
    void existingConditionIgnoresCaseAndDiacritics() {
        assertThat(HealthFollowUpServiceImpl.selfHistoryWith(
                        self(DeclarationState.REPORTED, "Hen suyễn; cận thị"), "CAN THI"))
                .isEqualTo("Hen suyễn; cận thị");
    }

    private static ChildMedicalHistory self(DeclarationState state, String text) {
        return new ChildMedicalHistory(
                state, text, DeclarationState.NONE_REPORTED, null, DeclarationState.NONE_REPORTED, null);
    }
}
