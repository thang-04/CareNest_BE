package com.carenest;

import com.carenest.service.MailService;
import com.carenest.service.StorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Account and child administration by the principal and vice principals.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carenest_users;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class UserManagementIntegrationTests {

    private static final AtomicInteger SEQ = new AtomicInteger(10_000_000);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private StorageService storageService;

    private String principal;

    @BeforeEach
    void setUp() throws Exception {
        when(storageService.uploadImage(any(), anyString())).thenAnswer(inv -> inv.getArgument(1) + "/x.png");
        when(storageService.url(anyString())).thenAnswer(inv -> "https://storage.test/" + inv.getArgument(0));
        principal = login("admin@carenest.com", "Admin@123");
    }

    @Test
    void listDetailAndUpdateUsers() throws Exception {
        String n = next();
        long viceId = createStaff(principal, "pho" + n + "@gmail.com", "VICE_PRINCIPAL", "Cô Phó " + n);
        long vice2Id = createStaff(principal, "phob" + n + "@gmail.com", "VICE_PRINCIPAL", "Thầy Phó " + n);
        String vice = login("pho" + n + "@gmail.com", "pass1234");
        long teacherId = createStaff(vice, "gv" + n + "@gmail.com", "TEACHER", "Cô Lan " + n);
        String teacher = login("gv" + n + "@gmail.com", "pass1234");

        // List: filter by role and keyword, paged
        perform(get("/api/management/users").param("role", "TEACHER").param("keyword", "lan " + n), vice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(teacherId));
        perform(get("/api/management/users").param("size", "1"), vice)
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.size").value(1));
        perform(get("/api/management/users").param("status", "LOCKED").param("keyword", n), vice)
                .andExpect(jsonPath("$.data.totalElements").value(0));
        perform(get("/api/management/users"), teacher).andExpect(status().isForbidden());

        // Detail of a parent lists the children
        String parentPhone = phone();
        long childId = createChild(vice, parentPhone);
        long parentId = parentIdOf(vice, childId);
        perform(get("/api/management/users/" + parentId), vice)
                .andExpect(jsonPath("$.data.account.roles", hasItem("PARENT")))
                .andExpect(jsonPath("$.data.children", hasSize(1)))
                .andExpect(jsonPath("$.data.lastLoginAt").doesNotExist());

        // Update a teacher: duplicate email, missing email, then a valid change that signs the teacher out
        putJson("/api/management/users/" + teacherId, vice,
                "{\"fullName\":\"Cô Lan\",\"email\":\"pho" + n + "@gmail.com\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(1200));
        putJson("/api/management/users/" + teacherId, vice, "{\"fullName\":\"Cô Lan\",\"email\":\"\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1211));
        putJson("/api/management/users/" + teacherId, vice,
                "{\"fullName\":\"Cô Lan Anh\",\"email\":\"LanAnh" + n + "@gmail.com\",\"position\":\"Giáo viên lớp Mầm\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("lananh" + n + "@gmail.com"))
                .andExpect(jsonPath("$.data.position").value("Giáo viên lớp Mầm"));
        perform(get("/api/users/me"), teacher)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1104));
        String teacherAgain = login("lananh" + n + "@gmail.com", "pass1234");

        // Parent must keep a phone number
        putJson("/api/management/users/" + parentId, vice, "{\"fullName\":\"PH\",\"phoneNumber\":\"\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1212));

        // Vice principal cannot edit another vice principal or the principal, but can edit themselves
        putJson("/api/management/users/" + vice2Id, vice, "{\"fullName\":\"X\",\"email\":\"phob" + n + "@gmail.com\"}")
                .andExpect(status().isForbidden());
        putJson("/api/management/users/" + viceId, vice,
                "{\"fullName\":\"Cô Phó Mới\",\"email\":\"pho" + n + "@gmail.com\"}")
                .andExpect(status().isOk());
        perform(get("/api/users/me"), vice).andExpect(status().isOk());

        // Own profile: name only
        putJson("/api/users/me", teacherAgain, "{\"fullName\":\"Lan Anh\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Lan Anh"));
    }

    @Test
    void changeRoleAndResetPasswordSignTheUserOut() throws Exception {
        String n = next();
        createStaff(principal, "pho" + n + "@gmail.com", "VICE_PRINCIPAL", "Phó");
        String vice = login("pho" + n + "@gmail.com", "pass1234");
        long teacherId = createStaff(vice, "gv" + n + "@gmail.com", "TEACHER", "GV");
        perform(put("/api/management/teachers/" + teacherId + "/head-teacher"), vice).andExpect(status().isOk());
        String teacher = login("gv" + n + "@gmail.com", "pass1234");

        // Vice principal cannot promote to vice principal; nobody changes their own role
        putJson("/api/management/users/" + teacherId + "/role", vice, "{\"role\":\"VICE_PRINCIPAL\"}")
                .andExpect(status().isForbidden());
        long principalId = id(perform(get("/api/users/me"), principal).andReturn(), "$.data.id");
        putJson("/api/management/users/" + principalId + "/role", principal, "{\"role\":\"TEACHER\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1214));
        putJson("/api/management/users/" + teacherId + "/role", vice, "{\"role\":\"PARENT\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1207));

        // Teacher -> staff drops HEAD_TEACHER and signs the user out
        putJson("/api/management/users/" + teacherId + "/role", vice, "{\"role\":\"STAFF\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("STAFF")))
                .andExpect(jsonPath("$.data.roles", not(hasItems("TEACHER"))))
                .andExpect(jsonPath("$.data.roles", not(hasItems("HEAD_TEACHER"))));
        perform(get("/api/users/me"), teacher).andExpect(status().isUnauthorized());

        // Principal promotes to vice principal
        putJson("/api/management/users/" + teacherId + "/role", principal, "{\"role\":\"VICE_PRINCIPAL\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roles", hasItem("VICE_PRINCIPAL")));

        // A parent-only account has no staff role to change
        long childId = createChild(vice, phone());
        long parentId = parentIdOf(vice, childId);
        putJson("/api/management/users/" + parentId + "/role", principal, "{\"role\":\"TEACHER\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1213));

        // Reset password: temporary password, must change, old tokens rejected, refresh rejected too
        long staffId = createStaff(vice, "nv" + n + "@gmail.com", "STAFF", "NV");
        MvcResult staffLogin = loginResult("nv" + n + "@gmail.com", "pass1234");
        String staffToken = JsonPath.read(staffLogin.getResponse().getContentAsString(), "$.data.accessToken");
        String staffRefresh = JsonPath.read(staffLogin.getResponse().getContentAsString(), "$.data.refreshToken");
        postJson("/api/management/users/" + staffId + "/reset-password", vice, "{\"password\":\"123\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/management/users/" + staffId + "/reset-password", vice, "{\"password\":\"temp1234\"}")
                .andExpect(status().isOk());
        perform(get("/api/users/me"), staffToken).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh-token").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + staffRefresh + "\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(1104));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"nv" + n + "@gmail.com\",\"password\":\"temp1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    @Test
    void onlyUnusedAccountsCanBeDeleted() throws Exception {
        String n = next();
        createStaff(principal, "pho" + n + "@gmail.com", "VICE_PRINCIPAL", "Phó");
        String vice = login("pho" + n + "@gmail.com", "pass1234");

        // Created by mistake, never logged in -> deleted
        long mistakeId = createStaff(vice, "nham" + n + "@gmail.com", "STAFF", "Nhầm");
        perform(delete("/api/management/users/" + mistakeId), vice).andExpect(status().isOk());
        perform(get("/api/management/users/" + mistakeId), vice).andExpect(status().isNotFound());

        // Logged in once -> must be locked instead
        long usedId = createStaff(vice, "dung" + n + "@gmail.com", "TEACHER", "Đã dùng");
        login("dung" + n + "@gmail.com", "pass1234");
        perform(delete("/api/management/users/" + usedId), vice)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(1210));

        // A parent with a child, the vice principal who added it, and the principal cannot be deleted
        long childId = createChild(vice, phone());
        long parentId = parentIdOf(vice, childId);
        perform(delete("/api/management/users/" + parentId), vice)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(1210));
        long viceId = id(perform(get("/api/users/me"), vice).andReturn(), "$.data.id");
        perform(delete("/api/management/users/" + viceId), principal)
                .andExpect(status().isConflict());
        long principalId = id(perform(get("/api/users/me"), principal).andReturn(), "$.data.id");
        perform(delete("/api/management/users/" + principalId), vice).andExpect(status().isForbidden());
    }

    @Test
    void manageChildrenAndMoveToAnotherParent() throws Exception {
        String wrongPhone = phone();
        long childId = createChild(principal, wrongPhone);
        String wrongParent = login(wrongPhone, wrongPhone);

        // The wrong parent added a pickup person, which the school approved
        MvcResult added = mockMvc.perform(multipart("/api/parent/children/" + childId + "/pickup-persons")
                        .file(new MockMultipartFile("photo", "a.png", "image/png", new byte[]{1}))
                        .param("fullName", "Người lạ").param("relationship", "Chú").param("phoneNumber", "0944444444")
                        .header("Authorization", "Bearer " + wrongParent))
                .andExpect(status().isCreated()).andReturn();
        long pickupId = id(added, "$.data.id");
        perform(post("/api/management/pickup-persons/" + pickupId + "/approve"), principal).andExpect(status().isOk());

        // List, search and detail
        perform(get("/api/management/children").param("keyword", wrongPhone), principal)
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(childId));
        perform(get("/api/management/children/" + childId), principal)
                .andExpect(jsonPath("$.data.child.fullName").value("Bé"))
                .andExpect(jsonPath("$.data.pickupPersons[0].status").value("APPROVED"));

        // Edit name and birth date
        putJson("/api/management/children/" + childId, principal, "{\"childName\":\"Bé Na\",\"dateOfBirth\":\"2999-01-01\"}")
                .andExpect(status().isBadRequest());
        putJson("/api/management/children/" + childId, principal, "{\"childName\":\"Bé Na\",\"dateOfBirth\":\"2022-03-04\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Bé Na"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("2022-03-04"));

        // Move to the right parent: new account created, pickup persons need approval again
        String rightPhone = phone();
        putJson("/api/management/children/" + childId + "/parent", principal,
                "{\"parentPhone\":\"" + rightPhone + "\",\"parentName\":\"Mẹ Na\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentAccountCreated").value(true))
                .andExpect(jsonPath("$.data.parent.phoneNumber").value(rightPhone));
        perform(get("/api/management/children/" + childId), principal)
                .andExpect(jsonPath("$.data.pickupPersons[0].status").value("PENDING"));

        perform(get("/api/parent/children"), wrongParent).andExpect(jsonPath("$.data", hasSize(0)));
        String rightParent = login(rightPhone, rightPhone);
        perform(get("/api/parent/children"), rightParent).andExpect(jsonPath("$.data", hasSize(1)));
        perform(get("/api/management/children/999999"), principal)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(1500));
    }

    // ---- helpers ----

    private static String next() {
        return String.valueOf(SEQ.incrementAndGet());
    }

    private static String phone() {
        return "09" + String.valueOf(SEQ.incrementAndGet()).substring(0, 8);
    }

    private long createStaff(String token, String email, String role, String name) throws Exception {
        MvcResult result = postJson("/api/management/staff", token,
                "{\"email\":\"" + email + "\",\"fullName\":\"" + name + "\",\"password\":\"pass1234\",\"role\":\"" + role + "\"}")
                .andExpect(status().isCreated()).andReturn();
        return id(result, "$.data.id");
    }

    private long createChild(String token, String parentPhone) throws Exception {
        MvcResult result = postJson("/api/management/children", token,
                "{\"childName\":\"Bé\",\"parentPhone\":\"" + parentPhone + "\",\"parentName\":\"PH\"}")
                .andExpect(status().isCreated()).andReturn();
        return id(result, "$.data.id");
    }

    private long parentIdOf(String token, long childId) throws Exception {
        return id(perform(get("/api/management/children/" + childId), token).andReturn(), "$.data.child.parent.id");
    }

    private String login(String identifier, String password) throws Exception {
        return JsonPath.read(loginResult(identifier, password).getResponse().getContentAsString(), "$.data.accessToken");
    }

    private MvcResult loginResult(String identifier, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                  String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions putJson(String url, String token, String json) throws Exception {
        return perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json), token);
    }

    private ResultActions postJson(String url, String token, String json) throws Exception {
        return perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json), token);
    }

    private static long id(MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }
}
