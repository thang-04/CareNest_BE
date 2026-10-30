package com.carenest.controller.eduplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.eduplan.CurriculumGoalResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.eduplan.EduCurriculumService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Role + envelope {code, errorCode, desc, data} của API danh mục Chương trình GDMN (EDU-28). */
@WebMvcTest(EduCurriculumController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class EduCurriculumControllerTest {

    private static final String BASE = "/api/education-plans/curriculum";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private EduCurriculumService curriculumService;

    @Test
    void teacherReadsGoalsInEnvelope() throws Exception {
        authenticateAs(CareNestRole.TEACHER);
        given(curriculumService.getGoals(2L))
                .willReturn(List.of(new CurriculumGoalResponse(
                        1L,
                        1L,
                        1L,
                        "Phát triển thể chất",
                        3L,
                        "Phát triển vận động",
                        4L,
                        "2",
                        "Nhóm",
                        "2.1",
                        "Mục tiêu",
                        "Phần ba › D › I › a) › 2 › 2.1",
                        1,
                        List.of("Nội dung"))));

        mockMvc.perform(get(BASE + "/goals").param("ageGroupId", "2").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].goalNo").value("2.1"))
                .andExpect(jsonPath("$.data[0].contents[0]").value("Nội dung"));
    }

    @Test
    void unknownAgeGroupReturns404WithErrorCode() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);
        given(curriculumService.getContents(9L)).willThrow(GlobalException.of(ApiCode.AGE_GROUP_NOT_FOUND));

        mockMvc.perform(get(BASE + "/contents").param("ageGroupId", "9").with(token(CareNestRole.VICE_PRINCIPAL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.AGE_GROUP_NOT_FOUND.getErrorCode()));
    }

    @Test
    void kitchenStaffIsRejectedByRole() throws Exception {
        authenticateAs(CareNestRole.KITCHEN_STAFF);

        mockMvc.perform(get(BASE + "/sources").with(token(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isForbidden());
        verify(curriculumService, never()).getSources();
        verify(curriculumService, never()).getGoals(any());
    }

    private CurrentUser authenticateAs(CareNestRole role) {
        CurrentUser user = new CurrentUser(7L, "kc-7", "demo.user", "Demo", null, Set.of(role));
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
