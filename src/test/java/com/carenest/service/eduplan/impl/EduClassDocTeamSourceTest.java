package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.ClassCalendarPreviewRequest;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.EduPlanAccessPolicy;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.Constants.EduPermission;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EduClassDocTeamSourceTest {
    @Mock EduPlanRepository yearPlanRepository;
    @Mock EduClassDocRepository classDocRepository;
    @Mock ClassroomService classroomService;
    @Mock EduWorkflowService eduWorkflowService;
    @Mock EduPlanAccessPolicy accessPolicy;
    @InjectMocks EduClassDocServiceImpl service;

    private final ClassroomInfoResponse classroom = new ClassroomInfoResponse(
            1L, "Lớp mẫu", 2L, 3L, null, null, null, 4L, List.of(), null, null);

    @Test
    void calendarPreviewRejectsApprovedAnnualAsClassSource() {
        var user = new CurrentUser(10L, "sample", "sample", "Ví dụ", "", Set.of(CareNestRole.TEACHER));
        EduPlan annual = plan(EduPlanLevel.SCHOOL_YEAR);
        when(accessPolicy.hasPermission(user, EduPermission.GOALS_VIEW)).thenReturn(true);
        when(classroomService.findClassroomInfo(1L)).thenReturn(Optional.of(classroom));
        when(eduWorkflowService.isAuthorEligible(user, EduClassDoc.BRANCH, 4L, 1L)).thenReturn(true);
        when(yearPlanRepository.findById(20L)).thenReturn(Optional.of(annual));

        assertThatThrownBy(() -> service.previewClassCalendar(
                        user, new ClassCalendarPreviewRequest(1L, 20L, -1L, null, null)))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.YEAR_PLAN_CLASS_MISMATCH);
    }

    @Test
    void approvedTeamIsAcceptedAndDraftTeamIsRejected() {
        EduPlan team = plan(EduPlanLevel.TEAM);
        when(yearPlanRepository.findById(20L)).thenReturn(Optional.of(team));
        when(team.getSchoolYearId()).thenReturn(3L);
        when(team.getAgeGroupId()).thenReturn(4L);
        when(team.getStatus()).thenReturn(EduPlanStatus.APPROVED, EduPlanStatus.DRAFT);

        assertThat((EduPlan) ReflectionTestUtils.invokeMethod(service, "requireIssuedPlan", 20L, classroom))
                .isSameAs(team);
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "requireIssuedPlan", 20L, classroom))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.YEAR_PLAN_NOT_ISSUED);
    }

    @Test
    void annualPublicationAndDraftTeamUpdateDoNotSupersedeIssuedTeam() {
        EduPlan team = plan(EduPlanLevel.TEAM);
        EduPlan annual = plan(EduPlanLevel.SCHOOL_YEAR);
        EduPlan update = plan(EduPlanLevel.TEAM);
        when(yearPlanRepository.findById(20L)).thenReturn(Optional.of(team));
        when(team.getSchoolYearId()).thenReturn(3L);
        when(team.getAgeGroupId()).thenReturn(4L);
        when(team.getVersionNo()).thenReturn(1);
        when(update.getStatus()).thenReturn(EduPlanStatus.DRAFT, EduPlanStatus.APPROVED);
        when(update.getVersionNo()).thenReturn(2);
        when(yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                        EduPlanLevel.TEAM, 3L, 4L))
                .thenReturn(List.of(annual, update));

        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isSuperseded", 20L)).isFalse();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isSuperseded", 20L)).isTrue();
    }

    @Test
    void copyRejectsAnnualSourceEvenWhenYearAgeAndVersionMatch() {
        EduClassDoc source = mock(EduClassDoc.class);
        EduPlan annual = plan(EduPlanLevel.SCHOOL_YEAR);
        EduPlan target = mock(EduPlan.class);
        when(classDocRepository.findByDocKey("bp_1_20")).thenReturn(Optional.of(source));
        when(source.getYearPlanId()).thenReturn(20L);
        when(source.getKind()).thenReturn(EduClassDoc.BRANCH);
        when(source.getClassroomId()).thenReturn(1L);
        when(yearPlanRepository.findById(20L)).thenReturn(Optional.of(annual));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                        service, "requireCopySource", "bp_1_20", EduClassDoc.BRANCH, 1L, target))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CLASS_DOC_KEY_MISMATCH);
    }

    @Test
    void versionedWeekKeyKeepsLegacyApiFormat() {
        EduPlan team = mock(EduPlan.class);
        when(team.getId()).thenReturn(20L);
        when(team.getVersionNo()).thenReturn(1, 2);
        LocalDate monday = LocalDate.of(2026, 11, 9);

        assertThat((String) ReflectionTestUtils.invokeMethod(
                        service, "expectedKey", EduClassDoc.WEEK, 1L, team, null, monday))
                .isEqualTo("wp_1_2026-11-09");
        assertThat((String) ReflectionTestUtils.invokeMethod(
                        service, "expectedKey", EduClassDoc.WEEK, 1L, team, null, monday))
                .isEqualTo("wp_1_2026-11-09_20");
    }

    private static EduPlan plan(EduPlanLevel level) {
        EduPlan plan = mock(EduPlan.class);
        when(plan.getPlanLevel()).thenReturn(level);
        return plan;
    }
}
