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
            "revoked_web_sessions",
            "parent_activation_codes",
            "device_tokens",
            // Không tạo tệp S3 hoặc lịch sử giao tới thiết bị khi chưa có token thật.
            "announcement_attachments",
            "notification_push_deliveries",
            // Hàng chờ email: seed có dòng thì worker gửi thư tới địa chỉ giả
            "notification_email_deliveries",
            // EduDomainBootstrap fills this table at application startup, outside Flyway.
            "edu_plan_defaults",
            "user_signatures",
            "meal_photos",
            "inspection_sheets",
            "health_follow_ups",
            "health_follow_up_events",
            "health_follow_up_images",
            "edu_resource_attachments",
            // Nháp gợi ý thực đơn do người dùng tạo lúc chạy; vector món do embedding API sinh lười (AI tắt ⇒ rỗng)
            "menu_ai_drafts",
            "dish_embeddings",
            // Hiệu ứng của đơn nghỉ ghi lúc chạy khi đơn áp vào điểm danh (V195); đơn seed không có dòng nên hủy theo
            // cách cũ (revertLegacy)
            "leave_attendance_effects");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

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
        assertThat(count("notification_email_deliveries")).isZero();
        assertThat(jdbc.queryForList("SELECT key FROM public.edu_plan_defaults", String.class))
                .contains("config_reset", "day_template");
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
    @DisplayName("Seed yêu cầu mở ghi biên bản không nhân dòng khi có nhiều PHT/hiệu trưởng còn hiệu lực")
    void inspectionGrantsSeedSurvivesDuplicateActiveAssignments() {
        String assignments = SCHEMA + ".staff_assignments";
        jdbc.update("delete from " + SCHEMA + ".inspection_day_grants");
        jdbc.update("insert into " + assignments + " (user_id, staff_role, campus_id, valid_from) "
                + "select user_id, staff_role, campus_id, valid_from from " + assignments
                + " where staff_role in ('VICE_PRINCIPAL', 'PRINCIPAL') and valid_to is null");
        try {
            jdbc.update("update " + SCHEMA
                    + ".flyway_schema_history set checksum = 0 where script like '%R__dev_14_supplier_contracts%'");
            flyway().migrate();

            assertThat(count("inspection_day_grants")).isPositive();
        } finally {
            jdbc.update("delete from " + assignments + " where id in (select id from (select id, row_number() over "
                    + "(partition by user_id, staff_role, campus_id order by id) rn from " + assignments
                    + " where staff_role in ('VICE_PRINCIPAL', 'PRINCIPAL')) t where rn > 1)");
        }
    }

    @Test
    @DisplayName("Chạy lại seed giữ bản đang chờ khác phiên bản mẫu, không tạo lịch sử thiếu chủ sở hữu")
    void rerunWithDifferentOpenYearPlanKeepsExistingPlan() throws IOException {
        String sql = new ClassPathResource("db/dev/R__dev_04_education.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement st = connection.createStatement()) {
                st.execute("set local search_path to " + SCHEMA);
                long planId;
                try (ResultSet rs = st.executeQuery("update edu_year_plans set version_no = 3"
                        + " where code = 'KHN-KHNH-2627-NT-V2' and status = 'REVIEW' returning id")) {
                    assertThat(rs.next()).isTrue();
                    planId = rs.getLong(1);
                }
                st.execute(sql);
                try (ResultSet rs =
                        st.executeQuery("select version_no, status from edu_year_plans where id = " + planId)) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(3);
                    assertThat(rs.getString(2)).isEqualTo("REVIEW");
                }
                try (ResultSet rs = st.executeQuery("select count(*) from edu_year_plans p"
                        + " join edu_year_plans existing on existing.school_year_id = p.school_year_id"
                        + " and existing.age_group_id = p.age_group_id"
                        + " where existing.id = " + planId + " and p.version_no = 2 and not p.disabled")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong(1)).isZero();
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            }
            return null;
        });
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

    @Test
    @DisplayName("CHD-11: dữ liệu dev có đủ trường hợp tiếp nhận năm sau")
    void datasetCoversNextYearIntakeCases() {
        String s = SCHEMA + ".";
        assertThat(jdbc.queryForObject(
                        "select count(distinct a.campus_id) from " + s
                                + "planned_admissions a where a.status = 'PLACED'",
                        Long.class))
                .as("đăng ký đã có lớp ở từng điểm trường")
                .isEqualTo(jdbc.queryForObject("select count(*) from " + s + "campuses", Long.class));
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + s + "planned_admissions a join " + s
                                + "children ch on ch.id = a.child_id where a.status = 'PENDING' and not ch.meal_registered",
                        Long.class))
                .as("đăng ký chưa có lớp, trẻ không báo ăn")
                .isPositive();
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + s + "planned_admissions a join " + s
                                + "children ch on ch.id = a.child_id where a.status = 'CANCELLED' and ch.status = 'LEFT'",
                        Long.class))
                .as("đăng ký đã hủy, trẻ thôi học")
                .isPositive();
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + s + "planned_admissions a join " + s
                                + "school_years y on y.id = a.school_year_id where a.start_date > y.start_date",
                        Long.class))
                .as("đăng ký bắt đầu muộn hơn đầu năm")
                .isPositive();
        assertThat(jdbc.queryForObject(
                        "select count(distinct a.child_id) from " + s + "planned_admissions a"
                                + " join " + s + "child_guardians g on g.child_id = a.child_id and g.account_recipient"
                                + " join " + s
                                + "child_guardians o on o.user_id = g.user_id and o.account_recipient and o.child_id <> a.child_id"
                                + " join " + s + "children oc on oc.id = o.child_id and oc.status = 'ACTIVE'"
                                + " join " + s + "enrollments e on e.child_id = oc.id and e.valid_to is null"
                                + " where a.status in ('PENDING', 'PLACED') and g.user_id is not null",
                        Long.class))
                .as("trẻ năm sau dùng chung tài khoản với trẻ đang học")
                .isPositive();
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + s
                                + "planned_admissions a where a.status in ('PENDING', 'PLACED') and ("
                                + "not exists (select 1 from " + s + "child_profiles p where p.child_id = a.child_id)"
                                + " or not exists (select 1 from " + s
                                + "child_health_declarations d where d.child_id = a.child_id)"
                                + " or (select count(*) from " + s
                                + "child_guardians g where g.child_id = a.child_id and g.account_recipient) <> 1)",
                        Long.class))
                .as("đăng ký đang mở thiếu hồ sơ, khai báo hoặc người nhận tài khoản")
                .isZero();
    }

    @Test
    @DisplayName("CHD-08: mọi hồ sơ trẻ trong dữ liệu dev đều có tuần thai khi sinh")
    void everyProfileHasGestationWeeks() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + SCHEMA + ".child_profiles where gestation_weeks is null", Long.class))
                .isZero();
    }

    @Test
    @DisplayName("Mục 2: mọi trẻ đã có lớp trong dữ liệu dev đều có ngày bắt đầu đi học")
    void everyEnrolledChildHasStartDate() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from " + SCHEMA + ".children c where c.start_date is null"
                                + " and exists (select 1 from " + SCHEMA + ".enrollments e where e.child_id = c.id)",
                        Long.class))
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
