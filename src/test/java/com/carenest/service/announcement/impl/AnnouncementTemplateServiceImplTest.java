package com.carenest.service.announcement.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.announcement.AnnouncementTemplateRequest;
import com.carenest.dto.announcement.AnnouncementTemplateResponse;
import com.carenest.entity.announcement.AnnouncementTemplate;
import com.carenest.exception.GlobalException;
import com.carenest.repository.announcement.AnnouncementTemplateRepository;
import com.carenest.security.CurrentUser;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Mẫu soạn sẵn (FR-017): PHT thấy mẫu toàn trường + điểm trường mình, chỉ sửa/xóa mẫu điểm trường mình. */
class AnnouncementTemplateServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-16T02:00:00Z");
    private static final CurrentUser PHT =
            new CurrentUser(2L, "kc-2", "pht", "PHT", null, Set.of(CareNestRole.VICE_PRINCIPAL), Set.of());

    private AnnouncementTemplateRepository templates;
    private TemplateUsage usage;
    private RecipientResolver resolver;
    private AnnouncementTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        templates = mock(AnnouncementTemplateRepository.class);
        usage = mock(TemplateUsage.class);
        resolver = mock(RecipientResolver.class);
        service = new AnnouncementTemplateServiceImpl(templates, usage, resolver, Clock.fixed(NOW, ZoneOffset.UTC));
        given(resolver.managedCampusIds(PHT)).willReturn(Set.of(1L));
        given(templates.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private AnnouncementTemplate template(Long id, Long campusId) {
        AnnouncementTemplate t = AnnouncementTemplate.create("Mẫu", "Tiêu đề", "Nội dung", campusId, 1L, NOW);
        ReflectionTestUtils.setField(t, "id", id);
        given(templates.findById(id)).willReturn(Optional.of(t));
        return t;
    }

    @Test
    void vicePrincipalSeesSchoolAndOwnCampusTemplates() {
        AnnouncementTemplate school = template(1L, null);
        AnnouncementTemplate own = template(2L, 1L);
        given(templates.findVisible(any())).willReturn(List.of(school, own));

        List<AnnouncementTemplateResponse> list = service.listTemplates(PHT);

        assertThat(list).extracting(AnnouncementTemplateResponse::canEdit).containsExactly(false, true);
    }

    @Test
    void vicePrincipalCannotCreateSchoolTemplateOrOtherCampus() {
        assertCode(
                () -> service.createTemplate(PHT, new AnnouncementTemplateRequest("A", "B", "C", null)),
                ApiCode.ANNOUNCEMENT_SCOPE_DENIED);
        assertCode(
                () -> service.createTemplate(PHT, new AnnouncementTemplateRequest("A", "B", "C", 2L)),
                ApiCode.ANNOUNCEMENT_SCOPE_DENIED);
        assertThat(service.createTemplate(PHT, new AnnouncementTemplateRequest("A", "B", "C", 1L))
                        .canEdit())
                .isTrue();
    }

    @Test
    void vicePrincipalCannotEditSchoolTemplate() {
        template(1L, null);

        assertCode(
                () -> service.updateTemplate(PHT, 1L, new AnnouncementTemplateRequest("A", "B", "C", 1L)),
                ApiCode.ANNOUNCEMENT_SCOPE_DENIED);
    }

    @Test
    void templateUsedBySchedulesCannotBeDeleted() {
        AnnouncementTemplate t = template(2L, 1L);
        given(usage.isUsed(2L)).willReturn(true);

        assertCode(() -> service.deleteTemplate(PHT, 2L), ApiCode.ANNOUNCEMENT_TEMPLATE_IN_USE);
        verify(templates, never()).delete(t);
    }

    @Test
    void blankFieldsAreRejected() {
        assertCode(
                () -> service.createTemplate(PHT, new AnnouncementTemplateRequest(" ", "", null, 1L)),
                ApiCode.INVALID_REQUEST_DATA);
    }

    private static void assertCode(Runnable call, ApiCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }
}
