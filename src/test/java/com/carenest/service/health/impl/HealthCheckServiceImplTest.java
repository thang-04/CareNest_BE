package com.carenest.service.health.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.dto.health.HealthCheckRequest;
import com.carenest.entity.health.HealthCheckCampaign;
import com.carenest.exception.GlobalException;
import com.carenest.repository.health.HealthCheckAbsenceRepository;
import com.carenest.repository.health.HealthCheckCampaignHistoryRepository;
import com.carenest.repository.health.HealthCheckCampaignRepository;
import com.carenest.repository.health.HealthMeasurementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.security.authorization.ClassroomAccessPolicy;
import com.carenest.service.account.AccountService;
import com.carenest.service.child.ChildService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.SchoolStructureService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.HealthCheckKind;
import com.carenest.utils.enums.HealthCheckStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class HealthCheckServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    // Giờ UTC còn ngày 09/11 nhưng giờ trường đã sang ngày 10/11.
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T18:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final CurrentUser PRINCIPAL =
            new CurrentUser(2L, "kc-test", "principal.test", "Giả", null, Set.of(CareNestRole.PRINCIPAL));

    private final CampusAccessPolicy campusAccess = mock(CampusAccessPolicy.class);
    private final SchoolStructureService schoolStructure = mock(SchoolStructureService.class);
    private final HealthCheckCampaignRepository campaigns = mock(HealthCheckCampaignRepository.class);
    private final HealthCheckCampaignHistoryRepository history = mock(HealthCheckCampaignHistoryRepository.class);
    private final HealthCheckRules rules = new HealthCheckRules(
            CLOCK,
            campusAccess,
            mock(ClassroomAccessPolicy.class),
            mock(StaffAssignmentService.class),
            mock(ClassroomService.class),
            schoolStructure,
            mock(EnrollmentService.class),
            campaigns);
    private final HealthCheckServiceImpl service = new HealthCheckServiceImpl(
            rules,
            mock(HealthRecords.class),
            campaigns,
            history,
            mock(HealthCheckAbsenceRepository.class),
            mock(HealthMeasurementRepository.class),
            schoolStructure,
            mock(ChildService.class),
            mock(AccountService.class));

    @BeforeEach
    void setUp() {
        when(campusAccess.canViewCampus(PRINCIPAL, 1L)).thenReturn(true);
        when(schoolStructure.findCampusNames(Set.of(1L))).thenReturn(Map.of(1L, "Điểm giả"));
        when(campaigns.save(any(HealthCheckCampaign.class))).thenAnswer(invocation -> {
            HealthCheckCampaign campaign = invocation.getArgument(0);
            ReflectionTestUtils.setField(campaign, "id", 99L);
            return campaign;
        });
    }

    @ParameterizedTest
    @EnumSource(HealthCheckKind.class)
    void rejectsPastStartDateAccordingToSchoolTime(HealthCheckKind kind) {
        assertThatThrownBy(() -> service.createHealthCheck(PRINCIPAL, request(kind, TODAY.minusDays(1), TODAY)))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                    assertThat(ex.getFieldErrors())
                            .containsExactly(
                                    new FieldErrorResponse("startDate", "Ngày bắt đầu không được trước hôm nay"));
                });
        verify(campaigns, never()).save(any());
        verify(history, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(HealthCheckKind.class)
    void rejectsBothDatesWhenEntirePeriodIsInThePast(HealthCheckKind kind) {
        assertThatThrownBy(() ->
                        service.createHealthCheck(PRINCIPAL, request(kind, TODAY.minusDays(2), TODAY.minusDays(1))))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA);
                    assertThat(ex.getFieldErrors())
                            .containsExactly(
                                    new FieldErrorResponse("startDate", "Ngày bắt đầu không được trước hôm nay"),
                                    new FieldErrorResponse("endDate", "Ngày kết thúc không được trước hôm nay"));
                });
        verify(campaigns, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(HealthCheckKind.class)
    void acceptsTodayAndFutureDates(HealthCheckKind kind) {
        var today = service.createHealthCheck(PRINCIPAL, request(kind, TODAY, TODAY));
        assertThat(today.startDate()).isEqualTo(TODAY);
        assertThat(today.endDate()).isEqualTo(TODAY);
        assertThat(today.status()).isEqualTo(HealthCheckStatus.IN_PROGRESS);

        var future = service.createHealthCheck(PRINCIPAL, request(kind, TODAY.plusDays(1), TODAY.plusDays(2)));
        assertThat(future.startDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(future.endDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(future.status()).isEqualTo(HealthCheckStatus.UPCOMING);
    }

    private static HealthCheckRequest request(HealthCheckKind kind, LocalDate start, LocalDate end) {
        return new HealthCheckRequest(kind, "Đợt/kỳ giả", 1L, start, end, "Đơn vị giả", "");
    }
}
