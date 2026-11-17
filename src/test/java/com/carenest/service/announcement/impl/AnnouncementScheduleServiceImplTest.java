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
    private com.carenest.repository.announcement.AnnouncementAttachmentRepository attachments;
    private com.carenest.integration.storage.FileStorage storage;
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
        attachments = mock(com.carenest.repository.announcement.AnnouncementAttachmentRepository.class);
        storage = mock(com.carenest.integration.storage.FileStorage.class);
        service = new AnnouncementScheduleServiceImpl(
                schedules,
                templates,
                announcements,
                resolver,
                notifications,
                schoolYears,
                Clock.fixed(NOW, VN),
                attachments,
                storage);
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
        assertThat(created.title()).isEqualTo("Học phí");
    }

    private AnnouncementScheduleRequest ownRequest(Long templateId) {
        return new AnnouncementScheduleRequest(
                templateId,
                new RecipientTargetRequest(
                        AnnouncementScopeType.SCHOOL, null, null, null, Set.of(RecipientRole.PARENT)),
                ScheduleFrequency.WEEKLY,
                1,
                null,
                LocalTime.of(8, 0),
                true,
                "Nội dung riêng",
                "Bản lưu độc lập",
                java.util.List.of());
    }

    @Test
    void directContentDoesNotRequireTemplateAndUploadsLikeCompose() {
        given(schedules.save(any())).willAnswer(inv -> {
            AnnouncementSchedule s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 8L);
            return s;
        });
        given(storage.store(any(), any(), anyString(), any(), anyLong()))
                .willReturn(
                        new com.carenest.integration.storage.FileStorage.StoredFile("own.pdf", "application/pdf", 3));
        var result = service.createSchedule(
                HT,
                ownRequest(null),
                java.util.List.of(new org.springframework.mock.web.MockMultipartFile(
                        "files", "demo.pdf", "application/pdf", new byte[] {1, 2, 3})));
        assertThat(result.templateId()).isNull();
        assertThat(result.title()).isEqualTo("Nội dung riêng");
        var file = org.mockito.ArgumentCaptor.forClass(com.carenest.entity.announcement.AnnouncementAttachment.class);
        verify(attachments).save(file.capture());
        assertThat(file.getValue().getScheduleId()).isEqualTo(8L);
        assertThat(file.getValue().getAnnouncementId()).isNull();
        verify(templates, never()).findById(any());
    }

    @Test
    void workerUsesSnapshotEvenWhenTemplateChangesAndClonesAttachments() {
        schedule.content("Bản cũ", "Nội dung đã lưu");
        given(schoolYears.isSchoolDay(any())).willReturn(true);
        given(attachments.findByScheduleIdOrderByIdAsc(4L))
                .willReturn(java.util.List.of(com.carenest.entity.announcement.AnnouncementAttachment.forSchedule(
                        4L, "shared.pdf", "demo.pdf", "application/pdf", 3, NOW)));
        service.runDue(4L);
        verify(notifications).notifyAnnouncement(9L, Set.of(500L), "Bản cũ", "Nội dung đã lưu");
        var file = org.mockito.ArgumentCaptor.forClass(com.carenest.entity.announcement.AnnouncementAttachment.class);
        verify(attachments).save(file.capture());
        assertThat(file.getValue().getAnnouncementId()).isEqualTo(9L);
        assertThat(file.getValue().getScheduleId()).isNull();
        assertThat(file.getValue().getFileKey()).isEqualTo("shared.pdf");
    }

    @Test
    void bulkActivationOnlyChangesSelectedAndRejectsMissingIdBeforeMutation() {
        given(schedules.lockByIds(Set.of(4L, 999L))).willReturn(java.util.List.of(schedule));
        assertThatThrownBy(() -> service.activateSchedules(
                        HT,
                        new com.carenest.dto.announcement.ScheduleActivationRequest(
                                java.util.List.of(4L, 999L), false)))
                .isInstanceOf(GlobalException.class);
        assertThat(schedule.isActive()).isTrue();
        given(schedules.lockByIds(Set.of(4L))).willReturn(java.util.List.of(schedule));
        assertThat(service.activateSchedules(
                        HT, new com.carenest.dto.announcement.ScheduleActivationRequest(java.util.List.of(4L), false)))
                .isEqualTo(1);
        assertThat(schedule.isActive()).isFalse();
        assertThat(service.activateSchedules(
                        HT, new com.carenest.dto.announcement.ScheduleActivationRequest(java.util.List.of(4L), true)))
                .isEqualTo(1);
        assertThat(schedule.isActive()).isTrue();
        assertThat(schedule.getNextRunAt()).isAfter(NOW);
    }

    @Test
    void deletingScheduleKeepsFileUsedBySentAnnouncement() {
        schedule.activate(false, schedule.getNextRunAt(), NOW);
        given(schedules.lockByIds(Set.of(4L))).willReturn(java.util.List.of(schedule));
        given(attachments.findByScheduleIdIn(Set.of(4L)))
                .willReturn(java.util.List.of(com.carenest.entity.announcement.AnnouncementAttachment.forSchedule(
                        4L, "shared.pdf", "demo.pdf", "application/pdf", 3, NOW)));
        given(attachments.existsByFileKey("shared.pdf")).willReturn(true);
        service.deleteSchedule(HT, 4L);
        verify(storage, never()).removeQuietly(any(), any());
    }

    @Test
    void activeScheduleCannotBeDeleted() {
        given(schedules.lockByIds(Set.of(4L))).willReturn(java.util.List.of(schedule));

        assertThatThrownBy(() -> service.deleteSchedule(HT, 4L))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.ANNOUNCEMENT_SCHEDULE_ACTIVE));
        verify(schedules, never()).deleteAll(any());
    }

    @Test
    void deletingSelectionWithMissingIdDeletesNothing() {
        given(schedules.lockByIds(Set.of(4L, 99L))).willReturn(java.util.List.of(schedule));

        assertThatThrownBy(() -> service.deleteSchedules(HT, java.util.List.of(4L, 99L)))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.ANNOUNCEMENT_SCHEDULE_NOT_FOUND));
        verify(schedules, never()).deleteAll(any());
    }

    @Test
    void vicePrincipalCannotManageSchedules() {
        CurrentUser pht = new CurrentUser(2L, "kc", "pht", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of());

        assertThatThrownBy(() -> service.listSchedules(pht))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.FORBIDDEN));
    }
}
