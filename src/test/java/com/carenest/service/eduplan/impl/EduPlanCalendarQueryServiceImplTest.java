package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.service.eduplan.EduPlanCalendarQueryService.EffectiveTheme;
import com.carenest.service.organization.ClassroomService;
import com.carenest.utils.enums.EduPlanLevel;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EduPlanCalendarQueryServiceImplTest {

    private ClassroomService classroomService;
    private EduPlanRepository yearPlanRepository;
    private EduClassDocRepository classDocRepository;
    private EduThemeRepository themeRepository;
    private ObjectMapper objectMapper;
    private EduPlanCalendarQueryServiceImpl queryService;

    @BeforeEach
    void setUp() {
        classroomService = mock(ClassroomService.class);
        yearPlanRepository = mock(EduPlanRepository.class);
        classDocRepository = mock(EduClassDocRepository.class);
        themeRepository = mock(EduThemeRepository.class);
        objectMapper = new ObjectMapper();
        queryService = new EduPlanCalendarQueryServiceImpl(
                classroomService, yearPlanRepository, classDocRepository, themeRepository, objectMapper);
    }

    @Test
    @DisplayName("T049: classroom not found returns empty list")
    void classroomNotFound() {
        when(classroomService.findClassroomInfo(999L)).thenReturn(Optional.empty());
        List<EffectiveTheme> result = queryService.findApprovedThemes(999L);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("T049: no approved TEAM plan returns empty list")
    void noApprovedTeamPlan() {
        ClassroomInfoResponse cls = mock(ClassroomInfoResponse.class);
        when(cls.schoolYearId()).thenReturn(10L);
        when(cls.ageGroupId()).thenReturn(30L);
        when(classroomService.findClassroomInfo(1L)).thenReturn(Optional.of(cls));
        when(yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                        EduPlanLevel.TEAM, 10L, 30L))
                .thenReturn(List.of());

        List<EffectiveTheme> result = queryService.findApprovedThemes(1L);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("T049: fallback to TEAM themes when no approved branch doc")
    void fallbackToTeamThemes() {
        ClassroomInfoResponse cls = mock(ClassroomInfoResponse.class);
        when(cls.schoolYearId()).thenReturn(10L);
        when(cls.ageGroupId()).thenReturn(30L);
        when(classroomService.findClassroomInfo(1L)).thenReturn(Optional.of(cls));

        EduPlan plan = mock(EduPlan.class);
        when(plan.getId()).thenReturn(50L);
        when(plan.getStatus()).thenReturn(EduPlanStatus.APPROVED);

        when(yearPlanRepository.findAllByPlanLevelAndSchoolYearIdAndAgeGroupIdOrderByVersionNoAsc(
                        EduPlanLevel.TEAM, 10L, 30L))
                .thenReturn(List.of(plan));

        EduTheme t1 = mock(EduTheme.class);
        when(t1.getId()).thenReturn(101L);
        when(t1.getName()).thenReturn("Chủ đề 1");
        when(t1.lineage()).thenReturn(101L);
        when(t1.getStartDate()).thenReturn(LocalDate.of(2026, 9, 7));
        when(t1.getEndDate()).thenReturn(LocalDate.of(2026, 9, 18));

        when(themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(50L)).thenReturn(List.of(t1));
        when(classDocRepository.findAllByClassroomIdAndYearPlanId(1L, 50L)).thenReturn(List.of());

        List<EffectiveTheme> result = queryService.findApprovedThemes(1L);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().themeId()).isEqualTo(101L);
        assertThat(result.getFirst().name()).isEqualTo("Chủ đề 1");
        assertThat(result.getFirst().startDate()).isEqualTo(LocalDate.of(2026, 9, 7));
    }
}
