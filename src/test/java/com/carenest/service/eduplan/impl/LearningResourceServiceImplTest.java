package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.eduplan.LearningResourceBinding;
import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.integration.storage.StorageService;
import com.carenest.repository.eduplan.EduLearningResourceRepository;
import com.carenest.repository.eduplan.EduPlanGoalRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduClassDocService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.LearningResourcePolicy;
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
import tools.jackson.databind.ObjectMapper;

class LearningResourceServiceImplTest {
    @Test
    void failedMetadataTransactionCleansOnlyUploadedObject() {
        var docs = mock(EduClassDocService.class);
        var resources = mock(EduLearningResourceRepository.class);
        var goals = mock(EduPlanGoalRepository.class);
        var storage = mock(StorageService.class);
        var service = new LearningResourceServiceImpl(
                docs,
                resources,
                goals,
                mock(StaffAssignmentService.class),
                storage,
                new ObjectMapper(),
                Clock.systemUTC());
        var user = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(CareNestRole.TEACHER));
        EduClassDoc doc = EduClassDoc.create("tp_1_2", "theme", 1L, 3L, 2L, null, 7L);
        ReflectionTestUtils.setField(doc, "id", 8L);
        doc.changeContent("{\"rows\":{\"9\":{\"activities\":\"A\"}}}", 7L);
        when(docs.requireThemeAccess(user, "tp_1_2", true)).thenReturn(doc);
        when(goals.belongsToTheme(9L, 3L, 2L)).thenReturn(true);
        when(storage.store(any(), anyLong())).thenReturn("new-object");
        when(resources.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("test failure"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.upload(
                            user,
                            "tp_1_2",
                            new MockMultipartFile("file", "demo.pdf", "application/pdf", "%PDF-1.7".getBytes()),
                            new LearningResourceBinding("9#0", LearningResourcePolicy.signature("A"), "A"),
                            UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storage).remove("new-object");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
