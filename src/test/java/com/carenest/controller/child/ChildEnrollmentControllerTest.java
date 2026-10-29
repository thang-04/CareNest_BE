package com.carenest.controller.child;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.SecurityConfig;
import com.carenest.config.WebMvcConfig;
import com.carenest.dto.child.ChildEnrollmentResponse;
import com.carenest.dto.child.ChildViewResponse;
import com.carenest.dto.child.DuplicateCheckResponse;
import com.carenest.dto.child.EnrollChildRequest;
import com.carenest.dto.child.GuardianAccountResultResponse;
import com.carenest.dto.common.FieldErrorResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.security.CurrentUserService;
import com.carenest.security.RestAccessDeniedHandler;
import com.carenest.security.RestAuthenticationEntryPoint;
import com.carenest.security.TestPermissions;
import com.carenest.service.account.ChildEnrollmentService;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.DuplicateChildKind;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianAccountResult;
import com.carenest.utils.enums.SmsStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ChildEnrollmentController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChildEnrollmentControllerTest {

    // Như form FE gửi: tuần thai để trống là chuỗi rỗng
    private static final String ENROLL_JSON = """
            {"child":{"fullName":"Nguyễn Minh An","dateOfBirth":"2023-03-01","gender":"MALE",
              "guardians":[{"fullName":"Trần Thị Mẹ","relation":"MOTHER","phone":"0912 345 678",
                "idNumber":"001234567890","email":""}],
              "profile":{"idNumber":"","premature":"NO","gestationWeeks":"","ethnicity":"Kinh",
                "beneficiary":"Không","bloodType":"","address":{"province":"Hà Nội","ward":"Cầu Giấy","street":"1 Láng"}}},
             "declaration":{"allergies":{"state":"NONE_REPORTED","items":[]},"diet":{"state":"NONE_REPORTED","text":""},
              "otherNotes":{"state":"NOT_PROVIDED","text":""},
              "medicalHistory":{"self":{"state":"NONE_REPORTED","text":""},"family":{"state":"NONE_REPORTED","text":""},
                "tbContact":{"state":"NONE_REPORTED","contactDate":""}}},
             "classId":null,"campusId":11}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private ChildIntakeService childIntakeService;

    @MockitoBean
    private ChildEnrollmentService childEnrollmentService;

    @Test
    @DisplayName("CHD-01: PHT tiếp nhận ⇒ 201, trường hồ sơ cùng cấp với guardianAccounts, tuần thai trống nhận được")
    void vicePrincipalEnrollsChild() throws Exception {
        CurrentUser vice = authenticate(CareNestRole.VICE_PRINCIPAL);
        given(childEnrollmentService.enroll(eq(vice), any()))
                .willReturn(new ChildEnrollmentResponse(
                        view(),
                        List.of(new GuardianAccountResultResponse(
                                "Trần Thị Mẹ", "0912345678", GuardianAccountResult.CREATED, false, SmsStatus.SENT))));

        mockMvc.perform(post("/api/children/enrollments")
                        .with(token(CareNestRole.VICE_PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENROLL_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(301))
                .andExpect(jsonPath("$.data.code").value("HS26005"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PLACEMENT"))
                .andExpect(jsonPath("$.data.child").doesNotExist())
                .andExpect(jsonPath("$.data.guardianAccounts[0].result").value("CREATED"))
                .andExpect(jsonPath("$.data.guardianAccounts[0].smsStatus").value("SENT"));

        ArgumentCaptor<EnrollChildRequest> request = ArgumentCaptor.forClass(EnrollChildRequest.class);
        verify(childEnrollmentService).enroll(eq(vice), request.capture());
        org.assertj.core.api.Assertions.assertThat(
                        request.getValue().child().profile().gestationWeeks())
                .isNull();
        org.assertj.core.api.Assertions.assertThat(request.getValue().campusId())
                .isEqualTo(11L);
    }

    @Test
    @DisplayName("C-5: lỗi theo ô từ service ⇒ 400 errorCode 9001, data = [{ field, message }]")
    void fieldErrorsAreReturnedAsList() throws Exception {
        authenticate(CareNestRole.PRINCIPAL);
        willThrow(GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA,
                        List.of(
                                new FieldErrorResponse(
                                        "guardians.0.phone", "Nhập số điện thoại hợp lệ, ví dụ 0912 345 678"),
                                new FieldErrorResponse("profile.address.ward", "Nhập phường/xã nơi ở"))))
                .given(childEnrollmentService)
                .enroll(any(), any());

        mockMvc.perform(post("/api/children/enrollments")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENROLL_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(9001))
                .andExpect(jsonPath("$.data[0].field").value("guardians.0.phone"))
                .andExpect(jsonPath("$.data[1].field").value("profile.address.ward"));
    }

    @Test
    @DisplayName("AUTH-18: CCCD/SĐT lệch phụ huynh đã có ⇒ 409 errorCode 3008 kèm ô")
    void guardianConflictIsConflictWithFields() throws Exception {
        authenticate(CareNestRole.PRINCIPAL);
        willThrow(GlobalException.withFieldErrors(
                        ApiCode.GUARDIAN_IDENTITY_CONFLICT,
                        List.of(new FieldErrorResponse(
                                "guardians.0.idNumber", "Số CCCD này đã ghi cho phụ huynh khác"))))
                .given(childEnrollmentService)
                .checkGuardians(any(), any());

        mockMvc.perform(post("/api/children/guardian-check")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guardians\":[{\"phone\":\"0912345678\",\"idNumber\":\"001234567890\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(3008))
                .andExpect(jsonPath("$.data[0].field").value("guardians.0.idNumber"));
    }

    @Test
    @DisplayName("CHD-04: kiểm trùng nhận danh sách CCCD phụ huynh; không trùng ⇒ data null")
    void duplicateCheckPassesGuardianIdNumbers() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childIntakeService.checkDuplicate(
                        eq(principal),
                        eq("Nguyễn Minh An"),
                        eq("2023-03-01"),
                        eq(List.of("001234567890", "009876543210")),
                        isNull()))
                .willReturn(new DuplicateCheckResponse(
                        DuplicateChildKind.LEFT,
                        true,
                        7L,
                        "HS25001",
                        "Nguyễn Minh An",
                        LocalDate.of(2026, 5, 1),
                        true));

        mockMvc.perform(get("/api/children/duplicate-check")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("fullName", "Nguyễn Minh An")
                        .param("dateOfBirth", "2023-03-01")
                        .param("guardianIdNumbers", "001234567890,009876543210"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("LEFT"))
                .andExpect(jsonPath("$.data.canReadmit").value(true));

        mockMvc.perform(get("/api/children/duplicate-check")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("fullName", "Khác"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("CHD-01: giáo viên không có quyền tiếp nhận ⇒ 403 ở mọi API tiếp nhận, service không chạy")
    void teacherCannotUseEnrollmentApis() throws Exception {
        authenticate(CareNestRole.TEACHER);

        mockMvc.perform(post("/api/children/enrollments")
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENROLL_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/children/duplicate-check").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/children/placement-classes").with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/children/parent-accounts/by-citizen-id")
                        .with(token(CareNestRole.TEACHER))
                        .param("idNumber", "001234567890"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/children/7/readmit")
                        .with(token(CareNestRole.TEACHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Quay lại\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @Test
    @DisplayName("CHD-03: mở lại hồ sơ ⇒ 200 trả hồ sơ")
    void principalReadmitsChild() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childIntakeService.readmit(eq(principal), eq(301L), any())).willReturn(view());

        mockMvc.perform(post("/api/children/301/readmit")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Gia đình chuyển về\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PLACEMENT"));
    }

    private static ChildViewResponse view() {
        return new ChildViewResponse(
                301L,
                "HS26005",
                "Nguyễn Minh An",
                LocalDate.of(2023, 3, 1),
                Gender.MALE,
                ChildStatus.PENDING_PLACEMENT,
                11L,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 10, 29),
                null,
                null,
                null,
                List.of(),
                true,
                null,
                List.of(),
                List.of());
    }

    private CurrentUser authenticate(CareNestRole role) {
        CurrentUser user = TestPermissions.userWithDefaults(role == CareNestRole.PRINCIPAL ? 2L : 3L, role);
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
