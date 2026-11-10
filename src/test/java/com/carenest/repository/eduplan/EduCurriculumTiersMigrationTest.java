package com.carenest.repository.eduplan;

import static org.assertj.core.api.Assertions.assertThat;

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
class EduCurriculumTiersMigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcTemplate jdbc;
    private String schema;
    private long curriculumGoal;

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
    void beforeTiers() {
        schema = "tiers_" + UUID.randomUUID().toString().replace("-", "");
        flyway("36").migrate();
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        curriculumGoal = id("SELECT g.id FROM edu_curriculum_goals g JOIN age_groups a ON a.id = g.age_group_id "
                + "WHERE a.code = 'ag-3' ORDER BY g.sort_order LIMIT 1");
    }

    @Test
    @DisplayName("EDU-28: V37 tách danh mục 3 tầng và gắn liên kết cho tiêu đề kế hoạch cũ trùng câu của Bộ")
    void splitsCatalogAndLinksMatchingHeadings() {
        String sub = jdbc.queryForObject(
                "SELECT sub_domain FROM edu_curriculum_goals WHERE id = ?", String.class, curriculumGoal);
        String group = jdbc.queryForObject(
                "SELECT group_text FROM edu_curriculum_goals WHERE id = ?", String.class, curriculumGoal);
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027','2026-09-01','2027-05-31') RETURNING id");
        long owner = id(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name) VALUES ('tiers','tiers','Demo') RETURNING id");
        long domain = id("SELECT domain_id FROM edu_curriculum_goals WHERE id = ?", curriculumGoal);
        long plan = id(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,status,owner_id) "
                        + "VALUES ('TIERS',?,(SELECT id FROM age_groups WHERE code='ag-3'),'Demo','DRAFT',?) RETURNING id",
                year,
                owner);
        // Lĩnh vực con chữ trùng Bộ (khác khoảng trắng); nhóm 1 trùng Bộ; nhóm 2 tự gõ, cùng có mục tiêu lấy từ Bộ
        long planSub = id(
                "INSERT INTO edu_plan_sub_domains(year_plan_id,domain_id,sort_order,text) VALUES (?,?,0,?) RETURNING id",
                plan,
                domain,
                "  " + sub + " ");
        long planGroup = id(
                "INSERT INTO edu_plan_expected_results(year_plan_id,domain_id,sort_order,text,sub_domain_id) "
                        + "VALUES (?,?,1,?,?) RETURNING id",
                plan,
                domain,
                group,
                planSub);
        long ownGroup = id(
                "INSERT INTO edu_plan_expected_results(year_plan_id,domain_id,sort_order,text,sub_domain_id) "
                        + "VALUES (?,?,3,'Nhóm trường tự đặt',?) RETURNING id",
                plan,
                domain,
                planSub);
        jdbc.update(
                "INSERT INTO edu_plan_goals(year_plan_id,domain_id,sort_order,text,sub_domain_id,expected_result_id,goal_no,curriculum_goal_id) "
                        + "VALUES (?,?,2,'MT 1',?,?,1,?)",
                plan,
                domain,
                planSub,
                planGroup,
                curriculumGoal);
        jdbc.update(
                "INSERT INTO edu_plan_goals(year_plan_id,domain_id,sort_order,text,sub_domain_id,expected_result_id,goal_no,curriculum_goal_id) "
                        + "VALUES (?,?,4,'MT 2',?,?,2,?)",
                plan,
                domain,
                planSub,
                ownGroup,
                curriculumGoal);

        flyway("37").migrate();

        Long catalogGroup = jdbc.queryForObject(
                "SELECT expected_result_id FROM edu_curriculum_goals WHERE id = ?", Long.class, curriculumGoal);
        Long catalogSub = jdbc.queryForObject(
                "SELECT sub_domain_id FROM edu_curriculum_expected_results WHERE id = ?", Long.class, catalogGroup);
        assertThat(jdbc.queryForObject(
                        "SELECT text FROM edu_curriculum_expected_results WHERE id = ?", String.class, catalogGroup))
                .isEqualTo(group);
        assertThat(jdbc.queryForObject(
                        "SELECT text FROM edu_curriculum_sub_domains WHERE id = ?", String.class, catalogSub))
                .isEqualTo(sub);
        assertThat(id("SELECT count(*) FROM edu_curriculum_goals WHERE expected_result_id IS NULL"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT curriculum_sub_domain_id FROM edu_plan_sub_domains WHERE id = ?", Long.class, planSub))
                .isEqualTo(catalogSub);
        assertThat(jdbc.queryForObject(
                        "SELECT curriculum_expected_result_id FROM edu_plan_expected_results WHERE id = ?",
                        Long.class,
                        planGroup))
                .isEqualTo(catalogGroup);
        assertThat(jdbc.queryForObject(
                        "SELECT curriculum_expected_result_id FROM edu_plan_expected_results WHERE id = ?",
                        Long.class,
                        ownGroup))
                .isNull();
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
