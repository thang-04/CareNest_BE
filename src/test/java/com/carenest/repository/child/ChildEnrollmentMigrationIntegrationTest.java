package com.carenest.repository.child;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migration V10 trên PostgreSQL thật, không cần Spring: nâng cấp dữ liệu có sẵn (schema {@code legacy}, mục 3.9 của plan)
 * và ràng buộc hồ sơ trẻ, phụ huynh (schema {@code public}). Cần Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class ChildEnrollmentMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static JdbcTemplate db;

    @BeforeAll
    static void migrateLatest() {
        DataSource dataSource = dataSource("public");
        flyway(dataSource, "public", null).migrate();
        db = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("CHD-03, AUTH-18: V10 nâng cấp trẻ và liên kết phụ huynh có sẵn")
    void upgradesExistingChildrenAndGuardianLinks() {
        DataSource dataSource = dataSource("legacy");
        flyway(dataSource, "legacy", "9").migrate();
        JdbcTemplate legacy = new JdbcTemplate(dataSource);
        long campus = id(legacy, "INSERT INTO campuses (code, name) VALUES ('CS-L', 'L') RETURNING id");
        long year = id(
                legacy,
                "INSERT INTO school_years (name, start_date, end_date) "
                        + "VALUES ('2026-2027', '2026-09-01', '2027-05-31') RETURNING id");
        long classroom = id(
                legacy,
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'L1') RETURNING id",
                campus,
                year);
        long studying =
                id(legacy, "INSERT INTO children (full_name, date_of_birth) VALUES ('An', '2022-01-01') RETURNING id");
        long leftClass = id(
                legacy, "INSERT INTO children (full_name, date_of_birth) VALUES ('Bình', '2022-02-02') RETURNING id");
        legacy.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                studying,
                classroom);
        legacy.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01', '2026-09-30')",
                leftClass,
                classroom);
        long phoneParent = id(
                legacy,
                "INSERT INTO user_accounts (keycloak_user_id, username, email, full_name) "
                        + "VALUES ('kc-1', '0912345678', 'me@example.test', 'Mẹ An') RETURNING id");
        long oldParent = id(
                legacy,
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                        + "VALUES ('kc-2', 'parent.old', 'Bố Bình') RETURNING id");
        legacy.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'MOTHER')",
                phoneParent,
                studying);
        legacy.update(
                "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'FATHER')",
                oldParent,
                leftClass);

        flyway(dataSource, "legacy", null).migrate();

        assertThat(legacy.queryForMap(
                        "SELECT code, status, enrolled_on, intake_campus_id FROM children WHERE id = ?", studying))
                .containsEntry("code", "HS26001")
                .containsEntry("status", "ACTIVE")
                .containsEntry("enrolled_on", java.sql.Date.valueOf(LocalDate.of(2026, 9, 1)))
                .containsEntry("intake_campus_id", null);
        // Không còn lớp ⇒ chờ xếp lớp ở điểm trường của lớp gần nhất (CHD-02)
        assertThat(legacy.queryForMap("SELECT code, status, intake_campus_id FROM children WHERE id = ?", leftClass))
                .containsEntry("code", "HS26002")
                .containsEntry("status", "PENDING_PLACEMENT")
                .containsEntry("intake_campus_id", campus);
        assertThat(legacy.queryForList(
                        "SELECT child_id, position, full_name, relationship, phone, email, user_id, citizen_id "
                                + "FROM child_guardians"))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsAllEntriesOf(Map.of(
                                "child_id", studying,
                                "position", 0,
                                "full_name", "Mẹ An",
                                "relationship", "MOTHER",
                                "phone", "0912345678",
                                "email", "me@example.test",
                                "user_id", phoneParent))
                        .containsEntry("citizen_id", null));
        assertThat(legacy.queryForObject(
                        "SELECT last_number FROM child_code_sequences WHERE code_prefix = 'HS26'", Integer.class))
                .isEqualTo(2);
        // CHD-08: hồ sơ cũ thiếu giới tính chỉ sửa được khi nhập đủ
        assertThatThrownBy(() -> legacy.update("UPDATE children SET readmitted_on = NULL WHERE id = ?", studying))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> legacy.update("UPDATE children SET gender = 'MALE' WHERE id = ?", studying))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AUTH-18: quan hệ nhận 6 loại cho cả liên kết tài khoản và hồ sơ trẻ")
    void relationshipAcceptsSixKinds() {
        long child = child("HS26101", "ACTIVE", null);
        long parent = account("kc-r1", "0911000001");

        assertThatCode(() -> db.update(
                        "INSERT INTO guardian_child_links (user_id, child_id, relationship) VALUES (?, ?, 'GRANDMOTHER')",
                        parent,
                        child))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guardian(child, 0, "UNCLE", "0911000002", "999911000002"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> guardian(child, 0, "SIBLING", "0911000002", "999911000002"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AUTH-18: mỗi trẻ tối đa 3 phụ huynh, hai phụ huynh của một trẻ không chung SĐT/CCCD")
    void guardiansOfOneChildAreAtMostThreeAndDistinct() {
        long full = child("HS26102", "ACTIVE", null);
        guardian(full, 0, "FATHER", "0911000011", "999911000011");
        guardian(full, 1, "MOTHER", "0911000012", "999911000012");
        guardian(full, 2, "GRANDFATHER", "0911000013", "999911000013");
        assertThatThrownBy(() -> guardian(full, 3, "GRANDMOTHER", "0911000014", "999911000014"))
                .isInstanceOf(DataIntegrityViolationException.class);

        long child = child("HS26103", "ACTIVE", null);
        guardian(child, 0, "FATHER", "0911000021", "999911000021");
        assertThatThrownBy(() -> guardian(child, 1, "MOTHER", "0911000021", "999911000022"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> guardian(child, 1, "MOTHER", "0911000022", "999911000021"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // SĐT lưu dạng chuẩn hóa 10 số bắt đầu 0
        assertThatThrownBy(() -> guardian(child, 1, "MOTHER", "+84911000022", "999911000022"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Một phụ huynh trên hồ sơ của hai anh/chị em là hợp lệ
        assertThatCode(() -> guardian(child("HS26104", "ACTIVE", null), 0, "FATHER", "0911000021", "999911000021"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CHD-03: ngày, lý do nghỉ học chỉ có khi Đã nghỉ, không trước ngày tiếp nhận")
    void leftDateAndReasonOnlyWhenLeft() {
        assertThatThrownBy(() -> child("HS26111", "LEFT", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
                        db.update("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, "
                                + "left_on, left_reason) VALUES ('HS26112', 'Trẻ', '2022-01-01', 'MALE', 'ACTIVE', '2026-09-01', "
                                + "'2026-10-01', 'Chuyển nhà')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
                        db.update("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, "
                                + "left_on, left_reason) VALUES ('HS26113', 'Trẻ', '2022-01-01', 'MALE', 'LEFT', '2026-09-01', "
                                + "'2026-08-31', 'Chuyển nhà')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() ->
                        db.update("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, "
                                + "left_on, left_reason) VALUES ('HS26114', 'Trẻ', '2022-01-01', 'MALE', 'LEFT', '2026-09-01', "
                                + "'2026-10-01', 'Chuyển nhà')"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CHD-08, CHD-02: hồ sơ mới phải có giới tính, trẻ chờ xếp lớp phải có điểm trường tiếp nhận")
    void newChildMustBeComplete() {
        long campus = id(db, "INSERT INTO campuses (code, name) VALUES ('CS-C', 'C') RETURNING id");

        assertThatThrownBy(() -> db.update("INSERT INTO children (code, full_name, date_of_birth, status, enrolled_on) "
                        + "VALUES ('HS26121', 'Trẻ', '2022-01-01', 'ACTIVE', '2026-09-01')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> child("HS26122", "PENDING_PLACEMENT", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> child("HS26123", "PENDING_PLACEMENT", campus)).doesNotThrowAnyException();
        // Mã trẻ không trùng
        assertThatThrownBy(() -> child("HS26123", "ACTIVE", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Một trẻ chỉ một enrollment đang mở (một lớp tại một thời điểm)")
    void onlyOneOpenEnrollmentPerChild() {
        long campus = id(db, "INSERT INTO campuses (code, name) VALUES ('CS-E', 'E') RETURNING id");
        long year = id(
                db,
                "INSERT INTO school_years (name, start_date, end_date) "
                        + "VALUES ('E-26', '2026-09-01', '2027-05-31') RETURNING id");
        long classroom = id(
                db,
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'E1') RETURNING id",
                campus,
                year);
        long child = child("HS26131", "ACTIVE", null);
        db.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01', '2026-09-30')",
                child,
                classroom);
        db.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-10-01')",
                child,
                classroom);

        assertThatThrownBy(() -> db.update(
                        "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-10-02')",
                        child,
                        classroom))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHD-05, CHD-06: tuần thai bắt buộc khi sinh non; khai báo \"Có\" phải có nội dung")
    void profileAndDeclarationRequireDetailsWhenReported() {
        long child = child("HS26141", "ACTIVE", null);
        long declarer = account("kc-d1", "declarer.d1");

        assertThatThrownBy(() -> db.update(
                        "INSERT INTO child_profiles (child_id, premature, ethnicity, beneficiary, "
                                + "address_province, address_ward, address_street) VALUES (?, 'YES', 'Kinh', 'Không', 'P', 'W', 'S')",
                        child))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> db.update(
                        "INSERT INTO child_profiles (child_id, premature, gestation_weeks, ethnicity, "
                                + "beneficiary, address_province, address_ward, address_street) "
                                + "VALUES (?, 'YES', 34, 'Kinh', 'Không', 'P', 'W', 'S')",
                        child))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> declaration(child, declarer, "REPORTED", null, "NONE_REPORTED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> declaration(child, declarer, "NONE_REPORTED", null, "REPORTED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> declaration(child, declarer, "REPORTED", "Ăn chay", "REPORTED", "2026-08-15"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AUTH-18: một CCCD chỉ thuộc một tài khoản, đúng 12 chữ số")
    void citizenIdIdentifiesOneAccount() {
        long first = account("kc-c1", "0911000031");
        long second = account("kc-c2", "0911000032");
        db.update("UPDATE user_accounts SET citizen_id = '999911000031' WHERE id = ?", first);

        assertThatThrownBy(() -> db.update("UPDATE user_accounts SET citizen_id = '999911000031' WHERE id = ?", second))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE user_accounts SET citizen_id = '12345' WHERE id = ?", second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static long child(String code, String status, Long intakeCampusId) {
        return id(
                db,
                "INSERT INTO children (code, full_name, date_of_birth, gender, status, intake_campus_id, enrolled_on) "
                        + "VALUES (?, 'Trẻ', '2022-01-01', 'FEMALE', ?, ?, '2026-09-01') RETURNING id",
                code,
                status,
                intakeCampusId);
    }

    private static long account(String keycloakUserId, String username) {
        return id(
                db,
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, 'Người dùng') RETURNING id",
                keycloakUserId,
                username);
    }

    private static int guardian(long childId, int position, String relationship, String phone, String citizenId) {
        return db.update(
                "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id) "
                        + "VALUES (?, ?, 'Phụ huynh', ?, ?, ?)",
                childId,
                position,
                relationship,
                phone,
                citizenId);
    }

    private static int declaration(
            long childId, long declaredBy, String dietState, String dietText, String tbState, String tbDate) {
        return db.update(
                "INSERT INTO child_health_declarations (child_id, allergies_state, diet_state, diet_text, other_notes_state, "
                        + "self_history_state, family_history_state, tb_contact_state, tb_contact_date, declared_by) "
                        + "VALUES (?, 'NONE_REPORTED', ?, ?, 'NOT_PROVIDED', 'NONE_REPORTED', 'NONE_REPORTED', ?, ?::date, ?)",
                childId,
                dietState,
                dietText,
                tbState,
                tbDate,
                declaredBy);
    }

    private static long id(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static Flyway flyway(DataSource dataSource, String schema, String target) {
        var config = Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }

    private static DataSource dataSource(String schema) {
        String url = postgres.getJdbcUrl();
        return new DriverManagerDataSource(
                url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword());
    }
}
