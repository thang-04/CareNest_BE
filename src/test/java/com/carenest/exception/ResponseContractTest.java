package com.carenest.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carenest.config.WebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Mọi response, thành công hay lỗi, phải có dạng {code, desc, data} với code = HTTP status. */
@WebMvcTest
@Import(WebMvcConfig.class)
class ResponseContractTest {

    private static final String BASE = "/api/response-contract";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successWithData() throws Exception {
        mockMvc.perform(get(BASE + "/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.desc").value("Get data success"))
                .andExpect(jsonPath("$.data.id").value(123));
    }

    @Test
    void successWithoutDataStillContainsNullDataField() throws Exception {
        mockMvc.perform(get(BASE + "/no-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // jsonPath().exists() coi null là không tồn tại, nên kiểm trực tiếp trên JSON thô
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    void controllerWithoutPrefixIsNotMapped() throws Exception {
        mockMvc.perform(get("/response-contract/data"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void createdUsesStatus201() throws Exception {
        mockMvc.perform(post(BASE + "/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"An\",\"classId\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.classId").value(5));
    }

    @Test
    void validationErrorReturnsFieldErrors() throws Exception {
        mockMvc.perform(post(BASE + "/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.desc").value("Invalid request data"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.field == 'classId')]").exists())
                .andExpect(jsonPath("$.data[?(@.field == 'name')]").exists());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/validate").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post(BASE + "/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(415));
    }

    @Test
    void globalExceptionUsesApiCodeAndMessage() throws Exception {
        mockMvc.perform(get(BASE + "/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.desc").value("Child not found"));

        mockMvc.perform(get(BASE + "/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.desc").value("Meal count already confirmed"));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.desc").value("Resource not found"));
    }

    @Test
    void wrongMethodReturns405() throws Exception {
        mockMvc.perform(post(BASE + "/data"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetail() throws Exception {
        mockMvc.perform(get(BASE + "/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.desc").value("Internal server error"))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }
}
