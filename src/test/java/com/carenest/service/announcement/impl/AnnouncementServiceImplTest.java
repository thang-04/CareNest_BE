package com.carenest.service.announcement.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.announcement.AnnouncementRequest;
import com.carenest.dto.announcement.AnnouncementResponse;
import com.carenest.dto.announcement.RecipientTargetRequest;
import com.carenest.entity.announcement.AnnouncementAttachment;
import com.carenest.entity.announcement.SchoolAnnouncement;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.announcement.AnnouncementAttachmentRepository;
import com.carenest.repository.announcement.SchoolAnnouncementRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.notification.NotificationService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AnnouncementScopeType;
import com.carenest.utils.enums.AnnouncementStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.RecipientRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

/** BGH gửi thông báo của trường (spec 019 US2): gửi ngay, hẹn, sửa/hủy, giới hạn tệp, quyền tải tệp. */
class AnnouncementServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");
    private static final CurrentUser HT =
            new CurrentUser(1L, "kc-1", "ht", "Hiệu trưởng", null, Set.of(CareNestRole.PRINCIPAL), Set.of());
    private static final RecipientTargetRequest CLASS_PARENTS =
            new RecipientTargetRequest(AnnouncementScopeType.CLASSROOM, null, null, 10L, Set.of(RecipientRole.PARENT));

    private SchoolAnnouncementRepository announcements;
    private AnnouncementAttachmentRepository attachments;
    private RecipientResolver resolver;
    private NotificationService notifications;
    private FileStorage storage;
    private AnnouncementServiceImpl service;

    @BeforeEach
    void setUp() {
        announcements = mock(SchoolAnnouncementRepository.class);
        attachments = mock(AnnouncementAttachmentRepository.class);
        resolver = mock(RecipientResolver.class);
        notifications = mock(NotificationService.class);
        storage = mock(FileStorage.class);
        AccountService accounts = mock(AccountService.class);
        given(accounts.findFullNames(anyCollection())).willReturn(Map.of(1L, "Hiệu trưởng"));
        service = new AnnouncementServiceImpl(
                announcements,
                attachments,
                resolver,
                notifications,
                accounts,
                storage,
                Clock.fixed(NOW, ZoneOffset.UTC),
                com.carenest.support.NotificationTimingTestConfig.load());
        given(resolver.normalize(any())).willAnswer(inv -> inv.getArgument(0));
        given(resolver.canView(any(), any(), any())).willReturn(true);
        given(resolver.resolve(any(), any(), any())).willReturn(Map.of(RecipientRole.PARENT, Set.of(500L, 501L)));
        given(announcements.save(any())).willAnswer(inv -> {
            SchoolAnnouncement a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 7L);
            return a;
        });
        given(notifications.notifyAnnouncement(eq(7L), anyCollection(), anyString(), anyString()))
                .willAnswer(inv -> ((java.util.Collection<?>) inv.getArgument(1)).size());
    }

    private static AnnouncementRequest request(Instant sendAt) {
        return new AnnouncementRequest("Nghỉ lễ", "Trường nghỉ ngày 20/11", CLASS_PARENTS, sendAt, null);
    }

    private SchoolAnnouncement existing(AnnouncementStatus status) {
        SchoolAnnouncement a = SchoolAnnouncement.create(
                1L,
                "Cũ",
                "Nội dung cũ",
                new SchoolAnnouncement.Target(
                        AnnouncementScopeType.CLASSROOM, 1L, null, 10L, Set.of(RecipientRole.PARENT)),
                NOW.plus(Duration.ofHours(2)),
                false,
                null,
                NOW);
        ReflectionTestUtils.setField(a, "id", 7L);
        ReflectionTestUtils.setField(a, "status", status);
        given(announcements.lockById(7L)).willReturn(Optional.of(a));
        given(announcements.findById(7L)).willReturn(Optional.of(a));
        return a;
    }

    @Test
    void sendNowCreatesNotificationsAndMarksSent() {
        AnnouncementResponse r = service.createAnnouncement(HT, request(null), null);

        assertThat(r.status()).isEqualTo(AnnouncementStatus.SENT);
        assertThat(r.recipientCount()).isEqualTo(2);
        verify(notifications).notifyAnnouncement(eq(7L), eq(Set.of(500L, 501L)), eq("Nghỉ lễ"), anyString());
    }

    @Test
    void onlyCancelledCanBeDeletedAndFilesWaitForCommit() {
        existing(AnnouncementStatus.SENT);
        assertThatThrownBy(() -> service.deleteAnnouncement(HT, 7L))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.ANNOUNCEMENT_NOT_DELETABLE));
        verify(announcements, never()).deleteAll(any());
        SchoolAnnouncement a = existing(AnnouncementStatus.CANCELLED);
        AnnouncementAttachment file = mock(AnnouncementAttachment.class);
        given(file.getFileKey()).willReturn("cancelled/demo.pdf");
        given(attachments.findByAnnouncementIdIn(List.of(7L))).willReturn(List.of(file));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteAnnouncement(HT, 7L);
            verify(announcements).deleteAll(List.of(a));
            verify(storage, never()).removeQuietly(any(), anyString());
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(storage).removeQuietly(com.carenest.utils.enums.FileCategory.ANNOUNCEMENT, "cancelled/demo.pdf");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteOutsideScopeReturnsNotFoundAndDoesNotTouchFiles() {
        existing(AnnouncementStatus.CANCELLED);
        given(resolver.canView(any(), any(), any())).willReturn(false);
        assertThatThrownBy(() -> service.deleteAnnouncement(HT, 7L))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.ANNOUNCEMENT_NOT_FOUND));
        verify(announcements, never()).deleteAll(any());
        verify(storage, never()).removeQuietly(any(), anyString());
    }

    @Test
    void bulkWithNonCancelledStatusDeletesNothing() {
        assertThat(service.deleteCancelledAnnouncements(HT, "demo", AnnouncementStatus.SENT))
                .isZero();
        verify(announcements, never()).deleteAll(any());
    }

    @Test
    void selectedIdsAreCheckedTogetherBeforeAnyDelete() {
        SchoolAnnouncement cancelled = existing(AnnouncementStatus.CANCELLED);
        SchoolAnnouncement sent = existing(AnnouncementStatus.SENT);
        ReflectionTestUtils.setField(sent, "id", 8L);
        given(announcements.findAll(
                        any(org.springframework.data.jpa.domain.Specification.class),
                        any(org.springframework.data.domain.Sort.class)))
                .willReturn(List.of(cancelled, sent));
        var selection = new com.carenest.dto.announcement.AnnouncementSelectionRequest(
                false, List.of(7L, 8L), List.of(), null, null);
        assertThatThrownBy(() -> service.deleteSelectedAnnouncements(HT, selection))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.ANNOUNCEMENT_NOT_DELETABLE));
        verify(announcements, never()).deleteAll(any());
        given(announcements.findAll(
                        any(org.springframework.data.jpa.domain.Specification.class),
                        any(org.springframework.data.domain.Sort.class)))
                .willReturn(List.of(cancelled));
        assertThatThrownBy(() -> service.deleteSelectedAnnouncements(HT, selection))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.ANNOUNCEMENT_NOT_FOUND));
        verify(announcements, never()).deleteAll(any());
    }

    @Test
    void emptySelectionIsRejected() {
        assertThatThrownBy(() -> service.deleteSelectedAnnouncements(
                        HT,
                        new com.carenest.dto.announcement.AnnouncementSelectionRequest(
                                false, List.of(), List.of(), null, null)))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_REQUEST_DATA));
        verify(announcements, never()).deleteAll(any());
    }

    @Test
    void scheduledIsSavedWithoutNotifying() {
        AnnouncementResponse r = service.createAnnouncement(HT, request(NOW.plus(Duration.ofDays(1))), null);

        assertThat(r.status()).isEqualTo(AnnouncementStatus.SCHEDULED);
        assertThat(r.canEdit()).isTrue();
        verify(notifications, never()).notifyAnnouncement(anyLong(), anyCollection(), anyString(), anyString());
    }

    @Test
    void sendAtInPastOrBeyondThirtyDaysIsRejected() {
        assertCode(
                () -> service.createAnnouncement(HT, request(NOW.minusSeconds(60)), null),
                ApiCode.ANNOUNCEMENT_INVALID_SEND_AT);
        assertCode(
                () -> service.createAnnouncement(HT, request(NOW.plus(Duration.ofDays(31))), null),
                ApiCode.ANNOUNCEMENT_INVALID_SEND_AT);
    }

    @Test
    void scopeOutsideCampusIsDenied() {
        willThrow(GlobalException.of(ApiCode.ANNOUNCEMENT_SCOPE_DENIED))
                .given(resolver)
                .checkCanSend(any(), any());

        assertCode(() -> service.createAnnouncement(HT, request(null), null), ApiCode.ANNOUNCEMENT_SCOPE_DENIED);
        verify(announcements, never()).save(any());
    }

    @Test
    void emptyScopeCannotBeSent() {
        given(resolver.resolve(any(), any(), any())).willReturn(Map.of(RecipientRole.PARENT, Set.of()));

        assertCode(() -> service.createAnnouncement(HT, request(null), null), ApiCode.ANNOUNCEMENT_NO_RECIPIENTS);
    }

    @Test
    void sixthFileIsRejected() {
        List<MultipartFile> files =
                Collections.nCopies(6, new MockMultipartFile("files", "a.pdf", "application/pdf", new byte[] {1}));

        assertCode(() -> service.createAnnouncement(HT, request(null), files), ApiCode.ANNOUNCEMENT_TOO_MANY_FILES);
    }

    @Test
    void storedFilesAreRemovedWhenSendingFails() {
        given(storage.store(any(), any(), anyString(), any(), anyLong()))
                .willReturn(new FileStorage.StoredFile("announcements/k1.pdf", "application/pdf", 1));
        given(notifications.notifyAnnouncement(anyLong(), anyCollection(), anyString(), anyString()))
                .willThrow(new IllegalStateException("db"));
        List<MultipartFile> files = List.of(new MockMultipartFile("files", "a.pdf", "application/pdf", new byte[] {1}));

        assertThatThrownBy(() -> service.createAnnouncement(HT, request(null), files))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).removeQuietly(any(), eq("announcements/k1.pdf"));
    }

    @Test
    void scheduledCanBeEditedBeforeSending() {
        SchoolAnnouncement a = existing(AnnouncementStatus.SCHEDULED);
        AnnouncementRequest edit =
                new AnnouncementRequest("Mới", "Nội dung mới", CLASS_PARENTS, NOW.plus(Duration.ofHours(5)), List.of());

        service.updateAnnouncement(HT, 7L, edit, null);

        assertThat(a.getTitle()).isEqualTo("Mới");
        assertThat(a.getSendAt()).isEqualTo(NOW.plus(Duration.ofHours(5)));
        assertThat(a.getStatus()).isEqualTo(AnnouncementStatus.SCHEDULED);
    }

    @Test
    void editingAfterSendingStartedIsRejected() {
        existing(AnnouncementStatus.SENDING);

        assertCode(() -> service.updateAnnouncement(HT, 7L, request(null), null), ApiCode.ANNOUNCEMENT_NOT_EDITABLE);
        assertCode(() -> service.cancelAnnouncement(HT, 7L), ApiCode.ANNOUNCEMENT_NOT_EDITABLE);
    }

    @Test
    void cancelledScheduledIsNeverSent() {
        SchoolAnnouncement a = existing(AnnouncementStatus.SCHEDULED);

        service.cancelAnnouncement(HT, 7L);

        assertThat(a.getStatus()).isEqualTo(AnnouncementStatus.CANCELLED);
        given(announcements.lockDue(eq(7L), any(), any())).willReturn(Optional.empty());
        assertThat(service.dispatchDue(7L)).isFalse();
        verify(notifications, never()).notifyAnnouncement(anyLong(), anyCollection(), anyString(), anyString());
    }

    @Test
    void workerSendsDueAnnouncementWithRecipientsAtSendTime() {
        SchoolAnnouncement a = existing(AnnouncementStatus.SCHEDULED);
        given(announcements.lockDue(eq(7L), any(), eq(NOW))).willReturn(Optional.of(a));

        assertThat(service.dispatchDue(7L)).isTrue();

        assertThat(a.getStatus()).isEqualTo(AnnouncementStatus.SENT);
        assertThat(a.getRecipientCount()).isEqualTo(2);
        assertThat(a.getSentAt()).isEqualTo(NOW);
    }

    @Test
    void outsiderCannotDownloadAttachment() {
        SchoolAnnouncement a = existing(AnnouncementStatus.SENT);
        given(attachments.findById(3L))
                .willReturn(Optional.of(AnnouncementAttachment.of(a.getId(), "k", "a.pdf", "application/pdf", 1, NOW)));
        given(resolver.canView(any(), any(), any())).willReturn(false);
        CurrentUser outsider =
                new CurrentUser(900L, "kc-9", "p", "Phụ huynh", null, Set.of(CareNestRole.PARENT), Set.of());

        assertCode(() -> service.downloadAttachment(outsider, 3L), ApiCode.ANNOUNCEMENT_NOT_FOUND);
        verify(storage, never()).load(any(), anyString());
    }

    @Test
    void recipientCanDownloadAttachment() {
        SchoolAnnouncement a = existing(AnnouncementStatus.SENT);
        given(attachments.findById(3L))
                .willReturn(Optional.of(AnnouncementAttachment.of(a.getId(), "k", "a.pdf", "application/pdf", 1, NOW)));
        given(resolver.canView(any(), any(), any())).willReturn(false);
        given(notifications.isAnnouncementRecipient(500L, 7L)).willReturn(true);
        CurrentUser parent =
                new CurrentUser(500L, "kc-5", "p", "Phụ huynh", null, Set.of(CareNestRole.PARENT), Set.of());

        assertThat(service.downloadAttachment(parent, 3L).fileName()).isEqualTo("a.pdf");
    }

    private static void assertCode(Runnable call, ApiCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }
}
