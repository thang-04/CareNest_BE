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
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @MockitoBean
    private RecipientResolver recipientResolver;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private NotificationService notificationService;

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
