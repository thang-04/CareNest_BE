package com.carenest.repository.child;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.account.UserAccount;
import com.carenest.entity.child.ChildGuardian;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.GuardianRelationship;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Danh sách trẻ theo lớp đang học + liên kết phụ huynh (V3 + V9) trên PostgreSQL thật. Cần Docker. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GuardianDirectoryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ChildRepository children;

    @Autowired
    private ChildGuardianRepository links;

    @Autowired
    private UserAccountRepository accounts;

    private Long classA;
    private Long classB;
    private Long childAn;
    private Long childBinh;
    private Long parent;

    @BeforeEach
    void seed() {
        Long campus = insert("INSERT INTO campuses (code, name) VALUES ('G-A', 'A') RETURNING id");
        Long year = insert(
                "INSERT INTO school_years (name, start_date, end_date) VALUES ('G-26', '2026-09-01', '2027-05-31') RETURNING id");
        classA = insert(
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'A1') RETURNING id",
                campus,
                year);
        classB = insert(
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'A2') RETURNING id",
                campus,
                year);
        childAn = insert("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) "
                + "VALUES ('HS26001', 'Nguyễn An', '2022-01-01', 'MALE', 'ACTIVE', '2026-09-01') RETURNING id");
        childBinh = insert("INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on) "
                + "VALUES ('HS26002', 'Trần Bình', '2022-02-02', 'FEMALE', 'ACTIVE', '2026-09-01') RETURNING id");
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                childAn,
                classA);
        // Bình đã rời lớp A2 trước hôm nay ⇒ không còn trong danh sách lớp
        jdbc.update(
                "INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES (?, ?, '2026-09-01', '2026-09-30')",
                childBinh,
                classB);
        parent = accounts.save(UserAccount.create("kc-p", "0912345678", null, "PH", CareNestRole.PARENT))
                .getId();
    }

    @AfterEach
    void cleanUp() {
        links.deleteAll();
        jdbc.update("DELETE FROM enrollments");
        jdbc.update("DELETE FROM children");
        accounts.deleteAll();
        jdbc.update("DELETE FROM classrooms");
        jdbc.update("DELETE FROM school_years");
        jdbc.update("DELETE FROM campuses");
    }

    @Test
    @DisplayName(
            "D-4, AUTH-08: danh sách trẻ theo phạm vi lớp, từ khóa họ tên hoặc mã; lớp hiển thị = lớp đang học hoặc lớp cuối")
    void listRowsFollowScopeAndKeyword() {
        // GV lớp A1: chỉ An (Bình đã rời A2 ⇒ lớp hiển thị A2, ngoài phạm vi)
        assertThat(children.findListRows(
                        null, TODAY, List.of("ACTIVE"), false, List.of(-1L), List.of(classA), null, null, ""))
                .extracting(ChildRepository.ChildListRow::getChildId)
                .containsExactly(childAn);
        // Toàn trường, từ khóa theo mã (không phân biệt hoa thường)
        assertThat(children.findListRows(
                        null, TODAY, List.of("ACTIVE"), true, List.of(-1L), List.of(-1L), null, null, "%hs26002%"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getChildId()).isEqualTo(childBinh);
                    assertThat(row.getClassroomId()).isEqualTo(classB);
                });
        assertThat(children.findListRows(
                        null, TODAY, List.of("ACTIVE"), true, List.of(-1L), List.of(-1L), null, null, "%bình%"))
                .extracting(ChildRepository.ChildListRow::getChildId)
                .containsExactly(childBinh);
    }

    @Test
    @DisplayName("AUTH-18: một phụ huynh liên kết nhiều trẻ; trùng liên kết và quan hệ lạ bị DB chặn")
    void guardianLinks() {
        links.saveAndFlush(linked(childAn, 0, "0912000001", "000000000101", parent));
        links.saveAndFlush(linked(childBinh, 0, "0912000001", "000000000101", parent));

        assertThat(links.findLinkedChildIds(parent)).containsExactlyInAnyOrder(childAn, childBinh);
        assertThat(links.countLinkedByChildIds(List.of(childAn, childBinh))).hasSize(2);
        assertThat(links.existsByUserIdAndChildId(parent, childAn)).isTrue();
        // Một tài khoản chỉ liên kết một lần với một trẻ
        assertThatThrownBy(() -> links.saveAndFlush(linked(childAn, 1, "0912000002", "000000000102", parent)))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long other = accounts.save(UserAccount.create("kc-q", "0987654321", null, "PH2", CareNestRole.PARENT))
                .getId();
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id,"
                                + " user_id, linked_at) VALUES (?, 2, 'Chú', 'UNCLE', '0912000003', '000000000103', ?,"
                                + " now())",
                        childAn,
                        other))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ChildGuardian linked(Long childId, int position, String phone, String citizenId, Long userId) {
        ChildGuardian guardian = ChildGuardian.create(
                childId, position, "PH (giả)", GuardianRelationship.MOTHER, phone, citizenId, null);
        guardian.linkAccount(userId, java.time.Instant.now());
        return guardian;
    }

    private Long insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
