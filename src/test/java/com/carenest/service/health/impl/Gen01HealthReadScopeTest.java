package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.organization.CampusOptionResponse;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Gen01HealthReadScopeTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T03:00:00Z"), ZoneOffset.UTC);
    private final CurrentUser vice =
            new CurrentUser(3L, "kc-vp", "vp.test", "PHT giả", null, Set.of(CareNestRole.VICE_PRINCIPAL));
    private final StaffAssignmentService assignments = mock(StaffAssignmentService.class);
    private final CampusAccessPolicy campusAccess = new CampusAccessPolicy(assignments, clock);
    private final SchoolStructureService structure = mock(SchoolStructureService.class);
    private final HealthCheckCampaignRepository campaigns = mock(HealthCheckCampaignRepository.class);
    private final HealthCheckRules rules = new HealthCheckRules(
            clock,
            campusAccess,
            mock(ClassroomAccessPolicy.class),
            assignments,
            mock(com.carenest.service.organization.ClassroomService.class),
            structure,
            mock(com.carenest.service.child.EnrollmentService.class),
            campaigns,
            mock(com.carenest.service.organization.SchoolYearService.class),
            mock(com.carenest.service.attendance.AttendanceService.class));

    private void assignVice() {
        when(assignments.findActiveCampusIds(3L, StaffRole.VICE_PRINCIPAL, LocalDate.of(2026, 11, 27)))
                .thenReturn(Set.of(1L));
    }

    @Test
    @DisplayName("GEN-01/HLT-01: PHT xem đợt của điểm trường khác, không quản lý được")
    void readsOtherCampusWithoutManage() {
        assignVice();
        HealthCheckCampaign campaign = mock(HealthCheckCampaign.class);
        when(campaign.getCampusId()).thenReturn(2L);
        when(campaigns.findById(20L)).thenReturn(Optional.of(campaign));
        assertThat(rules.findVisible(vice, 20L)).isSameAs(campaign);
        assertThat(rules.canManage(vice, 2L)).isFalse();
        assertThatThrownBy(() -> rules.findManageable(vice, 20L))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.HEALTH_CHECK_ACCESS_DENIED));
    }

    @Test
    @DisplayName("GEN-01: PHT hết phân công không xem đợt ở điểm trường khác")
    void noAssignmentCannotRead() {
        HealthCheckCampaign campaign = mock(HealthCheckCampaign.class);
        when(campaign.getCampusId()).thenReturn(2L);
        when(campaigns.findById(20L)).thenReturn(Optional.of(campaign));
        assertThatThrownBy(() -> rules.findVisible(vice, 20L)).isInstanceOf(GlobalException.class);
    }

    @Test
    @DisplayName("GEN-01/HLT-12: PHT đọc báo cáo toàn trường và lọc điểm trường khác")
    void readsWholeSchoolGrowthReportAndFiltersOtherCampus() {
        assignVice();
        when(structure.findCampusOptions())
                .thenReturn(List.of(
                        new CampusOptionResponse(1L, "a", "Điểm giả A"),
                        new CampusOptionResponse(2L, "b", "Điểm giả B")));
        var service = new HealthGrowthReportServiceImpl(
                rules,
                campaigns,
                mock(com.carenest.repository.health.HealthMeasurementRepository.class),
                mock(com.carenest.repository.health.HealthCheckAbsenceRepository.class),
                structure,
                mock(com.carenest.service.organization.AgeGroupService.class),
                mock(com.carenest.service.child.ChildService.class),
                mock(com.carenest.service.organization.CampusService.class));
        assertThat(service.getGrowthReport(vice, YearMonth.of(2026, 11), null).campuses())
                .extracting(c -> c.campusId())
                .containsExactly(1L, 2L);
        assertThat(service.getGrowthReport(vice, YearMonth.of(2026, 11), 2L).campuses())
                .extracting(c -> c.campusId())
                .containsExactly(2L);
    }
}
