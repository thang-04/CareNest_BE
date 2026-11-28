package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.entity.eduplan.EduContentThemeTick;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.entity.eduplan.EduTeamReviewItem;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.repository.eduplan.EduBreakRepository;
import com.carenest.repository.eduplan.EduContentThemeTickRepository;
import com.carenest.repository.eduplan.EduGoalContentRepository;
import com.carenest.repository.eduplan.EduPlanBranchTemplateRepository;
import com.carenest.repository.eduplan.EduPlanExpectedResultRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.repository.eduplan.EduPlanRepository;
import com.carenest.repository.eduplan.EduPlanSubDomainRepository;
import com.carenest.repository.eduplan.EduTeamReviewItemRepository;
import com.carenest.repository.eduplan.EduThemeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EduTeamReviewSaveTest {
    @Test
    void savesOnlyRequestedCurrentSourceItemsWithValidLinks() {
        var plans = mock(EduPlanRepository.class);
        var themes = mock(EduThemeRepository.class);
        var ticks = mock(EduContentThemeTickRepository.class);
        var reviews = mock(EduTeamReviewItemRepository.class);
        Instant now = Instant.parse("2026-11-28T00:00:00Z");
        var service = new EduTeamPlanServiceImpl(
                plans,
                mock(EduPlanSubDomainRepository.class),
                mock(EduPlanExpectedResultRepository.class),
                mock(EduPlanGoalRepository.class),
                mock(EduGoalContentRepository.class),
                themes,
                mock(EduBreakRepository.class),
                ticks,
                mock(EduPlanBranchTemplateRepository.class),
                reviews,
                Clock.fixed(now, ZoneOffset.UTC));

        var team = mock(EduPlan.class);
        var currentSource = mock(EduPlan.class);
        when(team.getId()).thenReturn(1L);
        when(team.getCurriculumSourcePlanId()).thenReturn(2L);
        when(currentSource.getId()).thenReturn(2L);
        when(plans.findById(2L)).thenReturn(Optional.of(currentSource));
        var theme = mock(EduTheme.class);
        when(theme.getId()).thenReturn(101L);
        when(themes.findAllByYearPlanIdOrderBySortOrderAsc(1L)).thenReturn(List.of(theme));
        when(ticks.findAllByThemeIdIn(Set.of(101L))).thenReturn(List.of(EduContentThemeTick.of(22L, 101L)));
        var valid = reviewItem(11L, 2L, 21L);
        var invalid = reviewItem(12L, 2L, 22L);
        var untouched = reviewItem(13L, 2L, 23L);
        var stale = reviewItem(14L, 3L, 24L);
        when(reviews.findAllByTeamPlanIdOrderByIdAsc(1L)).thenReturn(List.of(valid, invalid, untouched, stale));

        service.resolveOnSave(team, List.of(11L, 12L, 14L, 99L), 4L);

        assertThat(valid.isResolved()).isTrue();
        assertThat(valid.getResolvedBy()).isEqualTo(4L);
        assertThat(valid.getResolvedAt()).isEqualTo(now);
        assertThat(invalid.isResolved()).isFalse();
        assertThat(untouched.isResolved()).isFalse();
        assertThat(stale.isResolved()).isFalse();
        verify(reviews).saveAll(List.of(valid));
    }

    private static EduTeamReviewItem reviewItem(long id, long sourceId, long contentId) {
        var item = EduTeamReviewItem.create(1L, sourceId, "CONTENT", contentId, "SOURCE_CHANGED");
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
