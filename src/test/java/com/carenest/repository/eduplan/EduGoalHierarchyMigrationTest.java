package com.carenest.repository.eduplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class EduGoalHierarchyMigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcTemplate jdbc;
    private String schema;
    private long plan;
    private long domain;

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    @BeforeEach
    void oldDatabase() {
        schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        flyway("31").migrate();
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027','2026-09-01','2027-05-31') RETURNING id");
        long owner = id(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name) VALUES ('demo-upgrade','demo-upgrade','Demo') RETURNING id");
        long age = id("SELECT id FROM age_groups WHERE code='ag-3'");
        domain = id("SELECT id FROM edu_domains ORDER BY sort_order LIMIT 1");
        plan = id(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,status,owner_id) VALUES ('DEMO',?,?, 'Demo','DRAFT',?) RETURNING id",
                year,
                age,
                owner);
        row(101, "goal", 0, 1);
        row(102, "group", 1, null);
        row(103, "goal", 2, 2);
        row(104, "sub", 3, null);
        row(105, "goal", 4, 3);
        row(106, "group", 5, null);
        row(107, "goal", 6, 4);
        row(108, "group", 7, null);
        row(109, "goal", 8, 5);
        row(110, "sub", 9, null);
        row(111, "goal", 10, 6);
        jdbc.update("UPDATE edu_goal_rows SET lineage_id=77 WHERE id=107");
        long content = id(
                "INSERT INTO edu_goal_contents(goal_row_id,sort_order,text) VALUES (107,0,'Nội dung demo') RETURNING id");
        long theme = id(
                "INSERT INTO edu_themes(year_plan_id,sort_order,name,abbr) VALUES (?,0,'Demo','D') RETURNING id", plan);
        jdbc.update("INSERT INTO edu_content_theme_ticks(content_id,theme_id) VALUES (?,?)", content, theme);
        long curriculum = id("SELECT id FROM edu_curriculum_goals ORDER BY id LIMIT 1");
        jdbc.update("UPDATE edu_goal_rows SET curriculum_goal_id=? WHERE id=107", curriculum);
    }

    @Test
    @DisplayName("EDU-09/25: nâng cấp ba bảng giữ ID, lineage, nội dung và cấp cha tùy chọn")
    void migratesHierarchyWithoutInventingParents() {
        flyway("latest").migrate();
        assertThat(id("SELECT count(*) FROM edu_plan_sub_domains")).isEqualTo(2);
        assertThat(id("SELECT count(*) FROM edu_plan_expected_results")).isEqualTo(3);
        assertThat(id("SELECT count(*) FROM edu_plan_goals")).isEqualTo(6);
        assertThat(id("SELECT expected_result_id FROM edu_plan_goals WHERE id=107"))
                .isEqualTo(106);
        assertThat(id("SELECT sub_domain_id FROM edu_plan_goals WHERE id=107")).isEqualTo(104);
        assertThat(id("SELECT expected_result_id FROM edu_plan_goals WHERE id=109"))
                .isEqualTo(108);
        assertThat(id("SELECT sub_domain_id FROM edu_plan_goals WHERE id=111")).isEqualTo(110);
        assertThat(jdbc.queryForObject("SELECT expected_result_id FROM edu_plan_goals WHERE id=105", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT sub_domain_id FROM edu_plan_goals WHERE id=103", Long.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT sub_domain_id FROM edu_plan_goals WHERE id=101", Long.class))
                .isNull();
        assertThat(id("SELECT lineage_id FROM edu_plan_goals WHERE id=107")).isEqualTo(77);
        assertThat(id("SELECT goal_id FROM edu_goal_contents")).isEqualTo(107);
        assertThat(jdbc.queryForObject("SELECT text FROM edu_goal_contents", String.class))
                .isEqualTo("Nội dung demo");
        assertThat(id("SELECT count(*) FROM edu_content_theme_ticks")).isEqualTo(1);
        assertThat(id("SELECT curriculum_goal_id FROM edu_plan_goals WHERE id=107"))
                .isPositive();
        assertThat(id("SELECT nextval('edu_plan_rows_seq')")).isGreaterThan(111);
        assertThat(jdbc.queryForObject("SELECT to_regclass('edu_goal_rows')::text", String.class))
                .isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_plan_goals SET expected_result_id=102 WHERE id=107"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE edu_plan_goals SET domain_id=(SELECT id FROM edu_domains WHERE id<>? LIMIT 1) WHERE id=107",
                        domain))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("EDU-09: dữ liệu thứ tự mơ hồ làm migration rollback, không mất bảng cũ")
    void rejectsAmbiguousOrderBeforeDroppingOldTable() {
        jdbc.update("UPDATE edu_goal_rows SET sort_order=0 WHERE id=102");
        assertThatThrownBy(() -> flyway("latest").migrate()).hasMessageContaining("ambiguous");
        assertThat(id("SELECT count(*) FROM edu_goal_rows")).isEqualTo(11);
    }

    @Test
    @DisplayName("EDU-09: nội dung trỏ tiêu đề làm migration rollback, không âm thầm xóa")
    void rejectsContentAttachedToHeading() {
        jdbc.update("UPDATE edu_goal_contents SET goal_row_id=102");
        assertThatThrownBy(() -> flyway("latest").migrate()).hasMessageContaining("heading");
        assertThat(id("SELECT count(*) FROM edu_goal_rows")).isEqualTo(11);
    }

    @Test
    @DisplayName("EDU-09: FK cha chặn plan/sub khác; ID mới dùng chung sequence")
    void enforcesParentOwnershipAndSharedIdentity() {
        flyway("latest").migrate();
        long owner = id("SELECT owner_id FROM edu_year_plans WHERE id=?", plan);
        long year = id("SELECT school_year_id FROM edu_year_plans WHERE id=?", plan);
        long age = id("SELECT id FROM age_groups WHERE code='ag-4'");
        long other = id(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,status,owner_id) VALUES ('OTHER',?,?,'Demo','DRAFT',?) RETURNING id",
                year,
                age,
                owner);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO edu_plan_expected_results(year_plan_id,domain_id,sort_order,text,sub_domain_id) VALUES (?,?,0,'Sai cha',104)",
                        other,
                        domain))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_plan_goals SET sub_domain_id=110 WHERE id=107"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        long sub = id(
                "INSERT INTO edu_plan_sub_domains(year_plan_id,domain_id,sort_order,text) VALUES (?,?,11,'Demo') RETURNING id",
                plan,
                domain);
        long group = id(
                "INSERT INTO edu_plan_expected_results(year_plan_id,domain_id,sort_order,text,sub_domain_id) VALUES (?,?,12,'Demo',?) RETURNING id",
                plan,
                domain,
                sub);
        long goal = id(
                "INSERT INTO edu_plan_goals(year_plan_id,domain_id,sort_order,text,sub_domain_id,expected_result_id,goal_no) VALUES (?,?,13,'Demo',?,?,7) RETURNING id",
                plan,
                domain,
                sub,
                group);
        assertThat(sub).isGreaterThan(111);
        assertThat(group).isGreaterThan(sub);
        assertThat(goal).isGreaterThan(group);
        jdbc.update("DELETE FROM edu_plan_sub_domains WHERE id=?", sub);
        assertThat(id("SELECT count(*) FROM edu_plan_goals WHERE id=?", goal)).isZero();
    }

    @Test
    @DisplayName("EDU-09/25: nâng cấp nhiều lĩnh vực/phiên bản không nối nhầm cha; giữ sequence đã cấp")
    void partitionsParentsByPlanAndDomainAcrossVersions() {
        long otherDomain = id("SELECT id FROM edu_domains WHERE id<>? ORDER BY id LIMIT 1", domain);
        long year = id("SELECT school_year_id FROM edu_year_plans WHERE id=?", plan);
        long age = id("SELECT age_group_id FROM edu_year_plans WHERE id=?", plan);
        long owner = id("SELECT owner_id FROM edu_year_plans WHERE id=?", plan);
        jdbc.update("UPDATE edu_year_plans SET status='APPROVED' WHERE id=?", plan);
        long v2 = id(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,status,owner_id,version_no,source_plan_id) VALUES ('DEMO-V2',?,?,'Demo v2','DRAFT',?,2,?) RETURNING id",
                year,
                age,
                owner,
                plan);
        jdbc.update(
                "INSERT INTO edu_goal_rows(id,year_plan_id,domain_id,sort_order,kind,text,goal_no,lineage_id) OVERRIDING SYSTEM VALUE VALUES "
                        + "(201,?,?,0,'group','Nhóm lĩnh vực khác',NULL,NULL),"
                        + "(202,?,?,1,'goal','MT lĩnh vực khác',7,NULL),"
                        + "(301,?,?,0,'sub','Lĩnh vực con bản 2',NULL,104),"
                        + "(302,?,?,1,'group','Nhóm bản 2',NULL,106),"
                        + "(303,?,?,2,'goal','MT bản 2',1,107)",
                plan,
                otherDomain,
                plan,
                otherDomain,
                v2,
                domain,
                v2,
                domain,
                v2,
                domain);
        jdbc.execute("SELECT setval(pg_get_serial_sequence('edu_goal_rows','id'),500,true)");
        flyway("latest").migrate();
        assertThat(id("SELECT expected_result_id FROM edu_plan_goals WHERE id=202"))
                .isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT sub_domain_id FROM edu_plan_goals WHERE id=202", Long.class))
                .isNull();
        assertThat(id("SELECT expected_result_id FROM edu_plan_goals WHERE id=303"))
                .isEqualTo(302);
        assertThat(id("SELECT sub_domain_id FROM edu_plan_goals WHERE id=303")).isEqualTo(301);
        assertThat(id("SELECT lineage_id FROM edu_plan_goals WHERE id=303")).isEqualTo(107);
        assertThat(id("SELECT expected_result_id FROM edu_plan_goals WHERE id=107"))
                .isEqualTo(106);
        assertThat(id("SELECT count(*) FROM edu_plan_goals")).isEqualTo(8);
        assertThat(id("SELECT nextval('edu_plan_rows_seq')")).isGreaterThan(500);
    }

    private void row(long id, String kind, int order, Integer goalNo) {
        jdbc.update(
                "INSERT INTO edu_goal_rows(id,year_plan_id,domain_id,sort_order,kind,text,goal_no) OVERRIDING SYSTEM VALUE VALUES (?,?,?,?,?,'Demo',?)",
                id,
                plan,
                domain,
                order,
                kind,
                goalNo);
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
