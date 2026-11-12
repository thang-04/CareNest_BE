package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.eduplan.LearningResourceBinding;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.utils.enums.FileCategory;
import com.carenest.integration.storage.FileStorage;
import com.carenest.repository.eduplan.EduLearningResourceRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LearningResourceServiceImplTest {
    @Test
    void failedMetadataTransactionCleansOnlyUploadedObject() {
        var docs = mock(EduClassDocService.class);
        var resources = mock(EduLearningResourceRepository.class);
        var storage = mock(FileStorage.class);
        var transactions = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any()))
                .thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        var service = new LearningResourceServiceImpl(
                docs,
                resources,
                mock(StaffAssignmentService.class),
                storage,
                Clock.systemUTC(),
                mock(com.carenest.service.eduplan.EduWorkflowService.class),
                mock(com.carenest.service.organization.ClassroomService.class),
                transactions);
        var user = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
        // EDU-81: học liệu gắn kế hoạch giáo dục ngày
        EduClassDoc doc = EduClassDoc.create(
                "dd_1_2_2026-09-07", "day", 1L, 3L, 2L, java.time.LocalDate.of(2026, 9, 7), 7L);
        ReflectionTestUtils.setField(doc, "id", 8L);
        when(docs.requireDayAccess(user, "dd_1_2_2026-09-07", true)).thenReturn(doc);
        when(storage.store(
                        org.mockito.ArgumentMatchers.eq(FileCategory.LEARNING_RESOURCE),
                        org.mockito.ArgumentMatchers.eq(8L),
                        org.mockito.ArgumentMatchers.eq("demo.pdf"),
                        any(),
                        anyLong()))
                .thenReturn(new FileStorage.StoredFile("hoclieu/new-object", "application/pdf", 8));
        when(resources.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("test failure"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.upload(
                            user,
                            "dd_1_2_2026-09-07",
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
}
