package com.carenest.repository.eduplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class MinistrySourceCorrectionMigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private String schema;
    private JdbcTemplate jdbc;

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
    void prepare() {
        schema = "source_" + UUID.randomUUID().toString().replace("-", "");
        flyway("93").migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword()));
    }

    @Test
    void correctsSourceWithoutChangingGoalIdentity() {
        Long id = jdbc.queryForObject(
                "SELECT g.id FROM edu_curriculum_goals g JOIN age_groups a ON a.id=g.age_group_id WHERE a.code='ag-2' AND g.sort_order=27",
                Long.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_curriculum_goal_contents", Long.class))
                .isEqualTo(101);
        flyway("94").migrate();
        assertThat(jdbc.queryForObject("SELECT goal_text FROM edu_curriculum_goals WHERE id=?", String.class, id))
                .isEqualTo(
                        "Nói được câu đơn, câu có 5 - 7 tiếng, có các từ thông dụng chỉ sự vật, hoạt động, đặc điểm quen thuộc.");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_curriculum_goal_contents WHERE curriculum_goal_id=?", Long.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_curriculum_goal_contents", Long.class))
                .isEqualTo(100);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_curriculum_goals WHERE right(goal_text,1)=':'", Long.class))
                .isEqualTo(33);
    }

    @Test
    void restoresMergedAgeEntriesAndPreservesExistingIds() {
        flyway("94").migrate();
        var oldGoals = jdbc.queryForList("SELECT id, goal_text FROM edu_curriculum_goals ORDER BY id");
        var oldContents = jdbc.queryForList("SELECT id, text FROM edu_curriculum_contents ORDER BY id");
        flyway("95").migrate();
        assertThat(jdbc.queryForList("SELECT id, goal_text FROM edu_curriculum_goals WHERE id <= 327 ORDER BY id"))
                .isEqualTo(oldGoals);
        assertThat(jdbc.queryForList("SELECT id, text FROM edu_curriculum_contents WHERE id <= 423 ORDER BY id"))
                .isEqualTo(oldContents);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_curriculum_goals", Long.class))
                .isEqualTo(331);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_curriculum_contents", Long.class))
                .isEqualTo(494);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_curriculum_contents c JOIN age_groups a ON a.id=c.age_group_id WHERE a.code IN ('ag-4','ag-5') AND c.text='Hô hấp: Hít vào, thở ra.' AND c.sort_order=1",
                        Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_curriculum_goals g JOIN age_groups a ON a.id=g.age_group_id JOIN edu_curriculum_expected_results r ON r.id=g.expected_result_id WHERE a.code='ag-5' AND g.goal_text='Thích chăm sóc cây, con vật thân thuộc.' AND r.group_no='5'",
                        Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_curriculum_contents c JOIN age_groups a ON a.id=c.age_group_id JOIN edu_curriculum_sub_domains s ON s.id=c.sub_domain_id WHERE a.code='ag-2' AND c.category_no='3' AND s.text='Giáo dục dinh dưỡng và sức khỏe'",
                        Long.class))
                .isEqualTo(2);
    }

    @Test
    void leavesOtherCurriculumSourcesUntouched() {
        flyway("94").migrate();
        Long otherSource = jdbc.queryForObject(
                "INSERT INTO edu_curriculum_sources(code,title,document_no,issued_date,issuer,official_url,retrieved_from_url,retrieved_at) SELECT 'OTHER-TEST',title,document_no,issued_date,issuer,official_url,retrieved_from_url,retrieved_at FROM edu_curriculum_sources WHERE code='VBHN-01-2021-GDMN' RETURNING id",
                Long.class);
        jdbc.update(
                "INSERT INTO edu_curriculum_goals(source_id,age_group_id,domain_id,expected_result_id,goal_no,goal_text,section_ref,sort_order) SELECT ?,age_group_id,domain_id,expected_result_id,goal_no,goal_text,section_ref,5000 FROM edu_curriculum_goals ORDER BY id LIMIT 1",
                otherSource);
        flyway("95").migrate();
        assertThat(jdbc.queryForObject(
                        "SELECT sort_order FROM edu_curriculum_goals WHERE source_id=?", Integer.class, otherSource))
                .isEqualTo(5000);
    }

    @Test
    void rejectsChangedBaselineTextWithoutChangingSortOrder() {
        flyway("94").migrate();
        jdbc.update(
                "UPDATE edu_curriculum_contents SET text='changed' WHERE id=(SELECT min(id) FROM edu_curriculum_contents)");
        assertThatThrownBy(() -> flyway("95").migrate())
                .hasMessageContaining("Unexpected catalog baseline text or order");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_curriculum_contents", Long.class))
                .isEqualTo(423);
        assertThat(jdbc.queryForObject("SELECT max(sort_order) FROM edu_curriculum_goals", Integer.class))
                .isLessThan(1000);
    }

    @Test
    void rejectsUnexpectedMergedAgeBaselineWithoutChangingRows() {
        flyway("94").migrate();
        jdbc.update("DELETE FROM edu_curriculum_contents WHERE id=(SELECT min(id) FROM edu_curriculum_contents)");
        assertThatThrownBy(() -> flyway("95").migrate()).hasMessageContaining("Unexpected catalog baseline");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_curriculum_goals", Long.class))
                .isEqualTo(327);
        assertThat(jdbc.queryForObject("SELECT max(sort_order) FROM edu_curriculum_goals", Integer.class))
                .isLessThan(1000);
    }

    @Test
    void rejectsUnexpectedCatalogAndRollsBack() {
        jdbc.update(
                "UPDATE edu_curriculum_goals SET goal_text='changed' WHERE id=(SELECT g.id FROM edu_curriculum_goals g JOIN age_groups a ON a.id=g.age_group_id WHERE a.code='ag-2' AND g.sort_order=28)");
        assertThatThrownBy(() -> flyway("94").migrate())
                .hasMessageContaining("Expected 33 matching unchanged source rows");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_curriculum_goal_contents", Long.class))
                .isEqualTo(101);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_curriculum_goals WHERE right(goal_text,1)=':'", Long.class))
                .isZero();
    }
}
