package com.carenest.service.announcement.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.announcement.AnnouncementScheduleRequest;
import com.carenest.dto.announcement.RecipientTargetRequest;
import com.carenest.entity.announcement.AnnouncementSchedule;
import com.carenest.entity.announcement.AnnouncementTemplate;
import com.carenest.entity.announcement.SchoolAnnouncement;
import com.carenest.exception.GlobalException;
import com.carenest.repository.announcement.AnnouncementScheduleRepository;
import com.carenest.repository.announcement.AnnouncementTemplateRepository;
import com.carenest.repository.announcement.SchoolAnnouncementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.notification.NotificationService;
import com.carenest.service.organization.SchoolYearService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AnnouncementScopeType;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.RecipientRole;
import com.carenest.utils.enums.ScheduleFrequency;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Lịch gửi lặp (PAR-10): ngày nghỉ bỏ lần đó và chuyển lần kế tiếp; chỉ HT. */
class AnnouncementScheduleServiceImplTest {
    // Thứ Hai 16/11/2026 07:00 giờ Việt Nam
    private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final CurrentUser HT =
            new CurrentUser(1L, "kc", "ht", "HT", null, Set.of(CareNestRole.PRINCIPAL), Set.of());

    private AnnouncementScheduleRepository schedules;
    private AnnouncementTemplateRepository templates;
    private SchoolAnnouncementRepository announcements;
    private RecipientResolver resolver;
    private NotificationService notifications;
    private SchoolYearService schoolYears;
    private AnnouncementScheduleServiceImpl service;
    private AnnouncementSchedule schedule;

    @BeforeEach
    void setUp() {
        schedules = mock(AnnouncementScheduleRepository.class);
        templates = mock(AnnouncementTemplateRepository.class);
        announcements = mock(SchoolAnnouncementRepository.class);
        resolver = mock(RecipientResolver.class);
        notifications = mock(NotificationService.class);
        schoolYears = mock(SchoolYearService.class);
        service = new AnnouncementScheduleServiceImpl(
                schedules, templates, announcements, resolver, notifications, schoolYears, Clock.fixed(NOW, VN));
        AnnouncementTemplate template =
                AnnouncementTemplate.create("Nhắc học phí", "Học phí", "Nội dung", null, 1L, NOW);
        ReflectionTestUtils.setField(template, "id", 3L);
        given(templates.findById(3L)).willReturn(Optional.of(template));
        given(resolver.managesWholeSchool(HT)).willReturn(true);
        given(resolver.normalize(any())).willAnswer(inv -> inv.getArgument(0));
        given(resolver.resolve(any(), any(), any())).willReturn(Map.of(RecipientRole.PARENT, Set.of(500L)));
        given(announcements.save(any())).willAnswer(inv -> {
            SchoolAnnouncement a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 9L);
            return a;
        });
        schedule = AnnouncementSchedule.create(1L, NOW);
        schedule.edit(
                3L,
                new SchoolAnnouncement.Target(
                        AnnouncementScopeType.SCHOOL, null, null, null, Set.of(RecipientRole.PARENT)),
                ScheduleFrequency.WEEKLY,
                1,
                null,
                LocalTime.of(7, 0),
                true,
                NOW,
                NOW);
        ReflectionTestUtils.setField(schedule, "id", 4L);
        given(schedules.lockDue(4L, NOW)).willReturn(Optional.of(schedule));
    }

    @Test
    void schoolDaySendsFromTemplateAndMovesToNextWeek() {
        given(schoolYears.isSchoolDay(LocalDate.of(2026, 11, 16))).willReturn(true);

        assertThat(service.runDue(4L)).isTrue();

        verify(notifications).notifyAnnouncement(eq(9L), eq(Set.of(500L)), eq("Học phí"), anyString());
        assertThat(schedule.getNextRunAt()).isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
    }

    @Test
    void holidayIsSkippedButScheduleStillAdvances() {
        given(schoolYears.isSchoolDay(LocalDate.of(2026, 11, 16))).willReturn(false);

        assertThat(service.runDue(4L)).isTrue();

        verify(notifications, never()).notifyAnnouncement(anyLong(), anyCollection(), anyString(), anyString());
        verify(announcements, never()).save(any());
        assertThat(schedule.getNextRunAt()).isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
    }

    @Test
    void scheduleHeldByAnotherServerIsSkipped() {
        given(schedules.lockDue(4L, NOW)).willReturn(Optional.empty());

        assertThat(service.runDue(4L)).isFalse();
    }

    @Test
    void createComputesFirstRun() {
        given(schedules.save(any())).willAnswer(inv -> inv.getArgument(0));

        var created = service.createSchedule(
                HT,
                new AnnouncementScheduleRequest(
                        3L,
                        new RecipientTargetRequest(
                                AnnouncementScopeType.SCHOOL, null, null, null, Set.of(RecipientRole.PARENT)),
                        ScheduleFrequency.MONTHLY,
                        null,
                        31,
                        LocalTime.of(8, 0),
                        null));

        assertThat(created.nextRunAt()).isEqualTo(Instant.parse("2026-11-30T01:00:00Z"));
        assertThat(created.active()).isTrue();
    }

    @Test
    void vicePrincipalCannotManageSchedules() {
        CurrentUser pht = new CurrentUser(2L, "kc", "pht", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of());

        assertThatThrownBy(() -> service.listSchedules(pht))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.FORBIDDEN));
    }
}
