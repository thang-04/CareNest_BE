package com.carenest.controller.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.organization.AgeGroupDetailResponse;
import com.carenest.dto.organization.ClassResponse;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.service.organization.AgeGroupService;
import com.carenest.service.organization.ClassManagementService;
import com.carenest.service.organization.RoomService;
import com.carenest.service.organization.TeacherAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.MealSession;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Lớp 1 (role) + validation cho API phòng, nhóm tuổi, lớp. */
@WebMvcTest({RoomController.class, AgeGroupController.class, ClassController.class, TeacherAssignmentController.class})
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SchoolConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private AgeGroupService ageGroupService;

    @MockitoBean
    private ClassManagementService classManagementService;

    @MockitoBean
    private TeacherAssignmentService teacherAssignmentService;

    @Test
    void classTeachersNeedExactlyTwo() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(put("/api/classes/5/teachers")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teacherIds\":[1],\"homeroomTeacherId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("teacherIds"));
        verify(teacherAssignmentService, never()).assignClassTeachers(any(), any(), any());
    }

    @Test
    void teacherCannotOpenTeacherAssignments() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get("/api/teacher-assignments").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void vicePrincipalCannotAssignTeamLeader() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(put("/api/team-leader-assignments")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolYearId\":1,\"ageGroupId\":2,\"userId\":3}"))
                .andExpect(status().isForbidden());
        verify(teacherAssignmentService, never()).assignTeamLeader(any(), any());
    }

    @Test
    void kitchenCanReadAgeGroups() throws Exception {
        authenticateAs(CareNestRole.KITCHEN_STAFF);
        given(ageGroupService.getAgeGroups())
                .willReturn(List.of(new AgeGroupDetailResponse(
                        1L, "ag-3", "Mẫu giáo bé", "MGB", (short) 2, 36, 48, List.of(MealSession.LUNCH), 1, 25, "")));

        mockMvc.perform(get("/api/age-groups").with(token(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mealSessions[0]").value("LUNCH"));
    }

    @Test
    void ageGroupWithoutMealSessionIs400() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(post("/api/age-groups")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"shortName\":\"N\",\"ageFromMonths\":12,\"ageToMonths\":24,"
                                + "\"mealSessions\":[],\"maxClassSize\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verify(ageGroupService, never()).createAgeGroup(any(), any());
    }

    @Test
    void teacherCannotListRooms() throws Exception {
        authenticateAs(CareNestRole.TEACHER);

        mockMvc.perform(get("/api/locations").with(token(CareNestRole.TEACHER))).andExpect(status().isForbidden());
        verify(roomService, never()).getRooms(any(), any(), any());
    }

    @Test
    void vicePrincipalCannotChangeRoomStatus() throws Exception {
        authenticateAs(CareNestRole.VICE_PRINCIPAL);

        mockMvc.perform(patch("/api/locations/5/status")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void classWithoutRoomIs400() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(post("/api/classes")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lớp A\",\"schoolYearId\":1,\"campusId\":1,\"ageGroupId\":1,"
                                + "\"capacity\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data[0].field").value("locationId"));
        verify(classManagementService, never()).createClass(any(), any());
    }

    @Test
    void classAcceptsFeFieldNames() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);
        given(classManagementService.createClass(any(), any()))
                .willReturn(new ClassResponse(
                        9L, "Lớp A", 1L, 1L, 1L, 3L, 20, ClassroomStatus.ACTIVE, 0, null, 2, false, "OPEN"));

        mockMvc.perform(post("/api/classes")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lớp A\",\"schoolYear\":1,\"campusId\":1,\"ageGroupId\":1,"
                                + "\"roomId\":3,\"capacity\":20}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.locationId").value(3));
        verify(classManagementService)
                .createClass(
                        any(),
                        org.mockito.ArgumentMatchers.argThat(r -> r.schoolYearId() == 1L && r.locationId() == 3L));
    }

    @Test
    void teamLeaderAcceptsFeSchoolYearField() throws Exception {
        authenticateAs(CareNestRole.PRINCIPAL);

        mockMvc.perform(put("/api/team-leader-assignments")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolYear\":1,\"ageGroupId\":2,\"userId\":null}"))
                .andExpect(status().isOk());
        verify(teacherAssignmentService)
                .assignTeamLeader(any(), org.mockito.ArgumentMatchers.argThat(r -> r.schoolYearId() == 1L));
    }

    @Test
    void kitchenCannotReadSchoolStructure() throws Exception {
        authenticateAs(CareNestRole.KITCHEN_STAFF);

        mockMvc.perform(get("/api/school-structure").with(token(CareNestRole.KITCHEN_STAFF)))
                .andExpect(status().isForbidden());
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
