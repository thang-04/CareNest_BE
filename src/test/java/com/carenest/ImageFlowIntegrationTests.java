package com.carenest;

import com.carenest.service.MailService;
import com.carenest.service.StorageService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Images (avatar, user images, child photo) and pickup person approval on H2, with storage mocked.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:carenest_images;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class ImageFlowIntegrationTests {

    private static final String PRINCIPAL_EMAIL = "admin@carenest.com";
    private static final String PRINCIPAL_PASSWORD = "Admin@123";
    private static final AtomicInteger PHONE_SEQ = new AtomicInteger(1000);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailService mailService;

    @MockitoBean
    private StorageService storageService;

    private final AtomicInteger keySeq = new AtomicInteger();

    @BeforeEach
    void mockStorage() {
        when(storageService.uploadImage(any(), anyString()))
                .thenAnswer(inv -> inv.getArgument(1) + "/img" + keySeq.incrementAndGet() + ".png");
        when(storageService.url(anyString())).thenAnswer(inv -> "https://storage.test/" + inv.getArgument(0));
    }

    @Test
    void parentAddsPickupPersonAndManagementApprovesIt() throws Exception {
        String principal = login(PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
        String parentPhone = nextPhone();
        long childId = createChild(principal, parentPhone);
        String parent = login(parentPhone, parentPhone);
        String teacher = createTeacherAndLogin(principal);

        // Parent uploads the child's photo
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/parent/children/" + childId + "/photo")
                        .file(image("file")).header("Authorization", "Bearer " + parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").value(org.hamcrest.Matchers.startsWith("https://storage.test/children/" + childId)));
        mockMvc.perform(get("/api/parent/children").header("Authorization", "Bearer " + parent))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].photoUrl").exists());

        // Photo and valid fields are required
        mockMvc.perform(multipart("/api/parent/children/" + childId + "/pickup-persons")
                        .param("fullName", "Bà Tư").param("relationship", "Bà ngoại").param("phoneNumber", "0933333333")
                        .header("Authorization", "Bearer " + parent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));
        mockMvc.perform(multipart("/api/parent/children/" + childId + "/pickup-persons").file(image("photo"))
                        .param("fullName", "Bà Tư").param("relationship", "Bà ngoại").param("phoneNumber", "123")
                        .header("Authorization", "Bearer " + parent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.phoneNumber").exists());

        // Parent adds a pickup person -> PENDING
        MvcResult added = mockMvc.perform(multipart("/api/parent/children/" + childId + "/pickup-persons")
                        .file(image("photo"))
                        .param("fullName", "Bà Tư").param("relationship", "Bà ngoại").param("phoneNumber", "+84933333333")
                        .header("Authorization", "Bearer " + parent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0933333333"))
                .andExpect(jsonPath("$.data.photoUrl").exists())
                .andReturn();
        long pickupId = id(added, "$.data.id");

        // Another parent cannot see or edit this child
        String otherPhone = nextPhone();
        createChild(principal, otherPhone);
        String otherParent = login(otherPhone, otherPhone);
        mockMvc.perform(get("/api/parent/children/" + childId + "/pickup-persons")
                        .header("Authorization", "Bearer " + otherParent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1500));
        mockMvc.perform(delete("/api/parent/pickup-persons/" + pickupId).header("Authorization", "Bearer " + otherParent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1501));

        // Teacher sees only approved pickup persons; parent cannot approve
        mockMvc.perform(get("/api/teacher/children/" + childId + "/pickup-persons").header("Authorization", "Bearer " + teacher))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(post("/api/management/pickup-persons/" + pickupId + "/approve")
                        .header("Authorization", "Bearer " + parent))
                .andExpect(status().isForbidden());

        // Management sees it in the pending list and approves it
        mockMvc.perform(get("/api/management/pickup-persons").header("Authorization", "Bearer " + principal))
                .andExpect(jsonPath("$.data[?(@.id == " + pickupId + ")]").exists());
        mockMvc.perform(post("/api/management/pickup-persons/" + pickupId + "/approve")
                        .header("Authorization", "Bearer " + principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").exists());
        mockMvc.perform(get("/api/teacher/children/" + childId + "/pickup-persons").header("Authorization", "Bearer " + teacher))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].fullName").value("Bà Tư"));

        // Parent edits it (no new photo) -> back to PENDING, hidden from teachers
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/parent/pickup-persons/" + pickupId)
                        .param("fullName", "Bà Tư").param("relationship", "Bà nội").param("phoneNumber", "0933333333")
                        .header("Authorization", "Bearer " + parent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.relationship").value("Bà nội"));
        mockMvc.perform(get("/api/teacher/children/" + childId + "/pickup-persons").header("Authorization", "Bearer " + teacher))
                .andExpect(jsonPath("$.data", hasSize(0)));

        // Management rejects with a reason
        mockMvc.perform(post("/api/management/pickup-persons/" + pickupId + "/reject")
                        .header("Authorization", "Bearer " + principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Ảnh không rõ mặt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("Ảnh không rõ mặt"));

        // Parent deletes it
        mockMvc.perform(delete("/api/parent/pickup-persons/" + pickupId).header("Authorization", "Bearer " + parent))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/parent/children/" + childId + "/pickup-persons").header("Authorization", "Bearer " + parent))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void everyUserHasAvatarAndImages() throws Exception {
        String principal = login(PRINCIPAL_EMAIL, PRINCIPAL_PASSWORD);
        String teacher = createTeacherAndLogin(principal);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/users/me/avatar").file(image("file"))
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.containsString("avatars/")));
        MvcResult me = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + teacher))
                .andExpect(jsonPath("$.data.avatarUrl").exists())
                .andReturn();
        long teacherId = id(me, "$.data.id");

        MvcResult added = mockMvc.perform(multipart("/api/users/me/images").file(image("file")).param("title", "Chứng chỉ")
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Chứng chỉ"))
                .andReturn();
        long imageId = id(added, "$.data.id");

        // Management can view a staff member's images; a teacher cannot use that endpoint
        mockMvc.perform(get("/api/management/users/" + teacherId + "/images").header("Authorization", "Bearer " + principal))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/api/management/users/" + teacherId + "/images").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());

        // Another user cannot delete it
        mockMvc.perform(delete("/api/users/me/images/" + imageId).header("Authorization", "Bearer " + principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1403));
        mockMvc.perform(delete("/api/users/me/images/" + imageId).header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me/images").header("Authorization", "Bearer " + teacher))
                .andExpect(jsonPath("$.data", hasSize(0)));

        // Missing file part
        mockMvc.perform(multipart("/api/users/me/images").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1000));
    }

    private static MockMultipartFile image(String part) {
        return new MockMultipartFile(part, "a.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4});
    }

    private static String nextPhone() {
        return "09" + String.format("%08d", PHONE_SEQ.incrementAndGet());
    }

    private long createChild(String managementToken, String parentPhone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/management/children")
                        .header("Authorization", "Bearer " + managementToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"childName\":\"Bé\",\"parentPhone\":\"" + parentPhone + "\",\"parentName\":\"PH\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return id(result, "$.data.id");
    }

    private String createTeacherAndLogin(String principalToken) throws Exception {
        String email = "gv" + PHONE_SEQ.incrementAndGet() + "@gmail.com";
        mockMvc.perform(post("/api/management/staff").header("Authorization", "Bearer " + principalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"fullName\":\"GV\",\"password\":\"teacher123\",\"role\":\"TEACHER\"}"))
                .andExpect(status().isCreated());
        return login(email, "teacher123");
    }

    private String login(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").header("X-Client-Type", "mobile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private static long id(MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }
}
