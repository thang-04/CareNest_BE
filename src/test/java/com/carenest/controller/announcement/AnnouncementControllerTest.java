package com.carenest.controller.announcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.announcement.AnnouncementResponse;
import com.carenest.dto.announcement.MyAnnouncementResponse;
import com.carenest.dto.announcement.RecipientPreviewResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.announcement.AnnouncementService;
import com.carenest.utils.enums.AnnouncementStatus;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.RecipientRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** API thông báo của trường: chỉ HT/PHT gửi; người nhận (mọi vai trò) đọc nội dung. */
@WebMvcTest(AnnouncementController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AnnouncementControllerTest {

    @Test
    void selectionBodyBindsAndTeacherCannotUseIt() throws Exception {
        CurrentUser principal = as(CareNestRole.PRINCIPAL);
        given(announcementService.deleteSelectedAnnouncements(eq(principal), any()))
                .willReturn(24);
        String body = "{\"allMatching\":true,\"ids\":[],\"excludedIds\":[8],\"q\":\"hop\",\"status\":\"CANCELLED\"}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/announcements/delete-selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(24));
        verify(announcementService)
                .deleteSelectedAnnouncements(
                        principal,
                        new com.carenest.dto.announcement.AnnouncementSelectionRequest(
                                true, List.of(), List.of(8L), "hop", AnnouncementStatus.CANCELLED));
        as(CareNestRole.TEACHER);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/announcements/delete-selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCancelledUsesFilterAndTeacherCannotDelete() throws Exception {
        CurrentUser principal = as(CareNestRole.PRINCIPAL);
        given(announcementService.deleteCancelledAnnouncements(principal, "hop", AnnouncementStatus.CANCELLED))
                .willReturn(25);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/announcements/cancelled")
                        .param("q", "hop")
                        .param("status", "CANCELLED")
                        .with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(25));
        as(CareNestRole.TEACHER);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/announcements/9")
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/announcements/cancelled")
                        .with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPassesSearchStatusAndPagingToService() throws Exception {
        CurrentUser principal = as(CareNestRole.PRINCIPAL);
        given(announcementService.listAnnouncements(principal, "hop", AnnouncementStatus.CANCELLED, 1, 20))
                .willReturn(new com.carenest.dto.announcement.AnnouncementListResponse(List.of(), 1, 20, 0, 0, 0));
        mockMvc.perform(get("/api/announcements")
                        .param("q", "hop")
                        .param("status", "CANCELLED")
                        .param("page", "1")
                        .with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1));
        verify(announcementService).listAnnouncements(principal, "hop", AnnouncementStatus.CANCELLED, 1, 20);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private AnnouncementService announcementService;

    private CurrentUser as(CareNestRole role) {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo", "Demo", null, Set.of(role), Set.of());
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }

    @Test
    void principalSendsMultipartAnnouncement() throws Exception {
        as(CareNestRole.PRINCIPAL);
        given(announcementService.createAnnouncement(any(), any(), any()))
                .willReturn(new AnnouncementResponse(
                        9L,
                        "Nghỉ lễ",
                        "Nội dung",
                        null,
                        AnnouncementStatus.SENT,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        30,
                        7L,
                        "Demo",
                        List.of(),
                        false,
                        false));
        MockMultipartFile data = new MockMultipartFile(
                "data",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                ("{\"title\":\"Nghỉ lễ\",\"body\":\"Nội dung\",\"target\":{\"scopeType\":\"SCHOOL\","
                                + "\"roles\":[\"PARENT\"]}}")
                        .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/announcements").file(data).with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recipientCount").value(30))
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }

    @Test
    void teacherCannotPreviewOrSend() throws Exception {
        as(CareNestRole.TEACHER);

        mockMvc.perform(post("/api/announcements/recipient-preview")
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"SCHOOL\",\"roles\":[\"PARENT\"]}"))
                .andExpect(status().isForbidden());
        verify(announcementService, never()).previewRecipients(any(), any());
    }

    @Test
    void vicePrincipalPreviewsRecipientCounts() throws Exception {
        CurrentUser pht = as(CareNestRole.VICE_PRINCIPAL);
        given(announcementService.previewRecipients(eq(pht), any()))
                .willReturn(
                        new RecipientPreviewResponse(12, Map.of(RecipientRole.PARENT, 10, RecipientRole.TEACHER, 2)));

        mockMvc.perform(post("/api/announcements/recipient-preview")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"CLASSROOM\",\"classroomId\":10,\"roles\":[\"PARENT\",\"TEACHER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(12))
                .andExpect(jsonPath("$.data.byRole.PARENT").value(10));
    }

    @Test
    void parentReadsAnnouncementContent() throws Exception {
        CurrentUser parent = as(CareNestRole.PARENT);
        given(announcementService.getMyAnnouncement(parent, 9L))
                .willReturn(new MyAnnouncementResponse(9L, "Nghỉ lễ", "Nội dung", Instant.EPOCH, "HT", List.of()));

        mockMvc.perform(get("/api/me/announcements/9").with(token(CareNestRole.PARENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("Nội dung"));
    }
}
