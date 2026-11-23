package com.carenest.controller.meals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.common.ImageContent;
import com.carenest.dto.meals.InspectionApprovalResponse;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.dto.meals.InspectionSheetResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.meals.InspectionGrantService;
import com.carenest.service.meals.InspectionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionStatus;
import com.carenest.utils.enums.InspectionTab;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InspectionController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class InspectionControllerTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InspectionService inspections;

    @MockitoBean
    private InspectionGrantService grants;

    @MockitoBean
    private CurrentUserService users;

    private CurrentUser asVicePrincipal() {
        CurrentUser pht = TestPermissions.userWithDefaults(7L, CareNestRole.VICE_PRINCIPAL);
        given(users.getCurrentUser()).willReturn(pht);
        return pht;
    }

    private static InspectionSheetResponse sheet(InspectionTab tab, InspectionStatus status) {
        return new InspectionSheetResponse(tab, status, 1, true, Map.of(), List.of(), null, List.of(), null);
    }

    @Test
    @DisplayName("INS-01: PHT đọc các mẫu của điểm trường–ngày")
    void readsDay() throws Exception {
        CurrentUser pht = asVicePrincipal();
        given(inspections.getDay(pht, 1L, DAY))
                .willReturn(new InspectionDayResponse(
                        1L, DAY, true, List.of(sheet(InspectionTab.PROCESS, null)), List.of()));

        mvc.perform(get("/api/inspections/day")
                        .param("campusId", "1")
                        .param("date", "2026-11-09")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canEdit").value(true))
                .andExpect(jsonPath("$.data.sheets[0].tab").value("PROCESS"));
    }

    @Test
    @DisplayName("Lưu nháp nhận phiên bản và dòng; thiếu rows ⇒ 400")
    void savesDraft() throws Exception {
        CurrentUser pht = asVicePrincipal();
        given(inspections.save(any(), any(), any(), any(), any()))
                .willReturn(sheet(InspectionTab.SERVE, InspectionStatus.DRAFT));

        mvc.perform(put("/api/inspections/day/SERVE")
                        .param("campusId", "1")
                        .param("date", "2026-11-09")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"header\":{\"place\":\"Bếp\"},\"rows\":[{\"key\":\"a\"}]}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tab").value("SERVE"));
        mvc.perform(put("/api/inspections/day/SERVE")
                        .param("campusId", "1")
                        .param("date", "2026-11-09")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isBadRequest());
        verify(inspections).save(eq(pht), eq(1L), eq(DAY), eq(InspectionTab.SERVE), any());
    }

    @Test
    @DisplayName("Ký không cần thân bài; lỗi nghiệp vụ trả đúng mã HTTP và thông điệp tiếng Việt")
    void signsAndMapsErrors() throws Exception {
        asVicePrincipal();
        given(inspections.sign(any(), any(), any(), any(), any()))
                .willReturn(sheet(InspectionTab.PROCESS, InspectionStatus.SIGNED));

        mvc.perform(post("/api/inspections/day/PROCESS/sign")
                        .param("campusId", "1")
                        .param("date", "2026-11-09")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNED"));

        given(inspections.reopen(any(), any(), any(), any(), any()))
                .willThrow(GlobalException.of(ApiCode.INSPECTION_REASON_REQUIRED));
        mvc.perform(post("/api/inspections/day/PROCESS/reopen")
                        .param("campusId", "1")
                        .param("date", "2026-11-09")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"a\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("INS-03: ảnh chữ ký trả byte với MIME, private/no-store và nosniff")
    void returnsPrivateImage() throws Exception {
        CurrentUser pht = asVicePrincipal();
        String key = "33333333-3333-3333-3333-333333333333";
        given(inspections.getSignatureImage(pht, 1L, key)).willReturn(new ImageContent(new byte[] {1, 2}, "image/png"));

        mvc.perform(get("/api/inspections/signatures/" + key)
                        .param("campusId", "1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    private static InspectionApprovalResponse approval(InspectionGrantStatus status) {
        return new InspectionApprovalResponse(
                5L, 1L, DAY, InspectionTab.SAMPLE, status, false, "Quên ghi mẫu", "Demo", null, null, null, null, null);
    }

    @Test
    @DisplayName("INS-05: PHT gửi yêu cầu mở mẫu ngày cũ ⇒ 201 và dữ liệu yêu cầu")
    void requestsApproval() throws Exception {
        CurrentUser pht = asVicePrincipal();
        given(grants.request(eq(pht), eq(1L), eq(DAY), eq(InspectionTab.SAMPLE), any()))
                .willReturn(approval(InspectionGrantStatus.PENDING));

        mvc.perform(post("/api/inspections/day/SAMPLE/approvals")
                        .param("campusId", "1")
                        .param("date", "2026-11-09")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Quên ghi mẫu\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.tab").value("SAMPLE"));
    }

    @Test
    @DisplayName("INS-05: chỉ HT xem hàng chờ, duyệt, từ chối, thu hồi; PHT bị 403 và không chạm service")
    void onlyPrincipalDecidesApprovals() throws Exception {
        CurrentUser ht = TestPermissions.userWithDefaults(1L, CareNestRole.PRINCIPAL);
        given(users.getCurrentUser()).willReturn(ht);
        given(grants.queue(eq(ht), any(), any())).willReturn(List.of(approval(InspectionGrantStatus.PENDING)));
        given(grants.approve(eq(ht), eq(5L), any())).willReturn(approval(InspectionGrantStatus.APPROVED));
        given(grants.reject(eq(ht), eq(5L), any())).willReturn(approval(InspectionGrantStatus.REJECTED));
        given(grants.revoke(eq(ht), eq(5L), any())).willReturn(approval(InspectionGrantStatus.REVOKED));
        var principal = jwt().authorities(new SimpleGrantedAuthority("ROLE_PRINCIPAL"));

        mvc.perform(get("/api/inspections/approvals").with(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5));
        mvc.perform(post("/api/inspections/approvals/5/approve").with(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(post("/api/inspections/approvals/5/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Không đủ lý do\"}")
                        .with(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        mvc.perform(post("/api/inspections/approvals/5/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Mở nhầm\"}")
                        .with(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        var vice = jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"));
        mvc.perform(get("/api/inspections/approvals").with(vice)).andExpect(status().isForbidden());
        mvc.perform(post("/api/inspections/approvals/5/approve").with(vice)).andExpect(status().isForbidden());
        verify(grants).queue(eq(ht), any(), any());
    }

    @Test
    @DisplayName("INS-05: ngày cũ chưa duyệt ⇒ mã lỗi riêng 409, thông điệp tiếng Việt")
    void approvalRequiredMapsTo409() throws Exception {
        asVicePrincipal();
        given(inspections.save(any(), any(), any(), any(), any()))
                .willThrow(GlobalException.of(ApiCode.INSPECTION_APPROVAL_REQUIRED));

        mvc.perform(put("/api/inspections/day/SAMPLE")
                        .param("campusId", "1")
                        .param("date", "2026-11-09")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"header\":{},\"rows\":[{\"key\":\"a\"}]}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VICE_PRINCIPAL"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.errorCode").value(6116));
    }

    @Test
    @DisplayName("AUTH-21: bếp, giáo viên, phụ huynh và người chưa đăng nhập không dùng được biên bản")
    void rejectsOtherRoles() throws Exception {
        mvc.perform(get("/api/inspections/day").param("campusId", "1")).andExpect(status().isUnauthorized());
        for (String role : List.of("ROLE_KITCHEN_STAFF", "ROLE_TEACHER", "ROLE_PARENT")) {
            mvc.perform(get("/api/inspections/day")
                            .param("campusId", "1")
                            .with(jwt().authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(inspections);
    }
}
