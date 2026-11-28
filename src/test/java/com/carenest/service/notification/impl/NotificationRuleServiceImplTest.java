package com.carenest.service.notification.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.notification.NotificationRuleRequest;
import com.carenest.dto.notification.NotificationRuleResponse;
import com.carenest.entity.notification.NotificationRule;
import com.carenest.exception.GlobalException;
import com.carenest.repository.notification.NotificationRuleRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Quy tắc thông báo tự động (PAR-09): bắt buộc không tắt, loại chỉ xem không sửa, biến lạ bị chặn. */
class NotificationRuleServiceImplTest {
    private static final CurrentUser HT =
            new CurrentUser(1L, "kc", "ht", "HT", null, Set.of(CareNestRole.PRINCIPAL), Set.of());

    private NotificationRuleRepository repository;
    private StaffAccessPolicy access;
    private NotificationRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRuleRepository.class);
        access = mock(StaffAccessPolicy.class);
        service = new NotificationRuleServiceImpl(
                repository, access, Clock.fixed(Instant.parse("2026-11-16T02:00:00Z"), ZoneOffset.UTC));
        given(access.managesWholeSchool(HT)).willReturn(true);
        given(repository.findById(any())).willReturn(Optional.empty());
    }

    @Test
    void mandatoryRuleCannotBeTurnedOff() {
        assertCode(
                () -> service.updateRule(
                        HT, NotificationType.CHILD_PICKED_UP, new NotificationRuleRequest(false, null, null)),
                ApiCode.NOTIFICATION_RULE_MANDATORY);
        verify(repository, never()).save(any());
    }

    @Test
    void optionalRuleCanBeTurnedOffAndWordingEdited() {
        NotificationRuleResponse r = service.updateRule(
                HT, NotificationType.MENU_PUBLISHED, new NotificationRuleRequest(false, "Thực đơn {tuan}", null));

        assertThat(r.enabled()).isFalse();
        assertThat(r.titleTemplate()).isEqualTo("Thực đơn {tuan}");
        assertThat(r.customized()).isTrue();
        verify(repository).save(any(NotificationRule.class));
    }

    @Test
    void existingTypesAreReadOnly() {
        assertCode(
                () -> service.updateRule(
                        HT, NotificationType.EDU_SUBMITTED, new NotificationRuleRequest(true, "x", null)),
                ApiCode.NOTIFICATION_RULE_READ_ONLY);
    }

    @Test
    void unknownVariableIsRejected() {
        assertCode(
                () -> service.updateRule(
                        HT,
                        NotificationType.DAILY_HEALTH_NOTED,
                        new NotificationRuleRequest(true, null, "{tenTre} bị {trieuChung}")),
                ApiCode.NOTIFICATION_RULE_UNKNOWN_VARIABLE);
    }

    @Test
    void vicePrincipalIsForbidden() {
        CurrentUser pht = new CurrentUser(2L, "kc", "pht", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of());

        assertCode(() -> service.listRules(pht), ApiCode.FORBIDDEN);
    }

    @Test
    void listShowsEveryTypeWithEditableFirst() {
        given(repository.findAll()).willReturn(List.of());

        List<NotificationRuleResponse> list = service.listRules(HT);

        assertThat(list).hasSize(NotificationType.values().length);
        assertThat(list.getFirst().editable()).isTrue();
        assertThat(list.getLast().editable()).isFalse();
        assertThat(list.getLast().mandatory()).isTrue();
    }

    @Test
    void renderFillsAllowedVariables() {
        assertThat(NotificationRuleCatalog.render(
                        "{tenTre} đã được đón lúc {gio}. {khac}", Map.of("tenTre", "Bé An", "gio", "16:30")))
                .isEqualTo("Bé An đã được đón lúc 16:30.");
        assertThat(NotificationRuleCatalog.unknownVariables("{tenTre} {x}", List.of("tenTre")))
                .containsExactly("x");
    }

    private static void assertCode(Runnable call, ApiCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }
}
