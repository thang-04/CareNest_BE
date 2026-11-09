package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.entity.eduplan.EduPlan;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EduYearPlanEditingTest {
    @Test
    void onlyDesignatedAuthorCanEditWithoutLeaseAndRevisionIsStillRequired() {
        var plan = mock(EduPlan.class);
        var author = mock(CurrentUser.class);
        var other = mock(CurrentUser.class);
        when(plan.getOwnerId()).thenReturn(1L);
        when(author.userId()).thenReturn(1L);
        when(other.userId()).thenReturn(2L);
        var edit = new YearPlanEditRequest(null, 3L, UUID.randomUUID());
        EduYearPlanServiceImpl.requireDesignatedAuthor(author, plan, edit);
        assertThatThrownBy(() -> EduYearPlanServiceImpl.requireDesignatedAuthor(other, plan, edit))
                .isInstanceOf(GlobalException.class);
        assertThatThrownBy(() -> EduYearPlanServiceImpl.requireDesignatedAuthor(author, plan,
                new YearPlanEditRequest(null, null, UUID.randomUUID()))).isInstanceOf(GlobalException.class);
    }
}
