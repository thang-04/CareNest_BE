package com.carenest.service.observation.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carenest.dto.eduplan.DayActivity;
import com.carenest.dto.observation.ParentActivityDtos.ParentDayActivitiesResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.eduplan.EduLessonQueryService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParentActivityServiceImplTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);

    private final GuardianChildLinkService links = mock(GuardianChildLinkService.class);
    private final EnrollmentService enrollments = mock(EnrollmentService.class);
    private final EduLessonQueryService lessons = mock(EduLessonQueryService.class);
    private final ParentActivityServiceImpl service = new ParentActivityServiceImpl(
            links,
            enrollments,
            lessons,
            Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

    private static CurrentUser parent() {
        return new CurrentUser(
                9L,
                "kc-9",
                "0900000009",
                "Phụ huynh",
                null,
                Set.of(CareNestRole.PARENT),
                TestPermissions.defaultsOf(CareNestRole.PARENT));
    }

    @Test
    @DisplayName("AUTH-04: phụ huynh không liên kết với trẻ ⇒ 4108, không đọc giáo án")
    void otherParentForbidden() {
        when(links.isGuardianOf(9L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.getDayActivities(parent(), 5L, DAY))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.PARENT_CHILD_FORBIDDEN));
        verify(lessons, never()).findDayActivities(any(), any());
    }

    @Test
    @DisplayName("EDU-37: hoạt động của giáo án ngày đã ban hành của lớp trẻ học ngày đó")
    void activitiesOfChildClassThatDay() {
        when(links.isGuardianOf(9L, 5L)).thenReturn(true);
        when(enrollments.findActiveClassroomIds(5L, DAY)).thenReturn(List.of(12L));
        List<DayActivity> activities = List.of(new DayActivity("Hoạt động học", List.of("Bật xa")));
        when(lessons.findDayActivities(12L, DAY)).thenReturn(activities);

        ParentDayActivitiesResponse response = service.getDayActivities(parent(), 5L, DAY);

        assertThat(response.date()).isEqualTo(DAY);
        assertThat(response.activities()).isEqualTo(activities);
    }

    @Test
    @DisplayName("EDU-37: ngày trẻ chưa có lớp ⇒ danh sách rỗng; không truyền ngày ⇒ hôm nay")
    void noClassThatDayIsEmpty() {
        when(links.isGuardianOf(9L, 5L)).thenReturn(true);
        LocalDate today = LocalDate.of(2026, 11, 26);
        when(enrollments.findActiveClassroomIds(5L, today)).thenReturn(List.of());

        ParentDayActivitiesResponse response = service.getDayActivities(parent(), 5L, null);

        assertThat(response.date()).isEqualTo(today);
        assertThat(response.activities()).isEmpty();
    }
}
