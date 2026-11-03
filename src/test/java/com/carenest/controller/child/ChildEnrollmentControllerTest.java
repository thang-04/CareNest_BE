package com.carenest.controller.child;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.carenest.dto.child.GuardianCheckRequest;
import com.carenest.dto.child.GuardianInput;
import com.carenest.dto.child.LinkedChildResponse;
import com.carenest.dto.child.ParentAccountLookupResponse;
import com.carenest.dto.child.PlacementClassResponse;
import com.carenest.dto.child.ReadmitChildRequest;
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
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.Permission;
import com.carenest.utils.enums.SmsStatus;
import com.carenest.utils.enums.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ChildEnrollmentController.class)
@Import({WebMvcConfig.class, SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ChildEnrollmentControllerTest {

    // Như form FE gửi: tuần thai để trống là chuỗi rỗng
    private static final String ENROLL_JSON = """
            {
              "child": {
                "fullName": "Nguyễn Minh An",
                "dateOfBirth": "2023-03-01",
                "gender": "MALE",
                "guardians": [
                  {
                    "fullName": "Trần Thị Mẹ",
                    "relation": "MOTHER",
                    "phone": "0912 345 678",
                    "idNumber": "001234567890",
                    "email": ""
                  }
                ],
                "profile": {
                  "idNumber": "",
                  "premature": "NO",
                  "gestationWeeks": "",
                  "ethnicity": "Kinh",
                  "beneficiary": "Không",
                  "bloodType": "",
                  "address": {
                    "province": "Hà Nội",
                    "ward": "Cầu Giấy",
                    "street": "1 Láng"
                  }
                }
              },
              "declaration": {
                "allergies": {
                  "state": "NONE_REPORTED",
                  "items": []
                },
                "diet": {
                  "state": "NONE_REPORTED",
                  "text": ""
                },
                "otherNotes": {
                  "state": "NOT_PROVIDED",
                  "text": ""
                },
                "medicalHistory": {
                  "self": {
                    "state": "NONE_REPORTED",
                    "text": ""
                  },
                  "family": {
                    "state": "NONE_REPORTED",
                    "text": ""
                  },
                  "tbContact": {
                    "state": "NONE_REPORTED",
                    "contactDate": ""
                  }
                }
              },
              "classId": null,
              "campusId": 11
            }
            """;

    private static final String GUARDIAN_JSON = """
            {
              "guardians": [
                {
                  "fullName": "Phụ huynh kiểm thử",
                  "relation": "MOTHER",
                  "phone": "0912345678",
                  "idNumber": "001234567890",
                  "email": ""
                }
              ]
            }
            """;

    private static final String READMIT_JSON = """
            {
              "reason": "Gia đình chuyển về"
            }
            """;

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
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.desc").value("Enroll child success"))
                .andExpect(jsonPath("$.data.id").value(301))
                .andExpect(jsonPath("$.data.code").value("HS26005"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PLACEMENT"))
                .andExpect(jsonPath("$.data.child").doesNotExist())
                .andExpect(jsonPath("$.data.guardianAccounts[0].result").value("CREATED"))
                .andExpect(jsonPath("$.data.guardianAccounts[0].smsStatus").value("SENT"));

        ArgumentCaptor<EnrollChildRequest> request = ArgumentCaptor.forClass(EnrollChildRequest.class);
        verify(childEnrollmentService).enroll(eq(vice), request.capture());
        assertThat(request.getValue().child().profile().gestationWeeks()).isNull();
        assertThat(request.getValue().campusId()).isEqualTo(11L);
        assertThat(request.getValue().classId()).isNull();
        verifyNoInteractions(childIntakeService);
    }

    @Test
    @DisplayName("CHD-01: tiếp nhận có chọn lớp truyền đủ form xuống service và trả hồ sơ có lớp")
    void enrollmentPassesSelectedClassAndFormData() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childEnrollmentService.enroll(eq(principal), any()))
                .willReturn(new ChildEnrollmentResponse(view(21L), List.of()));

        mockMvc.perform(post("/api/children/enrollments")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENROLL_JSON.replace("\"classId\": null", "\"classId\": 21")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.classId").value(21))
                .andExpect(jsonPath("$.data.className").value("Lớp kiểm thử"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.guardianAccounts").isEmpty());

        ArgumentCaptor<EnrollChildRequest> request = ArgumentCaptor.forClass(EnrollChildRequest.class);
        verify(childEnrollmentService).enroll(eq(principal), request.capture());
        EnrollChildRequest form = request.getValue();
        assertThat(form.classId()).isEqualTo(21L);
        assertThat(form.campusId()).isEqualTo(11L);
        assertThat(form.child().fullName()).isEqualTo("Nguyễn Minh An");
        assertThat(form.child().dateOfBirth()).isEqualTo("2023-03-01");
        assertThat(form.child().gender()).isEqualTo("MALE");
        assertThat(form.child().guardians()).hasSize(1);
        assertThat(form.child().guardians().getFirst().phone()).isEqualTo("0912 345 678");
        assertThat(form.child().profile().address().ward()).isEqualTo("Cầu Giấy");
        assertThat(form.declaration().allergies().state()).isEqualTo("NONE_REPORTED");
        verifyNoInteractions(childIntakeService);
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
    @DisplayName("AUTH-18: CCCD/SĐT lệch phụ huynh đã có ⇒ 409 errorCode 3009 kèm ô")
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
                .andExpect(jsonPath("$.errorCode").value(3009))
                .andExpect(jsonPath("$.data[0].field").value("guardians.0.idNumber"));
    }

    @Test
    @DisplayName("CHD-04: kiểm trùng truyền đủ thông tin trẻ, CCCD phụ huynh và id hồ sơ loại trừ")
    void duplicateCheckPassesGuardianIdNumbers() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childIntakeService.checkDuplicate(
                        eq(principal),
                        eq("Nguyễn Minh An"),
                        eq("2023-03-01"),
                        eq(List.of("001234567890", "009876543210")),
                        eq(301L)))
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
                        .param("guardianIdNumbers", "001234567890,009876543210")
                        .param("excludeId", "301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.kind").value("LEFT"))
                .andExpect(jsonPath("$.data.canReadmit").value(true));

        verify(childIntakeService)
                .checkDuplicate(
                        principal, "Nguyễn Minh An", "2023-03-01", List.of("001234567890", "009876543210"), 301L);
        verifyNoInteractions(childEnrollmentService);
    }

    @Test
    @DisplayName("CHD-04: không trùng hồ sơ ⇒ 200, data null và giữ các tham số tùy chọn là null")
    void duplicateCheckReturnsNoMatch() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childIntakeService.checkDuplicate(eq(principal), eq("Khác"), isNull(), isNull(), isNull()))
                .willReturn(null);

        mockMvc.perform(get("/api/children/duplicate-check")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("fullName", "Khác"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desc").value("Check duplicate child success"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(childIntakeService).checkDuplicate(principal, "Khác", null, null, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enrollmentRequests")
    @DisplayName("CHD-01: giáo viên không có quyền tiếp nhận ⇒ 403 ở mọi API tiếp nhận, service không chạy")
    void teacherCannotUseEnrollmentApis(String endpoint, MockHttpServletRequestBuilder request) throws Exception {
        authenticate(CareNestRole.TEACHER);

        mockMvc.perform(request.with(token(CareNestRole.TEACHER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value(ApiCode.FORBIDDEN.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @Test
    @DisplayName("CHD-03: mở lại hồ sơ ⇒ 200 trả hồ sơ")
    void principalReadmitsChild() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        ReadmitChildRequest request = new ReadmitChildRequest("Gia đình chuyển về");
        given(childIntakeService.readmit(principal, 301L, request)).willReturn(view());

        mockMvc.perform(post("/api/children/301/readmit")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(READMIT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.desc").value("Readmit child success"))
                .andExpect(jsonPath("$.data.id").value(301))
                .andExpect(jsonPath("$.data.status").value("PENDING_PLACEMENT"));

        verify(childIntakeService).readmit(principal, 301L, request);
        verifyNoInteractions(childEnrollmentService);
    }

    @Test
    @DisplayName("CHD-01: chỉ cần quyền tiếp nhận để gọi API và nhận kết quả từ service")
    void enrollerWithoutParentAccountPermissionCanEnroll() throws Exception {
        CurrentUser enroller = authenticateWithPermissions(Permission.CHILD_ENROLL);
        given(childEnrollmentService.enroll(eq(enroller), any()))
                .willReturn(new ChildEnrollmentResponse(
                        view(),
                        List.of(new GuardianAccountResultResponse(
                                "Phụ huynh kiểm thử",
                                "0912345678",
                                GuardianAccountResult.NOT_ACTIVATED,
                                false,
                                null))));

        mockMvc.perform(post("/api/children/enrollments")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENROLL_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.guardianAccounts[0].result").value("NOT_ACTIVATED"))
                .andExpect(jsonPath("$.data.guardianAccounts[0].smsStatus").doesNotExist());

        verify(childEnrollmentService).enroll(eq(enroller), any());
        verifyNoInteractions(childIntakeService);
    }

    @Test
    @DisplayName("CHD-04: service báo hồ sơ trùng khi lưu tiếp nhận ⇒ 409, mã 3008")
    void enrollmentReturnsDuplicateChildConflict() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childEnrollmentService.enroll(eq(principal), any()))
                .willThrow(GlobalException.of(ApiCode.CHILD_DUPLICATE));

        mockMvc.perform(post("/api/children/enrollments")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENROLL_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.errorCode").value(3008))
                .andExpect(jsonPath("$.desc").value(ApiCode.CHILD_DUPLICATE.getDefaultDesc()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(childEnrollmentService).enroll(eq(principal), any());
        verifyNoInteractions(childIntakeService);
    }

    @Test
    @DisplayName("AUTH-18: service báo xung đột phụ huynh khi lưu tiếp nhận ⇒ 409 kèm ô sai")
    void enrollmentReturnsGuardianIdentityConflict() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childEnrollmentService.enroll(eq(principal), any()))
                .willThrow(GlobalException.withFieldErrors(
                        ApiCode.GUARDIAN_IDENTITY_CONFLICT,
                        List.of(new FieldErrorResponse("guardians.0.phone", "Số điện thoại đã thuộc phụ huynh khác"))));

        mockMvc.perform(post("/api/children/enrollments")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ENROLL_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(3009))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].field").value("guardians.0.phone"))
                .andExpect(jsonPath("$.data[0].message").value("Số điện thoại đã thuộc phụ huynh khác"));

        verify(childEnrollmentService).enroll(eq(principal), any());
    }

    @Test
    @DisplayName("AUTH-18: kiểm phụ huynh hợp lệ ⇒ 200 và chuyển đúng thông tin form cho service")
    void principalChecksGuardiansSuccessfully() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(post("/api/children/guardian-check")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GUARDIAN_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.desc").value("Check guardians success"))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<GuardianCheckRequest> request = ArgumentCaptor.forClass(GuardianCheckRequest.class);
        verify(childEnrollmentService).checkGuardians(eq(principal), request.capture());
        assertThat(request.getValue().guardians())
                .containsExactly(new GuardianInput("Phụ huynh kiểm thử", "MOTHER", "0912345678", "001234567890", ""));
        verifyNoInteractions(childIntakeService);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Permission.class,
            names = {"CHILD_ENROLL", "CHILD_PROFILE_EDIT"})
    @DisplayName("AUTH-18: một trong hai quyền tra cứu là đủ, trả tài khoản và dữ liệu liên kết từ service")
    void parentAccountLookupAcceptsEitherPermission(Permission permission) throws Exception {
        CurrentUser principal = authenticateWithPermissions(permission);
        given(childEnrollmentService.findParentAccount(principal, "001234567890"))
                .willReturn(new ParentAccountLookupResponse(
                        41L,
                        "Phụ huynh kiểm thử",
                        "0912345678",
                        "001234567890",
                        "parent@example.test",
                        UserStatus.ACTIVE,
                        List.of(new LinkedChildResponse(301L, "HS26005", "Trẻ kiểm thử", "Lớp kiểm thử")),
                        2,
                        GuardianRelationship.MOTHER));

        mockMvc.perform(get("/api/children/parent-accounts/by-citizen-id")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("idNumber", "001234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.desc").value("Find parent account success"))
                .andExpect(jsonPath("$.data.id").value(41))
                .andExpect(jsonPath("$.data.fullName").value("Phụ huynh kiểm thử"))
                .andExpect(jsonPath("$.data.phone").value("0912345678"))
                .andExpect(jsonPath("$.data.idNumber").value("001234567890"))
                .andExpect(jsonPath("$.data.email").value("parent@example.test"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.children.length()").value(1))
                .andExpect(jsonPath("$.data.children[0].id").value(301))
                .andExpect(jsonPath("$.data.children[0].className").value("Lớp kiểm thử"))
                .andExpect(jsonPath("$.data.otherChildrenCount").value(2))
                .andExpect(jsonPath("$.data.relation").value("MOTHER"));

        verify(childEnrollmentService).findParentAccount(principal, "001234567890");
        verifyNoInteractions(childIntakeService);
    }

    @Test
    @DisplayName("AUTH-18: không tìm thấy tài khoản phụ huynh ⇒ 200, data null")
    void parentAccountLookupReturnsNoMatch() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childEnrollmentService.findParentAccount(principal, "001234567890"))
                .willReturn(null);

        mockMvc.perform(get("/api/children/parent-accounts/by-citizen-id")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("idNumber", "001234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(childEnrollmentService).findParentAccount(principal, "001234567890");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = Permission.class,
            names = {"CHILD_ENROLL", "CHILD_PLACEMENT"})
    @DisplayName("CHD-10: một trong hai quyền lấy lớp là đủ, trả đủ thông tin lớp và sĩ số")
    void placementClassesAcceptEitherPermission(Permission permission) throws Exception {
        CurrentUser principal = authenticateWithPermissions(permission);
        given(childIntakeService.listPlacementClasses(principal))
                .willReturn(List.of(
                        new PlacementClassResponse(21L, "Lớp A", 11L, 31L, 51L, 30, 12, 61L),
                        new PlacementClassResponse(22L, "Lớp B", 12L, 32L, 51L, null, 0, null)));

        mockMvc.perform(get("/api/children/placement-classes").with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.desc").value("List placement classes success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.data[0].name").value("Lớp A"))
                .andExpect(jsonPath("$.data[0].campusId").value(11))
                .andExpect(jsonPath("$.data[0].ageGroupId").value(31))
                .andExpect(jsonPath("$.data[0].schoolYearId").value(51))
                .andExpect(jsonPath("$.data[0].capacity").value(30))
                .andExpect(jsonPath("$.data[0].enrolledCount").value(12))
                .andExpect(jsonPath("$.data[0].homeroomTeacherId").value(61))
                .andExpect(jsonPath("$.data[1].capacity").doesNotExist())
                .andExpect(jsonPath("$.data[1].enrolledCount").value(0))
                .andExpect(jsonPath("$.data[1].homeroomTeacherId").doesNotExist());

        verify(childIntakeService).listPlacementClasses(principal);
        verifyNoInteractions(childEnrollmentService);
    }

    @Test
    @DisplayName("CHD-10: không có lớp phù hợp ⇒ 200, danh sách rỗng")
    void placementClassesCanBeEmpty() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        given(childIntakeService.listPlacementClasses(principal)).willReturn(List.of());

        mockMvc.perform(get("/api/children/placement-classes").with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(childIntakeService).listPlacementClasses(principal);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = ApiCode.class,
            names = {"CHILD_NOT_FOUND", "CHILD_NOT_LEFT", "CHILD_ACCESS_DENIED"})
    @DisplayName("CHD-03: lỗi từ service mở lại hồ sơ giữ nguyên HTTP status và mã lỗi")
    void readmissionReturnsServiceErrors(ApiCode apiCode) throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        ReadmitChildRequest request = new ReadmitChildRequest("Gia đình chuyển về");
        given(childIntakeService.readmit(principal, 301L, request)).willThrow(GlobalException.of(apiCode));

        mockMvc.perform(post("/api/children/301/readmit")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(READMIT_JSON))
                .andExpect(status().is(apiCode.getHttpStatus().value()))
                .andExpect(jsonPath("$.code").value(apiCode.getCode()))
                .andExpect(jsonPath("$.errorCode").value(apiCode.getErrorCode()))
                .andExpect(jsonPath("$.desc").value(apiCode.getDefaultDesc()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(childIntakeService).readmit(principal, 301L, request);
        verifyNoInteractions(childEnrollmentService);
    }

    @Test
    @DisplayName("CHD-03: service báo thiếu lý do mở lại ⇒ 400 kèm lỗi ô reason")
    void readmissionReturnsReasonFieldError() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        ReadmitChildRequest request = new ReadmitChildRequest("");
        given(childIntakeService.readmit(principal, 301L, request))
                .willThrow(GlobalException.withFieldErrors(
                        ApiCode.INVALID_REQUEST_DATA, List.of(new FieldErrorResponse("reason", "Nhập lý do mở lại"))));

        mockMvc.perform(post("/api/children/301/readmit")
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(9001))
                .andExpect(jsonPath("$.data[0].field").value("reason"))
                .andExpect(jsonPath("$.data[0].message").value("Nhập lý do mở lại"));

        verify(childIntakeService).readmit(principal, 301L, request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enrollmentRequests")
    @DisplayName("CHD-01: chưa đăng nhập ⇒ 401 cho mọi API tiếp nhận, service không chạy")
    void unauthenticatedCallerCannotUseEnrollmentApis(String endpoint, MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.errorCode").value(ApiCode.UNAUTHENTICATED.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enrollmentRequests")
    @DisplayName("CHD-01: Hiệu trưởng không có quyền hiệu lực ⇒ 403, không cấp quyền chỉ dựa trên role")
    void principalWithoutPermissionsCannotUseEnrollmentApis(String endpoint, MockHttpServletRequestBuilder request)
            throws Exception {
        authenticateWithPermissions();

        mockMvc.perform(request.with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.FORBIDDEN.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @Test
    @DisplayName("AUTH-18: chỉ có quyền xếp lớp không được tra tài khoản phụ huynh")
    void placementPermissionDoesNotAllowParentAccountLookup() throws Exception {
        authenticateWithPermissions(Permission.CHILD_PLACEMENT);

        mockMvc.perform(get("/api/children/parent-accounts/by-citizen-id")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("idNumber", "001234567890"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.FORBIDDEN.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @Test
    @DisplayName("CHD-10: chỉ có quyền sửa hồ sơ không được lấy danh sách lớp tiếp nhận")
    void profileEditPermissionDoesNotAllowPlacementClasses() throws Exception {
        authenticateWithPermissions(Permission.CHILD_PROFILE_EDIT);

        mockMvc.perform(get("/api/children/placement-classes").with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.FORBIDDEN.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @Test
    @DisplayName("CHD-04: mọi tham số kiểm trùng đều tùy chọn và được chuyển nguyên trạng xuống service")
    void duplicateCheckAllowsMissingOptionalParameters() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(get("/api/children/duplicate-check").with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(childIntakeService).checkDuplicate(principal, null, null, null, null);
    }

    @Test
    @DisplayName("CHD-04: kiểm trùng chấp nhận đúng giới hạn độ dài và tối đa ba CCCD phụ huynh")
    void duplicateCheckAcceptsParameterSizeLimits() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        String fullName = "A".repeat(150);
        List<String> guardianIds = List.of("001234567890", "001234567891", "001234567892");

        mockMvc.perform(get("/api/children/duplicate-check")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("fullName", fullName)
                        .param("dateOfBirth", "2023-03-01")
                        .param("guardianIdNumbers", guardianIds.toArray(String[]::new)))
                .andExpect(status().isOk());

        verify(childIntakeService).checkDuplicate(principal, fullName, "2023-03-01", guardianIds, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oversizedDuplicateParameters")
    @DisplayName("CHD-04: tham số kiểm trùng vượt giới hạn ⇒ 400 kèm tên ô, service không chạy")
    void duplicateCheckRejectsOversizedParameters(String field, String[] values) throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(get("/api/children/duplicate-check")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param(field, values))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(9001))
                .andExpect(jsonPath("$.data[0].field").value(field));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @Test
    @DisplayName("AUTH-18: idNumber đúng giới hạn 20 ký tự được chuyển xuống service")
    void parentAccountLookupAcceptsIdNumberSizeLimit() throws Exception {
        CurrentUser principal = authenticate(CareNestRole.PRINCIPAL);
        String idNumber = "1".repeat(20);

        mockMvc.perform(get("/api/children/parent-accounts/by-citizen-id")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("idNumber", idNumber))
                .andExpect(status().isOk());

        verify(childEnrollmentService).findParentAccount(principal, idNumber);
    }

    @Test
    @DisplayName("AUTH-18: idNumber vượt 20 ký tự ⇒ 400 kèm tên ô, service không chạy")
    void parentAccountLookupRejectsOversizedIdNumber() throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(get("/api/children/parent-accounts/by-citizen-id")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("idNumber", "1".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(9001))
                .andExpect(jsonPath("$.data[0].field").value("idNumber"));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @Test
    @DisplayName("AUTH-18: thiếu tham số idNumber bắt buộc ⇒ 400 mã 9002, service không chạy")
    void parentAccountLookupRequiresIdNumber() throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(get("/api/children/parent-accounts/by-citizen-id").with(token(CareNestRole.PRINCIPAL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.MALFORMED_REQUEST.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidPostBodies")
    @DisplayName("CHD-01: JSON hỏng, body thiếu hoặc null ⇒ 400 ở mọi API POST, service không chạy")
    void postEndpointsRejectInvalidBodies(String endpoint, String body) throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(post(endpoint)
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value(ApiCode.MALFORMED_REQUEST.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("postEndpoints")
    @DisplayName("CHD-01: POST với Content-Type không hỗ trợ ⇒ 415, service không chạy")
    void postEndpointsRejectUnsupportedContentType(String endpoint) throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(post(endpoint)
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.UNSUPPORTED_MEDIA_TYPE.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    @DisplayName("CHD-04: excludeId sai kiểu hoặc vượt Long ⇒ 400, service không chạy")
    void duplicateCheckRejectsInvalidExcludeId(String excludeId) throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(get("/api/children/duplicate-check")
                        .with(token(CareNestRole.PRINCIPAL))
                        .param("excludeId", excludeId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.MALFORMED_REQUEST.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    @DisplayName("CHD-03: childId sai kiểu hoặc vượt Long ⇒ 400, service không chạy")
    void readmissionRejectsInvalidChildId(String childId) throws Exception {
        authenticate(CareNestRole.PRINCIPAL);

        mockMvc.perform(post("/api/children/{childId}/readmit", childId)
                        .with(token(CareNestRole.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(READMIT_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ApiCode.MALFORMED_REQUEST.getErrorCode()));

        verifyNoInteractions(childIntakeService, childEnrollmentService);
    }

    private static Stream<Arguments> enrollmentRequests() {
        return Stream.of(
                Arguments.of(
                        "enroll",
                        post("/api/children/enrollments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ENROLL_JSON)),
                Arguments.of("checkDuplicate", get("/api/children/duplicate-check")),
                Arguments.of(
                        "checkGuardians",
                        post("/api/children/guardian-check")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(GUARDIAN_JSON)),
                Arguments.of(
                        "findParentAccount",
                        get("/api/children/parent-accounts/by-citizen-id").param("idNumber", "001234567890")),
                Arguments.of("listPlacementClasses", get("/api/children/placement-classes")),
                Arguments.of(
                        "readmit",
                        post("/api/children/301/readmit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(READMIT_JSON)));
    }

    private static Stream<Arguments> oversizedDuplicateParameters() {
        return Stream.of(
                Arguments.of("fullName", new String[] {"A".repeat(151)}),
                Arguments.of("dateOfBirth", new String[] {"2023-03-011"}),
                Arguments.of(
                        "guardianIdNumbers",
                        new String[] {"001234567890", "001234567891", "001234567892", "001234567893"}));
    }

    private static Stream<String> postEndpoints() {
        return Stream.of("/api/children/enrollments", "/api/children/guardian-check", "/api/children/301/readmit");
    }

    private static Stream<Arguments> invalidPostBodies() {
        return postEndpoints()
                .flatMap(endpoint -> Stream.of("", "{", "null").map(body -> Arguments.of(endpoint, body)));
    }

    private static ChildViewResponse view() {
        return view(null);
    }

    private static ChildViewResponse view(Long classId) {
        return new ChildViewResponse(
                301L,
                "HS26005",
                "Nguyễn Minh An",
                LocalDate.of(2023, 3, 1),
                Gender.MALE,
                classId == null ? ChildStatus.PENDING_PLACEMENT : ChildStatus.ACTIVE,
                11L,
                classId,
                classId == null ? null : "Lớp kiểm thử",
                classId == null ? null : 31L,
                classId == null ? null : 51L,
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

    private CurrentUser authenticateWithPermissions(Permission... permissions) {
        CurrentUser user = new CurrentUser(
                2L, "kc-2", "demo.user", "Demo", null, Set.of(CareNestRole.PRINCIPAL), Set.of(permissions));
        given(currentUserService.getCurrentUser()).willReturn(user);
        return user;
    }

    private static RequestPostProcessor token(CareNestRole role) {
        return jwt().authorities(new SimpleGrantedAuthority(role.authority()));
    }
}
