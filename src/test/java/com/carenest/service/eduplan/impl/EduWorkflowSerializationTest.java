package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class EduWorkflowSerializationTest {
    @Mock
    ObjectMapper objectMapper;

    @Mock
    com.carenest.repository.eduplan.EduSettingsRepository settingsRepository;

    @InjectMocks
    EduWorkflowServiceImpl service;

    @Test
    void serializationFailureCannotEraseApprovals() {
        when(objectMapper.writeValueAsString(any())).thenThrow(new IllegalStateException("serialization failed"));
        var approval =
                new EduWorkflowPolicy.Approval(1L, "Ví dụ", EduWorkflowPolicy.Role.PRINCIPAL, Instant.EPOCH, "", 1);
        assertThatThrownBy(() -> service.serializeApprovals(List.of(approval))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidStoredApprovalsCannotBeTreatedAsEmptyHistory() {
        when(objectMapper.readTree("invalid")).thenThrow(new IllegalStateException("invalid stored approvals"));
        assertThatThrownBy(() -> service.parseApprovals("invalid")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void legacyLocalApprovalTimestampIsReadAsSchoolTime() throws Exception {
        String stored = "[{\"userId\":1,\"name\":\"Ví dụ\",\"roleKey\":\"TEAM_LEADER\","
                + "\"at\":\"2026-11-04 06:28\",\"note\":\"\"}]";
        when(objectMapper.readTree(stored)).thenReturn(new ObjectMapper().readTree(stored));

        var approvals = service.parseApprovals(stored);

        assertThat(approvals)
                .singleElement()
                .extracting(EduWorkflowPolicy.Approval::at)
                .isEqualTo(Instant.parse("2026-11-03T23:28:00Z"));
    }

    @Test
    void unknownApprovalRoleCannotBeSilentlyDiscarded() {
        String stored = "[{\"userId\":1,\"roleKey\":\"UNKNOWN\"}]";
        when(objectMapper.readTree(stored)).thenReturn(new ObjectMapper().readTree(stored));
        assertThatThrownBy(() -> service.parseApprovals(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void corruptPolicyCannotSilentlyGrantDefaultRoles() {
        var settings = org.mockito.Mockito.mock(com.carenest.entity.eduplan.EduSettings.class);
        when(settingsRepository.findById((short) 1)).thenReturn(java.util.Optional.of(settings));
        when(settings.getWorkflowPolicy()).thenReturn("invalid");
        when(objectMapper.readTree("invalid")).thenThrow(new IllegalStateException("invalid policy"));
        assertThatThrownBy(() -> service.loadPolicy("year")).isInstanceOf(RuntimeException.class);
    }
}
