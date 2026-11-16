package com.carenest.repository.organization;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Bộ dữ liệu dev (db/dev, sinh bởi scripts/dev/gen-dev-dataset.mjs): nạp sạch, nhất quán, chạy lại không đổi, phủ mọi bảng. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DevSeedIntegrationTest {
    private static final String SCHEMA = "dev_dataset";
    // Mỗi file một truy vấn trả (tên kiểm, số vi phạm); tách file vì mỗi phần có CTE riêng
    private static final List<String> INVARIANT_FILES =
            List.of("invariants.sql", "kitchen-invariants.sql", "year-invariants.sql");

    // Bảng không seed có chủ đích: cần file media/S3 (ảnh bữa, chữ ký, đính kèm, phiếu kiểm thực ký), token/OTP phát
    // sinh lúc chạy, hoặc nghiệp vụ đã bỏ (phiếu theo dõi sau khám, HLT-16)
    private static final Set<String> EMPTY_BY_DESIGN = Set.of(
            "flyway_schema_history",
            "password_tokens",
            "otp_send_limits",
            "parent_activation_codes",
            "device_tokens",
            // Không tạo tệp S3 hoặc lịch sử giao tới thiết bị khi chưa có token thật.
            "announcement_attachments",
            "notification_push_deliveries",
            "user_signatures",
            "meal_photos",
            "inspection_sheets",
            "health_follow_ups",
            "health_follow_up_events",
            "health_follow_up_images",
            "edu_resource_attachments");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @BeforeAll
    void loadDataset() {
        flyway().migrate();
    }

    @Test
    @DisplayName(
            "Bộ dữ liệu dev nhất quán: phân công, người nhận tài khoản, điểm danh–đơn nghỉ–đón trẻ, đánh giá, sức khỏe")
    void datasetSatisfiesInvariants() throws IOException {
        Map<String, Long> violations = new LinkedHashMap<>();
        for (String file : INVARIANT_FILES) {
            String sql = new ClassPathResource("dev-seed/" + file).getContentAsString(StandardCharsets.UTF_8);
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (Statement st = connection.createStatement()) {
                    st.execute("set search_path to " + SCHEMA);
                    try (ResultSet rs = st.executeQuery(sql)) {
                        while (rs.next()) {
                            violations.put(file + ":" + rs.getString(1), rs.getLong(2));
                        }
                    }
                    st.execute("set search_path to public");
                }
                return null;
            });
        }

        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .allSatisfy((check, count) -> assertThat(count).as(check).isZero());
    }

    @Test
    @DisplayName("Mọi bảng nghiệp vụ có dữ liệu (trừ bảng media/token/chưa tới kỳ), vào màn nào cũng có dữ liệu")
    void datasetFillsEveryBusinessTable() {
        List<String> empty = tables().stream()
                .filter(t -> !EMPTY_BY_DESIGN.contains(t))
                .filter(t -> count(t) == 0)
                .toList();

        assertThat(empty).isEmpty();
        assertThat(count("announcement_attachments")).isZero();
        assertThat(count("notification_push_deliveries")).isZero();
        assertThat(count("user_accounts")).isGreaterThan(200);
        assertThat(count("children")).isGreaterThan(200);
    }

    @Test
    @DisplayName("Chạy lại seed (đổi checksum) không nhân đôi dữ liệu")
    void rerunKeepsRowCounts() {
        Map<String, Long> before = rowCounts();
        jdbc.update("update " + SCHEMA + ".flyway_schema_history set checksum = 0 where script like 'R__dev_%'");
        flyway().migrate();

        Map<String, Long> after = rowCounts();
        after.remove("flyway_schema_history");
        before.remove("flyway_schema_history");
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("Không còn tài khoản/dữ liệu demo cũ: tên đăng nhập là SĐT, không tên chứa Demo")
    void datasetHasNoDemoIdentities() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + SCHEMA
                                + ".user_accounts where username !~ '^0[0-9]{9}$' and username not in ('admin', 'principal', 'viceprincipal', 'teacher1', 'teacher2', 'parent1', 'parent2', 'kitchen')"
                                + " or full_name ilike '%demo%'",
                        Long.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + SCHEMA + ".children where full_name ilike '%demo%'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + SCHEMA + ".campuses where name ilike '%demo%'", Long.class))
                .isZero();
    }

    private Map<String, Long> rowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        tables().forEach(t -> counts.put(t, count(t)));
        return counts;
    }

    private List<String> tables() {
        return jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = ? and table_type = 'BASE TABLE'"
                        + " order by table_name",
                String.class,
                SCHEMA);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + SCHEMA + "." + table, Long.class);
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(SCHEMA)
                .locations("classpath:db/migration", "classpath:db/dev")
                .load();
    }
}
