package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.utils.enums.HealthGrowthLevel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthGrowthLevelTest {

    @Test
    @DisplayName("HLT-20: cấp học theo tuổi bắt đầu của nhóm tuổi — dưới 36 tháng là nhà trẻ, từ 36 tháng là mẫu giáo")
    void levelFollowsAgeGroupMonths() {
        assertThat(HealthGrowthReportServiceImpl.levelOf(group("tuy-y", 24))).isEqualTo(HealthGrowthLevel.NURSERY);
        assertThat(HealthGrowthReportServiceImpl.levelOf(group("tuy-y", 12))).isEqualTo(HealthGrowthLevel.NURSERY);
        assertThat(HealthGrowthReportServiceImpl.levelOf(group("tuy-y", 36))).isEqualTo(HealthGrowthLevel.PRESCHOOL);
        assertThat(HealthGrowthReportServiceImpl.levelOf(group("tuy-y", 60))).isEqualTo(HealthGrowthLevel.PRESCHOOL);
        // Tháng ưu tiên hơn mã: nhóm mã ag-2 nhưng HT cấu hình bắt đầu 36 tháng ⇒ mẫu giáo
        assertThat(HealthGrowthReportServiceImpl.levelOf(group("ag-2", 36))).isEqualTo(HealthGrowthLevel.PRESCHOOL);
    }

    @Test
    @DisplayName("HLT-20: nhóm tuổi chưa có tháng ⇒ theo mã mặc định; mã khác hoặc không có nhóm ⇒ không cấp học")
    void levelFallsBackToDefaultCodes() {
        assertThat(HealthGrowthReportServiceImpl.levelOf(group("ag-2", null))).isEqualTo(HealthGrowthLevel.NURSERY);
        assertThat(HealthGrowthReportServiceImpl.levelOf(group(" AG-4 ", null))).isEqualTo(HealthGrowthLevel.PRESCHOOL);
        assertThat(HealthGrowthReportServiceImpl.levelOf(group("lop-ghep", null)))
                .isNull();
        assertThat(HealthGrowthReportServiceImpl.levelOf(group(null, null))).isNull();
        assertThat(HealthGrowthReportServiceImpl.levelOf(null)).isNull();
    }

    private static AgeGroupDetailResponse group(String code, Integer ageFromMonths) {
        return new AgeGroupDetailResponse(
                1L,
                code,
                "Nhóm (giả)",
                "Nhóm",
                (short) 1,
                ageFromMonths,
                ageFromMonths == null ? null : ageFromMonths + 12,
                List.of(),
                0,
                null,
                "");
    }
}
