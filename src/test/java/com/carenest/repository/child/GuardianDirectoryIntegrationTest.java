package com.carenest.repository.child;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.account.UserAccount;
import com.carenest.entity.child.GuardianChildLink;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.utils.enums.CareNestRole;
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
import org.springframework.data.domain.PageRequest;
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
    private GuardianChildLinkRepository links;

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
        childAn = insert(
                "INSERT INTO children (full_name, date_of_birth) VALUES ('Nguyễn An', '2022-01-01') RETURNING id");
        childBinh = insert(
                "INSERT INTO children (full_name, date_of_birth) VALUES ('Trần Bình', '2022-02-02') RETURNING id");
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
    @DisplayName("AUTH-08: danh sách trẻ chỉ gồm trẻ đang học ở các lớp được phép, lọc theo tên")
    void searchEnrolledChildren() {
        assertThat(children.searchEnrolled(List.of(classA, classB), TODAY, "%", PageRequest.of(0, 10)))
                .extracting("id")
                .containsExactly(childAn);
        assertThat(children.searchEnrolled(List.of(classA), TODAY, "%bình%", PageRequest.of(0, 10)))
                .isEmpty();
        assertThat(children.findByFullNameContainingIgnoreCase("BÌNH", PageRequest.of(0, 10)))
                .extracting("id")
                .containsExactly(childBinh);
    }

    @Test
    @DisplayName("AUTH-18: một phụ huynh liên kết nhiều trẻ; trùng liên kết và quan hệ lạ bị DB chặn")
    void guardianLinks() {
        links.saveAndFlush(GuardianChildLink.create(parent, childAn, "MOTHER"));
        links.saveAndFlush(GuardianChildLink.create(parent, childBinh, "MOTHER"));

        assertThat(links.findChildIdsByUserId(parent)).containsExactlyInAnyOrder(childAn, childBinh);
        assertThat(links.countByChildIds(List.of(childAn, childBinh))).hasSize(2);
        assertThatThrownBy(() -> links.saveAndFlush(GuardianChildLink.create(parent, childAn, "FATHER")))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long other = accounts.save(UserAccount.create("kc-q", "0987654321", null, "PH2", CareNestRole.PARENT))
                .getId();
        assertThatThrownBy(() -> links.saveAndFlush(GuardianChildLink.create(other, childAn, "UNCLE")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
