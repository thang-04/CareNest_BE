package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RewardListDiligentTest {

    @Test
    void halfDayAbsenceJustBelowNinetyPercentIsNotDiligent() {
        // 179/200 ngày = 89,5%: làm tròn sẽ thành 90% nhưng chưa đủ chuẩn Bé chăm
        assertThat(RewardListServiceImpl.isDiligent(179, 200)).isFalse();
        assertThat(RewardListServiceImpl.isDiligent(180, 200)).isTrue();
        assertThat(RewardListServiceImpl.isDiligent(17.5, 20)).isFalse();
        assertThat(RewardListServiceImpl.isDiligent(0, 0)).isFalse();
    }
}
