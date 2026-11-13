package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.eduplan.LearningResourceBinding;
import com.carenest.dto.eduplan.ResourceAttachRequest;
import com.carenest.dto.eduplan.ResourceOrderRequest;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduResourceAttachment;
import com.carenest.entity.eduplan.EduResourceItem;
import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.eduplan.EduResourceAttachmentRepository;
import com.carenest.repository.eduplan.EduResourceItemRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.ResourceLibraryService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LearningResourceServiceImplTest {
    private static final String KEY = "dd_1_2_2026-09-07";
    private final EduClassDocService docs = mock(EduClassDocService.class);
    private final EduResourceAttachmentRepository attachments = mock(EduResourceAttachmentRepository.class);
    private final EduResourceItemRepository items = mock(EduResourceItemRepository.class);
    private final ResourceLibraryService library = mock(ResourceLibraryService.class);
    private final StaffAssignmentService assignments = mock(StaffAssignmentService.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final ClassroomService classrooms = mock(ClassroomService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final CurrentUser teacher = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
    private EduClassDoc doc;
    private LearningResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new LearningResourceServiceImpl(
                docs,
                attachments,
                items,
                library,
                assignments,
                storage,
                Clock.systemUTC(),
                mock(EduWorkflowService.class),
                classrooms,
                transactions);
        // EDU-81: học liệu gắn kế hoạch giáo dục ngày
        doc = EduClassDoc.create(KEY, "day", 1L, 3L, 2L, LocalDate.of(2026, 9, 7), 7L);
        ReflectionTestUtils.setField(doc, "id", 8L);
        when(docs.requireDayAccess(teacher, KEY, true)).thenReturn(doc);
        when(docs.requireDayAccess(teacher, KEY, false)).thenReturn(doc);
        when(classrooms.findClassroomInfo(1L))
                .thenReturn(Optional.of(
                        new ClassroomInfoResponse(1L, "A1", 1L, 2L, null, null, null, 4L, List.of(), null, null)));
        when(attachments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static EduResourceItem link(long id, long owner) {
        EduResourceItem item = EduResourceItem.link(
                owner, "Bé đi nhà trẻ", 4L, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", Instant.now());
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    @Test
    void failedMetadataTransactionCleansOnlyUploadedObject() {
        when(storage.store(eq(FileCategory.LEARNING_RESOURCE), eq(8L), eq("demo.pdf"), any(), anyLong()))
                .thenReturn(new FileStorage.StoredFile("hoclieu/new-object", "application/pdf", 8));
        when(items.findByOwnerIdAndUploadRequestId(any(), any())).thenReturn(Optional.empty());
        when(library.createFileItem(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("test failure"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.upload(
                            teacher,
                            KEY,
                            new MockMultipartFile("file", "demo.pdf", "application/pdf", "%PDF-1.7".getBytes()),
                            new LearningResourceBinding("2026-09-08#goc", "Hoạt động góc"),
                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storage).remove(FileCategory.LEARNING_RESOURCE, "hoclieu/new-object");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void attachCreatesOnlyReferenceToLibraryItem() {
        EduResourceItem item = link(5L, 7L);
        when(library.requireVisible(teacher, 5L)).thenReturn(item);
        var result =
                service.attach(teacher, KEY, new ResourceAttachRequest(List.of(5L), "2026-09-08#hoc", "Hoạt động học"));
        // EDU-82: chỉ thêm dòng gắn, không tạo học liệu mới, không đụng kho tệp
        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.itemId()).isEqualTo(5L);
            assertThat(r.kind()).isEqualTo("LINK");
            assertThat(r.active()).isTrue();
        });
        verify(items, never()).save(any());
        verifyNoInteractions(storage);
    }

    @Test
    void attachSameItemTwiceInSectionRejected() {
        when(library.requireVisible(teacher, 5L)).thenReturn(link(5L, 7L));
        when(attachments.existsByClassDocIdAndActivityKeyAndItemId(8L, "2026-09-08#hoc", 5L))
                .thenReturn(true);
        assertThatThrownBy(() -> service.attach(
                        teacher, KEY, new ResourceAttachRequest(List.of(5L), "2026-09-08#hoc", "Hoạt động học")))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_ALREADY_ATTACHED);
    }

    @Test
    void sectionHoldsAtMostTwentyResources() {
        when(library.requireVisible(teacher, 5L)).thenReturn(link(5L, 7L));
        when(attachments.countByClassDocIdAndActivityKey(8L, "2026-09-08#hoc")).thenReturn(20L);
        assertThatThrownBy(() -> service.attach(
                        teacher, KEY, new ResourceAttachRequest(List.of(5L), "2026-09-08#hoc", "Hoạt động học")))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_SECTION_FULL);
    }

    @Test
    void detachRemovesOnlyThisAttachment() {
        EduResourceAttachment a =
                EduResourceAttachment.of(8L, "2026-09-08#hoc", "Hoạt động học", 5L, 0, 7L, Instant.now());
        ReflectionTestUtils.setField(a, "id", 30L);
        when(attachments.findByIdAndClassDocId(30L, 8L)).thenReturn(Optional.of(a));
        service.detach(teacher, KEY, 30L);
        // EDU-85: học liệu trong kho và dòng gắn ở ngày khác còn nguyên
        verify(attachments).delete(a);
        verify(items, never()).delete(any());
        verifyNoInteractions(storage);
    }

    @Test
    void viewerOfPlanSeesAttachedLinkWhateverItsScope() {
        // EDU-86: học liệu "Của tôi" của người khác, đã ẩn — người xem giáo án vẫn mở được
        EduResourceItem item = link(5L, 99L);
        item.changeHidden(true, Instant.now());
        EduResourceAttachment a =
                EduResourceAttachment.of(8L, "2026-09-08#hoc", "Hoạt động học", 5L, 0, 99L, Instant.now());
        ReflectionTestUtils.setField(a, "id", 30L);
        when(attachments.findByIdAndClassDocId(30L, 8L)).thenReturn(Optional.of(a));
        when(items.findById(5L)).thenReturn(Optional.of(item));
        when(assignments.findHomeroomTeacherIds(any(), any())).thenReturn(Map.of());
        assertThat(service.contentUrl(teacher, KEY, 30L).url())
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
    }

    private EduResourceAttachment row(long id, long itemId, int order) {
        EduResourceAttachment a =
                EduResourceAttachment.of(8L, "2026-09-08#hoc", "Hoạt động học", itemId, order, 7L, Instant.now());
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    @Test
    void reorderSetsPositionsInGivenOrder() {
        EduResourceAttachment a = row(30L, 5L, 0), b = row(31L, 6L, 1), c = row(32L, 7L, 2);
        when(attachments.findAllByClassDocIdAndActivityKey(8L, "2026-09-08#hoc"))
                .thenReturn(List.of(a, b, c));
        service.reorder(teacher, KEY, new ResourceOrderRequest("2026-09-08#hoc", List.of(32L, 30L, 31L)));
        assertThat(List.of(c.getSortOrder(), a.getSortOrder(), b.getSortOrder()))
                .containsExactly(0, 1, 2);
        verify(attachments).saveAllAndFlush(any());
    }

    @Test
    void reorderRejectsStaleList() {
        // EDU-82: người khác vừa gắn thêm ⇒ danh sách gửi lên thiếu một dòng
        when(attachments.findAllByClassDocIdAndActivityKey(8L, "2026-09-08#hoc"))
                .thenReturn(List.of(row(30L, 5L, 0), row(31L, 6L, 1)));
        assertThatThrownBy(
                        () -> service.reorder(teacher, KEY, new ResourceOrderRequest("2026-09-08#hoc", List.of(30L))))
                .isInstanceOf(GlobalException.class)
                .extracting("apiCode")
                .isEqualTo(ApiCode.RESOURCE_ORDER_STALE);
        verify(attachments, never()).saveAllAndFlush(any());
    }
}
