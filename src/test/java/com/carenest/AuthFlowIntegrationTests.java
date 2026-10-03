package com.carenest;

import com.carenest.service.MailService;
import com.carenest.service.SmsService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end auth flow on an in-memory H2 database (PostgreSQL mode).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carenest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@carenest.com";
    private static final String ADMIN_PASSWORD = "Admin@123";

    @Autowired
    private MockMvc mockMvc;

    // Never send real emails from tests; the mock also lets us read the OTP.
    @MockitoBean
    private MailService mailService;

    // Spy keeps the real behaviour (SMS not supported yet) but records the calls.
    @MockitoSpyBean
    private SmsService smsService;

    @Test
    void managementCreatesStaffAndParentLogsInByPhone() throws Exception {
        String principalToken = loginMobile(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken;

        // Principal creates a vice principal
        mockMvc.perform(post("/api/management/staff").header("Authorization", "Bearer " + principalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pho@gmail.com","fullName":"Cô Mai","password":"vice1234","role":"VICE_PRINCIPAL"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("VICE_PRINCIPAL"));
        String viceToken = loginMobile("pho@gmail.com", "vice1234").accessToken;

        // Vice principal creates a teacher, but not another vice principal; nobody creates a principal
        MvcResult teacherCreated = mockMvc.perform(post("/api/management/staff")
                        .header("Authorization", "Bearer " + viceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"CoGiao@Gmail.com","fullName":"Cô Lan","password":"teacher123","role":"TEACHER"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("cogiao@gmail.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("TEACHER"))
                .andReturn();
        long teacherId = ((Number) JsonPath.read(teacherCreated.getResponse().getContentAsString(), "$.data.id")).longValue();
        mockMvc.perform(post("/api/management/staff").header("Authorization", "Bearer " + viceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pho2@gmail.com","fullName":"X","password":"vice1234","role":"VICE_PRINCIPAL"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1103));
        mockMvc.perform(post("/api/management/staff").header("Authorization", "Bearer " + principalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ht2@gmail.com","fullName":"X","password":"abc12345","role":"PRINCIPAL"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1207));

        // Vice principal appoints the teacher as head teacher; the teacher keeps TEACHER
        mockMvc.perform(put("/api/management/teachers/" + teacherId + "/head-teacher")
                        .header("Authorization", "Bearer " + viceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItems("TEACHER", "HEAD_TEACHER")));

        // Teacher logs in by email and must change password; teacher cannot use management APIs
        Tokens teacher = loginMobile("cogiao@gmail.com", "teacher123");
        assertThat(teacher.mustChangePassword).isTrue();
        mockMvc.perform(post("/api/management/children")
                        .header("Authorization", "Bearer " + teacher.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childName":"X","parentPhone":"0911111111","parentName":"Y"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1103));

        // Vice principal adds a child -> parent account created
        MvcResult created = mockMvc.perform(post("/api/management/children")
                        .header("Authorization", "Bearer " + viceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childName":"Bé Na","parentPhone":"+84912345678","parentName":"Chị Hoa"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentAccountCreated").value(true))
                .andExpect(jsonPath("$.data.parent.phoneNumber").value("0912345678"))
                .andReturn();
        long parentId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.parent.id")).longValue();

        // Second child with the same phone (added by the principal) -> linked to existing parent
        mockMvc.perform(post("/api/management/children")
                        .header("Authorization", "Bearer " + principalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"childName":"Bé Bin","parentPhone":"0912345678","parentName":"Chị Hoa"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentAccountCreated").value(false))
                .andExpect(jsonPath("$.data.parent.id").value(parentId));

        // Parent logs in by phone with the default password
        Tokens parent = loginMobile("0912345678", "0912345678");
        assertThat(parent.mustChangePassword).isTrue();

        // Parent changes password -> flag cleared, old password no longer works
        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + parent.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"0912345678","newPassword":"parent123","confirmPassword":"parent123"}"""))
                .andExpect(status().isOk());
        assertThat(loginMobile("0912345678", "parent123").mustChangePassword).isFalse();
        loginExpectFailure("0912345678", "0912345678");

        // Teacher can only reset parents of children the teacher added
        mockMvc.perform(post("/api/teacher/parents/" + parentId + "/reset-password")
                        .header("Authorization", "Bearer " + teacher.accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1206));

        // Vice principal resets the parent's password back to the phone number
        mockMvc.perform(post("/api/management/parents/" + parentId + "/reset-password")
                        .header("Authorization", "Bearer " + viceToken))
                .andExpect(status().isOk());
        assertThat(loginMobile("0912345678", "0912345678").mustChangePassword).isTrue();
    }

    @Test
    void wrongPasswordAndValidationErrorsAreInVietnamese() throws Exception {
        loginExpectFailure(ADMIN_EMAIL, "wrong-password");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.identifier").value("Email hoặc số điện thoại không được để trống"));

        mockMvc.perform(post("/api/management/children").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1100));

        mockMvc.perform(post("/api/management/children").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1104));
    }

    @Test
    void refreshTokenRotationAndLogoutRevokeTokens() throws Exception {
        Tokens first = loginMobile(ADMIN_EMAIL, ADMIN_PASSWORD);

        // Refresh token cannot be used as access token
        mockMvc.perform(post("/api/management/staff").header("Authorization", "Bearer " + first.refreshToken))
                .andExpect(status().isUnauthorized());

        Tokens second = refreshMobile(first.refreshToken);
        assertThat(second.refreshToken).isNotEqualTo(first.refreshToken);

        // Old refresh token is revoked after rotation
        mockMvc.perform(post("/api/auth/refresh-token").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first.refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1104));

        // Logout revokes both tokens
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + second.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + second.refreshToken + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + second.accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh-token").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + second.refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webClientUsesHttpOnlyCookiesForBothTokens() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().httpOnly("access_token", true))
                .andExpect(cookie().path("access_token", "/"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/auth"))
                .andReturn();
        Cookie accessCookie = login.getResponse().getCookie("access_token");
        Cookie refreshCookie = login.getResponse().getCookie("refresh_token");

        // Protected API works with the access cookie only (no Authorization header)
        mockMvc.perform(post("/api/management/staff").cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"web@gmail.com","fullName":"Cô Web","password":"teacher123","role":"TEACHER"}"""))
                .andExpect(status().isCreated());

        // Login still works when an invalid access cookie is present
        mockMvc.perform(post("/api/auth/login").cookie(new Cookie("access_token", "expired.or.invalid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // Refresh with cookie only, returns new cookies
        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh-token").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();
        Cookie newAccess = refreshed.getResponse().getCookie("access_token");
        Cookie newRefresh = refreshed.getResponse().getCookie("refresh_token");

        // Logout with cookies: both revoked and cleared
        mockMvc.perform(post("/api/auth/logout").cookie(newAccess, newRefresh))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("access_token", 0))
                .andExpect(cookie().maxAge("refresh_token", 0));
        mockMvc.perform(post("/api/management/staff").cookie(newAccess))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh-token").cookie(newRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordByEmail() throws Exception {
        createTeacher("quen@gmail.com", null, "teacher123");

        // Step 1: send OTP by email
        postJson("/api/auth/forgot-password", "{\"identifier\":\"quen@gmail.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mã OTP đã được gửi tới email của bạn"));

        // OTP still valid -> cannot resend yet
        postJson("/api/auth/forgot-password", "{\"identifier\":\"quen@gmail.com\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1301));
        String otp = lastOtpSentTo("quen@gmail.com");
        verify(smsService, never()).sendSms(anyString(), anyString());

        // Cannot change password before the OTP is verified
        resetPassword("quen@gmail.com", "moi123456")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1303));

        // Step 2: wrong then correct OTP
        verifyOtp("quen@gmail.com", wrong(otp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1300));
        verifyOtp("quen@gmail.com", otp).andExpect(status().isOk());

        // Step 3: change password
        resetPassword("quen@gmail.com", "moi123456").andExpect(status().isOk());
        assertThat(loginMobile("quen@gmail.com", "moi123456").mustChangePassword).isFalse();

        // Verified OTP is consumed after the password change
        resetPassword("quen@gmail.com", "khac123456").andExpect(status().isForbidden());
    }

    @Test
    void forgotPasswordErrors() throws Exception {
        // Unknown account
        postJson("/api/auth/forgot-password", "{\"identifier\":\"khongco@gmail.com\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1203));

        // Phone number -> SMS channel, not supported yet; nothing is saved
        createTeacher("sdt@gmail.com", "0977777777", "teacher123");
        postJson("/api/auth/forgot-password", "{\"identifier\":\"0977777777\"}")
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value(1304));
        verify(mailService, never()).sendMail(anyString(), anyString(), anyString());
        verify(smsService).sendSms(eq("0977777777"), anyString());

        // Same account by email still works right after (no cooldown left by the failed SMS attempt)
        postJson("/api/auth/forgot-password", "{\"identifier\":\"sdt@gmail.com\"}")
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void lockedAccountIsRejectedImmediatelyAndCanBeUnlocked() throws Exception {
        Tokens principal = loginMobile(ADMIN_EMAIL, ADMIN_PASSWORD);
        MvcResult created = mockMvc.perform(post("/api/management/staff")
                        .header("Authorization", "Bearer " + principal.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"khoa@gmail.com","fullName":"Cô Khoá","password":"teacher123","role":"TEACHER"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        long teacherId = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id")).longValue();
        Tokens teacher = loginMobile("khoa@gmail.com", "teacher123");

        // Teacher cannot lock anyone; nobody locks themselves or the principal
        mockMvc.perform(put("/api/management/users/" + teacherId + "/status")
                        .header("Authorization", "Bearer " + teacher.accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isForbidden());
        long principalId = ((Number) JsonPath.read(mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + principal.accessToken))
                .andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
        mockMvc.perform(put("/api/management/users/" + principalId + "/status")
                        .header("Authorization", "Bearer " + principal.accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1209));

        // Principal locks the teacher
        mockMvc.perform(put("/api/management/users/" + teacherId + "/status")
                        .header("Authorization", "Bearer " + principal.accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));

        // The teacher's existing access token stops working at once, refresh and login fail too
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + teacher.accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1102));
        mockMvc.perform(post("/api/auth/refresh-token").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + teacher.refreshToken + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1102));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"khoa@gmail.com\",\"password\":\"teacher123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1102));

        // Unlock: the teacher can log in again (the account and its data were kept)
        mockMvc.perform(put("/api/management/users/" + teacherId + "/status")
                        .header("Authorization", "Bearer " + principal.accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        loginMobile("khoa@gmail.com", "teacher123");
    }

    private org.springframework.test.web.servlet.ResultActions verifyOtp(String identifier, String otp) throws Exception {
        return postJson("/api/auth/verify-otp",
                "{\"identifier\":\"" + identifier + "\",\"otp\":" + otp + "}");
    }

    private void createTeacher(String email, String phone, String password) throws Exception {
        String adminToken = loginMobile(ADMIN_EMAIL, ADMIN_PASSWORD).accessToken;
        String phoneJson = phone == null ? "" : ",\"phoneNumber\":\"" + phone + "\"";
        mockMvc.perform(post("/api/management/staff").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"Giáo viên\",\"password\":\""
                                + password + "\",\"role\":\"TEACHER\"" + phoneJson + "}"))
                .andExpect(status().isCreated());
    }

    private String lastOtpSentTo(String email) {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendMail(anyString(), content.capture(), eq(email));
        clearInvocations(mailService);
        Matcher matcher = Pattern.compile("(\\d{6})").matcher(content.getValue());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String wrong(String otp) {
        return otp.equals("111111") ? "222222" : "111111";
    }

    private org.springframework.test.web.servlet.ResultActions resetPassword(String identifier, String password)
            throws Exception {
        return postJson("/api/auth/reset-password", "{\"identifier\":\"" + identifier + "\",\"password\":\""
                + password + "\",\"confirmPassword\":\"" + password + "\"}");
    }

    private Tokens loginMobile(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return Tokens.from(result);
    }

    private Tokens refreshMobile(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh-token").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return Tokens.from(result);
    }

    private void loginExpectFailure(String identifier, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1101))
                .andExpect(jsonPath("$.message").value("Tài khoản hoặc mật khẩu không chính xác"));
    }

    private record Tokens(String accessToken, String refreshToken, boolean mustChangePassword) {
        static Tokens from(MvcResult result) throws Exception {
            String body = result.getResponse().getContentAsString();
            return new Tokens(
                    JsonPath.read(body, "$.data.accessToken"),
                    JsonPath.read(body, "$.data.refreshToken"),
                    JsonPath.read(body, "$.data.mustChangePassword"));
        }
    }
}
