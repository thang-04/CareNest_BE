package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.carenest.entity.eduplan.*;
import com.carenest.repository.eduplan.*;
import com.carenest.service.account.AccountService;
import com.carenest.service.eduplan.EduTeamPlanService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EduTeamAutoDraftTest {
    @Mock
    EduPlanRepository plans;

    @Mock
    EduPlanSubDomainRepository subDomainRepository;

    @Mock
    EduPlanExpectedResultRepository expectedResultRepository;

    @Mock
    EduPlanGoalRepository goalRepository;

    @Mock
    EduGoalContentRepository goalContentRepository;

    @Mock
    EduThemeRepository themeRepository;

    @Mock
    EduBreakRepository breakRepository;

    @Mock
    EduContentThemeTickRepository tickRepository;

    @Mock
    EduPlanBranchTemplateRepository templateRepository;

    @Mock
    EduTeamPlanService teamService;

    @Mock
    StaffAssignmentService assignments;

    @Mock
    AccountService accounts;

    @Mock
    Clock clock;

    @Mock
    EduPlanTimeLock timeLock;

    @InjectMocks
    EduTeamUpdateGenerator generator;

    @Test
    void repeatedPublicationDoesNotCreateAnotherTeamAndOlderAnnualNeverRebasesBackwards() {
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        EduPlan annual = annual(now);
        EduPlan team = EduPlan.createDraft("TEAM", 1L, 2L, "Tổ", "", 7L, "TT", now);
        team.initializePlanSource(EduPlanLevel.TEAM, annual.getId(), false, annual.getDurationPolicySnapshot());
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(EduPlanLevel.SCHOOL_YEAR, 1L, 2L))
                .thenReturn(List.of(annual));
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(EduPlanLevel.TEAM, 1L, 2L))
                .thenReturn(List.of(team));
        generator.updateAfterPublication(annual, now);
        verify(plans, never()).saveAndFlush(any());
        verifyNoInteractions(teamService, assignments);
        EduPlan later = annual(now);
        ReflectionTestUtils.setField(later, "id", 20L);
        ReflectionTestUtils.setField(later, "versionNo", 2);
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(EduPlanLevel.SCHOOL_YEAR, 1L, 2L))
                .thenReturn(List.of(annual, later));
        generator.updateAfterPublication(annual, now);
        verifyNoInteractions(teamService, assignments);
        assertThat(team.getCurriculumSourcePlanId()).isEqualTo(10L);
    }

    private EduPlan annual(Instant now) {
        var annual = EduPlan.createDraft(
                EduPlanLevel.SCHOOL_YEAR, "KHN-KHNH-2627-MGL", 1L, 2L, "Kế hoạch năm học", "", 9L, "PHT", now);
        ReflectionTestUtils.setField(annual, "id", 10L);
        annual.initializePlanSource(EduPlanLevel.SCHOOL_YEAR, null, false, "{\"mode\":\"LEGACY_UNBOUNDED\"}");
        annual.issue(8L, now);
        return annual;
    }

    @Test
    void firstPublishedAnnualAutomaticallyCreatesOneDraftAssignedToItsAgeGroupLeader() {
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        var annual = EduPlan.createDraft(
                EduPlanLevel.SCHOOL_YEAR, "KHN-KHNH-2627-MGL", 1L, 2L, "Kế hoạch năm học", "Mô tả", 9L, "PHT", now);
        ReflectionTestUtils.setField(annual, "id", 10L);
        annual.initializePlanSource(EduPlanLevel.SCHOOL_YEAR, null, false, "{\"mode\":\"LEGACY_UNBOUNDED\"}");
        annual.issue(8L, now);
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(EduPlanLevel.SCHOOL_YEAR, 1L, 2L))
                .thenReturn(List.of(annual));
        when(plans.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(EduPlanLevel.TEAM, 1L, 2L))
                .thenReturn(List.of());
        lenient()
                .when(assignments.findActiveTeamLeaderIds(eq(1L), eq(2L), any()))
                .thenReturn(Set.of(7L));
        lenient().when(accounts.findActiveUserIds(Set.of(7L))).thenReturn(Set.of(7L));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        lenient().when(plans.saveAndFlush(any())).thenAnswer(call -> {
            EduPlan target = call.getArgument(0);
            ReflectionTestUtils.setField(target, "id", 11L);
            when(plans.findById(11L)).thenReturn(Optional.of(target));
            return target;
        });
        generator.updateAfterPublication(annual, now);
        var captor = ArgumentCaptor.forClass(EduPlan.class);
        verify(plans).saveAndFlush(captor.capture());
        var team = captor.getValue();
        assertThat(team.getPlanLevel()).isEqualTo(EduPlanLevel.TEAM);
        assertThat(team.getStatus()).isEqualTo(EduPlanStatus.DRAFT);
        assertThat(team.getOwnerId()).isEqualTo(7L);
        assertThat(team.getCurriculumSourcePlanId()).isEqualTo(10L);
        assertThat(team.getSchoolYearId()).isEqualTo(1L);
        assertThat(team.getAgeGroupId()).isEqualTo(2L);
        assertThat(team.getTitle()).contains("Kế hoạch giáo dục tổ");
    }
}
