package com.carenest.controller.eduplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.eduplan.EduWorkflowPermissionResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.eduplan.EduWorkflowPermissionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Tab Soạn/duyệt hồ sơ (EDU-108): chỉ HT; ràng buộc EDU-54 trả lỗi. */
@WebMvcTest(EduWorkflowPermissionController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class EduWorkflowPermissionControllerTest {

    private static final String URL = "/api/permissions/edu-workflow";
    private static final String BODY = """
            {"revision":1,"reason":"Đổi người duyệt","documents":[
              {"docType":"SCHOOL_YEAR","creators":["VICE_PRINCIPAL"],"reviewers":["PRINCIPAL"],
               "editRoles":["PRINCIPAL","VICE_PRINCIPAL"],"approveRoles":["PRINCIPAL"]},
              {"docType":"TEAM","creators":["TEAM_LEADER"],"reviewers":["VICE_PRINCIPAL"],
               "editRoles":["PRINCIPAL","TEAM_LEADER"],"approveRoles":["PRINCIPAL","VICE_PRINCIPAL"]},
              {"docType":"SEMESTER_PACKAGE","creators":["TEACHER"],"reviewers":["TEAM_LEADER","VICE_PRINCIPAL"],
               "editRoles":["PRINCIPAL","TEACHER"],"approveRoles":["PRINCIPAL","TEAM_LEADER","VICE_PRINCIPAL"]},
              {"docType":"THEME_PLAN","creators":["TEACHER"],"reviewers":["TEAM_LEADER","VICE_PRINCIPAL"],
               "editRoles":["PRINCIPAL","TEACHER"],"approveRoles":["PRINCIPAL","TEAM_LEADER","VICE_PRINCIPAL"]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private EduWorkflowPermissionService service;

    private static final EduWorkflowPermissionResponse SAMPLE = new EduWorkflowPermissionResponse(
            2L,
            List.of(new EduWorkflowPermissionResponse.Document(
                    "SCHOOL_YEAR",
                    "schoolYear",
                    "EDU_SCHOOL_YEAR_PLAN_EDIT",
                    "EDU_SCHOOL_YEAR_PLAN_APPROVE",
                    List.of("VICE_PRINCIPAL"),
                    List.of("PRINCIPAL"),
                    List.of("PRINCIPAL", "VICE_PRINCIPAL"),
                    List.of("PRINCIPAL", "VICE_PRINCIPAL"))));

    @Test
    @DisplayName("FR7.1: HT xem tab nhận 200")
    void principalGets() throws Exception {
        as(CareNestRole.PRINCIPAL);
        given(service.getWorkflowPermissions(any())).willReturn(SAMPLE);

        mockMvc.perform(get(URL).with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents[0].docType").value("SCHOOL_YEAR"))
                .andExpect(jsonPath("$.data.documents[0].editRoles[1]").value("VICE_PRINCIPAL"));
    }

    @Test
    @DisplayName("FR7.5: HT lưu tab nhận 200")
    void principalSaves() throws Exception {
        as(CareNestRole.PRINCIPAL);
        given(service.saveWorkflowPermissions(any(), any())).willReturn(SAMPLE);

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2));
    }

    @Test
    @DisplayName("FR7.1/NFR1: PHT gọi PUT bị 403, không vào service")
    void vicePrincipalForbidden() throws Exception {
        as(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("FR7.4: vai trò soạn trùng vai trò duyệt bị 400 SIGNERS_INVALID")
    void creatorEqualsReviewerRejected() throws Exception {
        as(CareNestRole.PRINCIPAL);
        given(service.saveWorkflowPermissions(any(), any())).willThrow(GlobalException.of(ApiCode.SIGNERS_INVALID));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"reviewers\":[\"PRINCIPAL\"]", "\"reviewers\":[\"VICE_PRINCIPAL\"]"))
                        .with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(7202));
    }

    private void as(CareNestRole role) {
        given(currentUserService.getCurrentUser()).willReturn(TestPermissions.userWithDefaults(1L, role));
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
