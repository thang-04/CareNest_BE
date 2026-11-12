package com.carenest.repository.eduplan;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class EduPlanLevelMigrationIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void keepsLegacyRecordsAndSeparatesOpenVersionsByLevel() {
        String schema = "levels_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, "94").migrate();
        String url =
                postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
        Long owner = jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES ('test-owner','test-owner','Synthetic owner','ACTIVE') RETURNING id",
                Long.class);
        Long year = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2098-2099','2098-08-01','2099-07-31') RETURNING id",
                Long.class);
        Long age = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-4'", Long.class);
        Long original = jdbc.queryForObject(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id,version_no) VALUES ('OLD-1',?,?,'Original','','APPROVED',?,1) RETURNING id",
                Long.class,
                year,
                age,
                owner);
        Long open = jdbc.queryForObject(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id,version_no,source_plan_id) VALUES ('OLD-2',?,?,'Draft','','DRAFT',?,2,?) RETURNING id",
                Long.class,
                year,
                age,
                owner,
                original);
        String before = jdbc.queryForObject(
                "SELECT jsonb_agg(to_jsonb(p) ORDER BY id)::text FROM edu_year_plans p", String.class);
        String policy =
                jdbc.queryForObject("SELECT workflow_policy->'year' FROM edu_settings WHERE id=1", String.class);
        flyway(schema, "96").migrate();
        String after = jdbc.queryForObject(
                "SELECT jsonb_agg(to_jsonb(p)-ARRAY['plan_level','curriculum_source_plan_id','legacy_team_without_source','duration_policy_snapshot'] ORDER BY id)::text FROM edu_year_plans p",
                String.class);
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_year_plans WHERE plan_level='TEAM' AND legacy_team_without_source",
                        Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT source_plan_id FROM edu_year_plans WHERE id=?", Long.class, open))
                .isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT workflow_policy->'year' FROM edu_settings WHERE id=1", String.class))
                .isEqualTo(policy);
        Long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name,is_main) VALUES ('SNAP','Synthetic',true) RETURNING id", Long.class);
        Long classroom = jdbc.queryForObject(
                "INSERT INTO classrooms(campus_id,school_year_id,age_group_id,name) VALUES (?,?,?,'Synthetic') RETURNING id",
                Long.class,
                campus,
                year,
                age);
        Long child = jdbc.queryForObject(
                "INSERT INTO children(code,full_name,date_of_birth,gender,status,enrolled_on) VALUES ('HS98001','Synthetic child','2094-01-01','MALE','ACTIVE','2098-09-01') RETURNING id",
                Long.class);
        Long domain = jdbc.queryForObject("SELECT id FROM edu_domains ORDER BY id LIMIT 1", Long.class);
        Long theme = jdbc.queryForObject(
                "INSERT INTO edu_themes(year_plan_id,sort_order,name,start_date,end_date) VALUES (?,1,'Original theme','2098-09-01','2098-09-12') RETURNING id",
                Long.class,
                original);
        Long goal = jdbc.queryForObject(
                "INSERT INTO edu_plan_goals(year_plan_id,domain_id,sort_order,text,goal_no) VALUES (?,?,1,'Original goal',1) RETURNING id",
                Long.class,
                original,
                domain);
        Long content = jdbc.queryForObject(
                "INSERT INTO edu_goal_contents(goal_id,sort_order,text) VALUES (?,1,'Original content') RETURNING id",
                Long.class,
                goal);
        jdbc.update("INSERT INTO edu_content_theme_ticks(content_id,theme_id) VALUES (?,?)", content, theme);
        jdbc.update(
                "INSERT INTO theme_evaluations(classroom_id,campus_id,school_year_id,theme_id,theme_name,start_date,end_date,cells,confirmed_by,confirmed_at,updated_at) VALUES (?,?,?,?,'Original theme','2098-09-01','2098-09-12',?,?,now(),now())",
                classroom,
                campus,
                year,
                theme,
                "[{\"c\":" + child + ",\"g\":" + goal + ",\"m\":true,\"x\":false}]",
                owner);
        flyway(schema, "97").migrate();
        assertThat(jdbc.queryForObject(
                        "SELECT presentation_snapshot->'goals'->0->>'text' FROM theme_evaluations", String.class))
                .isEqualTo("Original goal");
        assertThat(jdbc.queryForObject(
                        "SELECT presentation_snapshot->'children'->0->>'fullName' FROM theme_evaluations",
                        String.class))
                .isEqualTo("Synthetic child");
        jdbc.update(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id,version_no,plan_level) VALUES ('ANNUAL-1',?,?,'Annual','','DRAFT',?,1,'SCHOOL_YEAR')",
                year,
                age,
                owner);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id,version_no,plan_level) VALUES ('ANNUAL-2',?,?,'Annual','','DRAFT',?,2,'SCHOOL_YEAR')",
                        year,
                        age,
                        owner))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id,version_no) VALUES ('FAKE-LEGACY',?,?,'Fake','','DRAFT',?,3)",
                        year,
                        age,
                        owner))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        flyway(schema, "98").migrate();
        assertThat(jdbc.queryForObject("SELECT display_label FROM edu_plan_goals WHERE id=?", String.class, goal))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT text FROM edu_plan_goals WHERE id=?", String.class, goal))
                .isEqualTo("Original goal");
        jdbc.update("UPDATE edu_plan_goals SET display_label='Mục tiêu 01' WHERE id=?", goal);
        assertThatThrownBy(() ->
                        jdbc.update("UPDATE edu_plan_goals SET display_label=? WHERE id=?", "x".repeat(101), goal))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }
}
