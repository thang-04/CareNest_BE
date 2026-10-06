package com.carenest.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Khóa hợp đồng API: OpenAPI sinh từ code phải trùng docs/api/openapi.yaml (ADR-0012).
 * Đổi có chủ đích ⇒ chạy với -Dopenapi.snapshot.update=true, commit file, nêu consumer FE/APP.
 * Không có Docker ⇒ test bị skip = CHƯA kiểm chứng (docs/quality/VERIFICATION.md).
 */
@SpringBootTest(
        properties = {
            // Snapshot không phụ thuộc API_PREFIX của máy chạy
            "carenest.api.prefix=/api",
            // Thứ tự key ổn định để diff có nghĩa
            "springdoc.writer-with-order-by-keys=true",
            // Controller chỉ có trong src/test, không thuộc hợp đồng
            "springdoc.paths-to-exclude=/api/response-contract/**,/api/security-test/**"
        })
// Bỏ filter bảo mật (nếu có) để đọc được api-docs.yaml
@AutoConfigureMockMvc(addFilters = false)
@Testcontainers(disabledWithoutDocker = true)
class OpenApiSnapshotTest {

    // Surefire chạy với working directory = thư mục gốc module
    private static final Path SNAPSHOT = Path.of("docs/api/openapi.yaml");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiMatchesCommittedSnapshot() throws Exception {
        String actual = normalize(mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8));

        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual, StandardCharsets.UTF_8);
            return;
        }
        assertThat(SNAPSHOT)
                .as("Chưa có snapshot — chạy với -Dopenapi.snapshot.update=true")
                .exists();
        assertThat(actual)
                .as(
                        "API khác docs/api/openapi.yaml. Có chủ đích ⇒ -Dopenapi.snapshot.update=true + nêu tác động FE/APP")
                .isEqualTo(normalize(Files.readString(SNAPSHOT, StandardCharsets.UTF_8)));
    }

    private static String normalize(String yaml) {
        return yaml.replace("\r\n", "\n").stripTrailing() + "\n";
    }
}
