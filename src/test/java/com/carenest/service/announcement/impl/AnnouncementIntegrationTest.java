package com.carenest.service.announcement.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.carenest.dto.announcement.AnnouncementRequest;
import com.carenest.dto.announcement.AnnouncementResponse;
import com.carenest.dto.announcement.RecipientTargetRequest;
import com.carenest.entity.announcement.SchoolAnnouncement;
import com.carenest.repository.announcement.SchoolAnnouncementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.announcement.AnnouncementService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.enums.AnnouncementScopeType;
import com.carenest.utils.enums.AnnouncementStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.RecipientRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V131 trên PostgreSQL thật: gửi ngay tạo thông báo cho từng người nhận, worker gửi hẹn, gửi lại không trùng. */
@SpringBootTest(properties = {"carenest.push.worker-delay=1h", "carenest.announcement.worker-delay=1h"})
@Testcontainers(disabledWithoutDocker = true)
class AnnouncementIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @MockitoBean
    private RecipientResolver recipientResolver;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private com.carenest.service.announcement.AnnouncementScheduleService scheduleService;

    @MockitoBean
    private com.carenest.service.organization.SchoolYearService schoolYearService;

    @Autowired
    private NotificationService notificationService;

    @MockitoBean
    private com.carenest.integration.storage.FileStorage storage;

    @Autowired
    private com.carenest.repository.announcement.AnnouncementAttachmentRepository attachmentRepository;

    @Autowired
    private SchoolAnnouncementRepository announcementRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private CurrentUser principal;
    private Long parent1;
    private Long parent2;

    @BeforeEach
    void setUp() {
        principal = new CurrentUser(user(), "kc", "ht", "HT", null, Set.of(CareNestRole.PRINCIPAL), Set.of());
        parent1 = user();
        parent2 = user();
        given(recipientResolver.normalize(any())).willAnswer(inv -> inv.getArgument(0));
        given(recipientResolver.canView(any(), any(), any())).willReturn(true);
        given(recipientResolver.resolve(any(), any(), any()))
                .willReturn(Map.of(RecipientRole.PARENT, Set.of(parent1, parent2)));
    }

    private Long user() {
        String key = "ann-" + System.nanoTime();
        return jdbc.queryForObject(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, 'Demo') RETURNING id",
                Long.class,
                key,
                key);
    }

    private int notificationsOf(Long announcementId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE announcement_id = ? AND type = 'SCHOOL_ANNOUNCEMENT'"
                        + " AND push_state = 'PENDING'",
                Integer.class,
                announcementId);
    }

    @Test
    void directScheduleSnapshotSendsFilesAndDeletionKeepsSentFiles() {
        given(recipientResolver.managesWholeSchool(principal)).willReturn(true);
        given(schoolYearService.isSchoolDay(any())).willReturn(true);
        String key = "schedule-" + System.nanoTime() + ".pdf";
        given(storage.store(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .willReturn(new com.carenest.integration.storage.FileStorage.StoredFile(key, "application/pdf", 3));
        var request = new com.carenest.dto.announcement.AnnouncementScheduleRequest(
                null,
                new RecipientTargetRequest(
                        AnnouncementScopeType.SCHOOL, null, null, null, Set.of(RecipientRole.PARENT)),
                com.carenest.utils.enums.ScheduleFrequency.WEEKLY,
                1,
                null,
                java.time.LocalTime.of(8, 0),
                true,
                "Tin trực tiếp",
                "Nội dung riêng",
                List.of());
        var created = scheduleService.createSchedule(
                principal,
                request,
                List.of(new org.springframework.mock.web.MockMultipartFile(
                        "files", "demo.pdf", "application/pdf", new byte[] {1, 2, 3})));
        assertThat(created.templateId()).isNull();
        assertThat(created.attachments()).hasSize(1);
        var fileId = created.attachments().getFirst().id();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> announcementService.downloadAttachment(
                        new CurrentUser(parent1, "kc", "parent", "Parent", null, Set.of(CareNestRole.PARENT), Set.of()),
                        fileId))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        assertThat(scheduleService.activateSchedules(
                        principal,
                        new com.carenest.dto.announcement.ScheduleActivationRequest(List.of(created.id()), false)))
                .isEqualTo(1);
        assertThat(scheduleService.getSchedule(principal, created.id()).active())
                .isFalse();
        assertThat(scheduleService.activateSchedules(
                        principal,
                        new com.carenest.dto.announcement.ScheduleActivationRequest(List.of(created.id()), true)))
                .isEqualTo(1);
        jdbc.update(
                "UPDATE announcement_schedules SET next_run_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                created.id());
        assertThat(scheduleService.runDue(created.id())).isTrue();
        Long sentId = jdbc.queryForObject(
                "SELECT id FROM school_announcements WHERE schedule_id = ?", Long.class, created.id());
        assertThat(attachmentRepository.findByAnnouncementIdOrderByIdAsc(sentId))
                .hasSize(1);
        assertThat(notificationsOf(sentId)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> scheduleService.deleteSchedule(principal, created.id()))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        scheduleService.activateSchedules(
                principal, new com.carenest.dto.announcement.ScheduleActivationRequest(List.of(created.id()), false));
        scheduleService.deleteSchedule(principal, created.id());
        assertThat(attachmentRepository.findByScheduleIdOrderByIdAsc(created.id()))
                .isEmpty();
        assertThat(attachmentRepository.findByAnnouncementIdOrderByIdAsc(sentId))
                .hasSize(1);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never())
                .removeQuietly(com.carenest.utils.enums.FileCategory.ANNOUNCEMENT, key);
    }

    @Test
    void sendNowCreatesOneNotificationPerRecipient() {
        AnnouncementResponse sent = announcementService.createAnnouncement(
                principal,
                new AnnouncementRequest(
                        "Nghỉ lễ",
                        "Trường nghỉ ngày 20/11",
                        new RecipientTargetRequest(
                                AnnouncementScopeType.SCHOOL, null, null, null, Set.of(RecipientRole.PARENT)),
                        null,
                        null),
                null);

        assertThat(sent.status()).isEqualTo(AnnouncementStatus.SENT);
        assertThat(sent.recipientCount()).isEqualTo(2);
        assertThat(notificationsOf(sent.id())).isEqualTo(2);
    }

    @Test
    void filtersAndBulkDeleteApplyScopeAcrossPagesAndCascadeAttachments() {
        String keyword = "cleanup-" + System.nanoTime();
        Long campus = cleanupCampus();
        Long otherCampus = cleanupCampus();
        CurrentUser vice =
                new CurrentUser(user(), "kc", "vp", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of());
        given(recipientResolver.managedCampusIds(vice)).willReturn(Set.of(campus));
        List<Long> matching = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) matching.add(cleanupRow(keyword, campus, AnnouncementStatus.CANCELLED));
        Long outside = cleanupRow(keyword, otherCampus, AnnouncementStatus.CANCELLED);
        Long sent = cleanupRow(keyword, campus, AnnouncementStatus.SENT);
        Long otherTitle = cleanupRow("unrelated-" + keyword, campus, AnnouncementStatus.SCHEDULED);
        Long differentCancelled = cleanupRow("different-" + System.nanoTime(), campus, AnnouncementStatus.CANCELLED);
        Long literal = cleanupRow("literal%_" + System.nanoTime(), campus, AnnouncementStatus.CANCELLED);
        assertThat(announcementService
                        .listAnnouncements(vice, "%_", AnnouncementStatus.CANCELLED, 0, 20)
                        .items())
                .extracting(AnnouncementResponse::id)
                .containsExactly(literal);
        assertThat(announcementService.deleteCancelledAnnouncements(vice, "%_", AnnouncementStatus.CANCELLED))
                .isEqualTo(1);
        attachmentRepository.save(com.carenest.entity.announcement.AnnouncementAttachment.of(
                matching.getFirst(), "demo.pdf", "demo.pdf", "application/pdf", 1, Instant.now()));
        var page = announcementService.listAnnouncements(vice, keyword, AnnouncementStatus.CANCELLED, 0, 20);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.items()).hasSize(20).allMatch(com.carenest.dto.announcement.AnnouncementResponse::canDelete);
        assertThat(announcementService.deleteCancelledAnnouncements(vice, keyword, AnnouncementStatus.CANCELLED))
                .isEqualTo(25);
        assertThat(announcementRepository.findAllById(matching)).isEmpty();
        assertThat(attachmentRepository.findByAnnouncementIdOrderByIdAsc(matching.getFirst()))
                .isEmpty();
        assertThat(announcementRepository.findAllById(List.of(outside, sent, otherTitle, differentCancelled)))
                .hasSize(4);
        org.mockito.Mockito.verify(storage)
                .removeQuietly(com.carenest.utils.enums.FileCategory.ANNOUNCEMENT, "demo.pdf");
    }

    @Test
    void rollbackKeepsAnnouncementAndDoesNotCleanStorage() {
        Long id = cleanupRow("rollback-demo", cleanupCampus(), AnnouncementStatus.CANCELLED);
        String key = "rollback-" + id + ".pdf";
        attachmentRepository.save(com.carenest.entity.announcement.AnnouncementAttachment.of(
                id, key, "demo.pdf", "application/pdf", 1, Instant.now()));
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            announcementService.deleteAnnouncement(principal, id);
            tx.setRollbackOnly();
        });
        assertThat(announcementRepository.existsById(id)).isTrue();
        assertThat(attachmentRepository.findByAnnouncementIdOrderByIdAsc(id)).hasSize(1);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never())
                .removeQuietly(com.carenest.utils.enums.FileCategory.ANNOUNCEMENT, key);
    }

    private Long cleanupCampus() {
        String name = "demo-" + System.nanoTime();
        return jdbc.queryForObject(
                "INSERT INTO campuses (code, name) VALUES (?, ?) RETURNING id", Long.class, name, name);
    }

    @Test
    void selectAllAcrossPagesExcludesUntickedAndExplicitSelectionIsAtomic() {
        String q = "selection-" + System.nanoTime();
        Long campus = cleanupCampus();
        Long otherCampus = cleanupCampus();
        CurrentUser vice =
                new CurrentUser(user(), "kc", "vp", "VP", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of());
        given(recipientResolver.managedCampusIds(vice)).willReturn(Set.of(campus));
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) ids.add(cleanupRow(q, campus, AnnouncementStatus.CANCELLED));
        Long outside = cleanupRow(q, otherCampus, AnnouncementStatus.CANCELLED);
        Long sent = cleanupRow(q, campus, AnnouncementStatus.SENT);
        var page = announcementService.listAnnouncements(vice, q, null, 0, 20);
        assertThat(page.totalElements()).isEqualTo(26);
        assertThat(page.cancelledCount()).isEqualTo(25);
        assertThat(announcementService.deleteSelectedAnnouncements(
                        vice,
                        new com.carenest.dto.announcement.AnnouncementSelectionRequest(
                                true, List.of(), List.of(ids.getFirst()), q, null)))
                .isEqualTo(24);
        assertThat(announcementRepository.existsById(ids.getFirst())).isTrue();
        assertThat(announcementRepository.existsById(outside)).isTrue();
        assertThat(announcementRepository.existsById(sent)).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> announcementService.deleteSelectedAnnouncements(
                        vice,
                        new com.carenest.dto.announcement.AnnouncementSelectionRequest(
                                false, List.of(ids.getFirst(), sent), List.of(), null, null)))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        assertThat(announcementRepository.existsById(ids.getFirst())).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> announcementService.deleteSelectedAnnouncements(
                        vice,
                        new com.carenest.dto.announcement.AnnouncementSelectionRequest(
                                false, List.of(ids.getFirst(), outside), List.of(), null, null)))
                .isInstanceOf(com.carenest.exception.GlobalException.class);
        assertThat(announcementRepository.existsById(ids.getFirst())).isTrue();
        assertThat(announcementService.deleteSelectedAnnouncements(
                        vice,
                        new com.carenest.dto.announcement.AnnouncementSelectionRequest(
                                false, List.of(ids.getFirst()), List.of(), null, null)))
                .isEqualTo(1);
    }

    private Long cleanupRow(String title, Long campus, AnnouncementStatus state) {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            SchoolAnnouncement a = SchoolAnnouncement.create(
                    principal.userId(),
                    title,
                    "Nội dung",
                    new SchoolAnnouncement.Target(
                            AnnouncementScopeType.CAMPUS, campus, null, null, Set.of(RecipientRole.BGH)),
                    Instant.now(),
                    false,
                    null,
                    Instant.now());
            if (state == AnnouncementStatus.CANCELLED) a.cancel(Instant.now());
            if (state == AnnouncementStatus.SENT) a.markSent(0, Instant.now());
            return announcementRepository.save(a).getId();
        });
    }

    @Test
    void dueScheduledAnnouncementIsSentOnceAndResendIsIdempotent() {
        Long id = new TransactionTemplate(transactionManager)
                .execute(s -> announcementRepository
                        .save(SchoolAnnouncement.create(
                                principal.userId(),
                                "Họp phụ huynh",
                                "Thứ Bảy 8h",
                                new SchoolAnnouncement.Target(
                                        AnnouncementScopeType.SCHOOL, null, null, null, Set.of(RecipientRole.PARENT)),
                                Instant.now().minusSeconds(5),
                                false,
                                null,
                                Instant.now()))
                        .getId());

        assertThat(announcementService.findDueAnnouncementIds(10)).contains(id);
        assertThat(announcementService.dispatchDue(id)).isTrue();
        assertThat(announcementService.dispatchDue(id)).isFalse();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(s -> notificationService.notifyAnnouncement(id, List.of(parent1), "x", "y"));

        assertThat(announcementRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(AnnouncementStatus.SENT);
        assertThat(notificationsOf(id)).isEqualTo(2);
    }
}
