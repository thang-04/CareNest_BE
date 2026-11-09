package com.carenest.repository.eduplan;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class SeparateEducationPlansMigrationIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static final List<String> OWNED = List.of("edu_plan_goals", "edu_plan_sub_domains",
            "edu_plan_expected_results", "edu_themes", "edu_breaks", "edu_plan_events",
            "edu_document_authors", "edu_plan_branch_templates", "edu_class_docs");
    private static final List<String> UNCHANGED = List.of("edu_goal_contents", "edu_content_theme_ticks", "theme_evaluations");

    @Test
    void preservesAllPayloadsOwnersLineageAndConstraintsIncludingLateWrites() throws Exception {
        String schema = schema();
        JdbcTemplate jdbc = prepare(schema);
        Map<String, String> original = snapshots(jdbc, OWNED);
        Map<String, String> nested = snapshots(jdbc, UNCHANGED);
        String headers = snapshot(jdbc, "edu_year_plans");
        flyway(schema, "89").migrate();
        assertThat(snapshot(jdbc, "edu_year_plans")).isEqualTo(headers);
        for (String table : OWNED) assertThat(snapshotWithoutOwners(jdbc, table)).isEqualTo(original.get(table));
        seed(jdbc, "separate-plans-after-expand.sql");
        Map<String, String> expected = new LinkedHashMap<>();
        for (String table : OWNED) expected.put(table, snapshotWithoutOwners(jdbc, table));
        String teamHeaders = jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(p)-'plan_level' ORDER BY id),'[]')::text FROM edu_year_plans p WHERE plan_level='TEAM'", String.class);
        String annualHeaders = jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(p)-ARRAY['plan_level','curriculum_source_plan_id','legacy_team_without_source','duration_policy_snapshot'] ORDER BY id),'[]')::text FROM edu_year_plans p WHERE plan_level='SCHOOL_YEAR'", String.class);
        flyway(schema, "90").migrate();
        assertThat(jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(p)-'previous_source_plan_id' ORDER BY id)::text FROM edu_team_plans p", String.class)).isEqualTo(teamHeaders);
        assertThat(snapshot(jdbc, "edu_year_plans")).isEqualTo(annualHeaders);
        for (String table : OWNED) {
            String actual = jdbc.queryForObject("SELECT coalesce(jsonb_agg((to_jsonb(c)-ARRAY['school_year_plan_id','team_plan_id'])||jsonb_build_object('year_plan_id',coalesce(school_year_plan_id,team_plan_id)) ORDER BY id),'[]')::text FROM " + table + " c", String.class);
            assertThat(actual).as(table).isEqualTo(expected.get(table));
        }
        assertThat(snapshots(jdbc, UNCHANGED)).isEqualTo(nested);
        assertThat(jdbc.queryForObject("SELECT team_plan_id FROM edu_themes WHERE id=910999", Long.class)).isEqualTo(910202L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_document_authors WHERE id=910901 AND class_doc_id=910201 AND school_year_plan_id IS NULL AND team_plan_id IS NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_team_plans WHERE legacy_team_without_source AND curriculum_source_plan_id IS NULL", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT source_plan_id FROM edu_team_plans WHERE id=910302", Long.class)).isEqualTo(910301L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND column_name='year_plan_id'", Integer.class, schema)).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_plan_goals SET sub_domain_id=910101 WHERE id=910201")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_plan_goals SET expected_result_id=910101 WHERE id=910201")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_plan_branch_templates SET theme_id=910101 WHERE id=910201")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_class_docs SET team_plan_id=NULL,school_year_plan_id=910101 WHERE id=910201")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_document_authors SET team_plan_id=910201 WHERE id=910901")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_themes SET school_year_plan_id=910101 WHERE id=910201")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_team_plans SET version_no=1,source_plan_id=NULL WHERE id=910202")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_team_plans SET status='DRAFT' WHERE id=910201")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname=? AND c.contype='f' AND c.condeferrable AND pg_get_constraintdef(c.oid) LIKE '%domain_id%'", Integer.class, schema)).isEqualTo(6);
        assertSharedSequence(jdbc);
        flyway(schema, "90").validate();
    }

    @Test
    void rejectsUnexpectedExpandedWritersAndRollsBackWithoutChangingPayloads() {
        String schema = schema();
        JdbcTemplate jdbc = prepare(schema);
        flyway(schema, "89").migrate();
        jdbc.update("INSERT INTO edu_team_plans(id,code,school_year_id,age_group_id,title,status,owner_id,legacy_team_without_source) OVERRIDING SYSTEM VALUE SELECT id,'unexpected',school_year_id,age_group_id,'Unexpected writer','DRAFT',owner_id,true FROM edu_year_plans WHERE id=910201");
        Map<String,String> before=snapshots(jdbc, OWNED);
        String headers=snapshot(jdbc,"edu_year_plans");
        assertThatThrownBy(() -> flyway(schema,"90").migrate()).hasStackTraceContaining("must remain empty");
        assertThat(snapshots(jdbc, OWNED)).isEqualTo(before);
        assertThat(snapshot(jdbc,"edu_year_plans")).isEqualTo(headers);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=? AND column_name='plan_level'", Integer.class,schema)).isEqualTo(1);
        jdbc.update("DELETE FROM edu_team_plans");
        flyway(schema,"90").migrate();
    }

    @Test
    void rejectsAnnualClassDocumentInsteadOfInventingAnOwner() {
        String schema=schema();
        JdbcTemplate jdbc=prepare(schema);
        jdbc.update("UPDATE edu_class_docs SET year_plan_id=910101 WHERE id=910201");
        flyway(schema,"89").migrate();
        assertThatThrownBy(() -> flyway(schema,"90").migrate()).hasStackTraceContaining("Class document references annual plan");
        assertThat(jdbc.queryForObject("SELECT year_plan_id FROM edu_class_docs WHERE id=910201",Long.class)).isEqualTo(910101L);
    }

    @Test
    void rollsBackChildDdlAndCopiedHeadersWhenContractFailsAfterCopy() {
        String schema=schema();
        JdbcTemplate jdbc=prepare(schema);
        flyway(schema,"89").migrate();
        Map<String,String> before=snapshots(jdbc,OWNED);
        String headers=snapshot(jdbc,"edu_year_plans");
        jdbc.execute("CREATE FUNCTION reject_synthetic_cutover() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic cutover failure after copy'; END $$");
        jdbc.execute("CREATE TRIGGER reject_synthetic_cutover BEFORE DELETE ON edu_year_plans FOR EACH ROW EXECUTE FUNCTION reject_synthetic_cutover()");
        assertThatThrownBy(() -> flyway(schema,"90").migrate()).hasStackTraceContaining("Synthetic cutover failure after copy");
        assertThat(snapshots(jdbc,OWNED)).isEqualTo(before);
        assertThat(snapshot(jdbc,"edu_year_plans")).isEqualTo(headers);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_team_plans",Integer.class)).isZero();
        jdbc.execute("DROP TRIGGER reject_synthetic_cutover ON edu_year_plans");
        jdbc.execute("DROP FUNCTION reject_synthetic_cutover()");
        flyway(schema,"90").migrate();
    }

    private void assertSharedSequence(JdbcTemplate jdbc) throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try {
            List<Callable<List<Long>>> creates=new ArrayList<>();
            for (String table:List.of("edu_year_plans","edu_team_plans")) creates.add(() -> {
                List<Long> ids=new ArrayList<>();
                boolean team=table.equals("edu_team_plans");
                for (int i=3;i<13;i++) ids.add(jdbc.queryForObject("INSERT INTO "+table+"(code,school_year_id,age_group_id,title,status,owner_id,version_no,source_plan_id"+(team?",curriculum_source_plan_id":"")+") SELECT ?,school_year_id,age_group_id,'Synthetic concurrent','APPROVED',owner_id,?,id"+(team?",curriculum_source_plan_id":"")+" FROM "+table+" WHERE id=? RETURNING id",Long.class,table+"-"+i,i,team?910201L:910101L));
                return ids;
            });
            Set<Long> ids=new HashSet<>();
            for (Future<List<Long>> result:workers.invokeAll(creates)) for (Long id:result.get()) assertThat(ids.add(id)).isTrue();
            assertThat(ids).hasSize(20).allMatch(id -> id>910302L);
            // Một connection mới dùng cùng sequence sau khi các writer đã kết thúc.
            Long next=jdbc.queryForObject("SELECT nextval('edu_plan_id_seq')",Long.class);
            assertThat(next).isGreaterThan(Collections.max(ids));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN ('edu_year_plans','edu_team_plans') AND column_name='id' AND column_default LIKE '%edu_plan_id_seq%'",Integer.class)).isEqualTo(2);
        } finally { workers.shutdownNow(); }
    }

    private JdbcTemplate prepare(String schema) {
        flyway(schema,"88").migrate();
        String url=postgres.getJdbcUrl()+(postgres.getJdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema;
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,postgres.getUsername(),postgres.getPassword()));
        seed(jdbc,"separate-plans-before-expand.sql");
        return jdbc;
    }
    private void seed(JdbcTemplate jdbc,String file) {
        try (var stream = new ClassPathResource("eduplan/"+file).getInputStream()) {
            jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
    }
    private String schema() { return "split_"+UUID.randomUUID().toString().replace("-",""); }
    private String snapshot(JdbcTemplate jdbc,String table) { return jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]')::text FROM "+table+" t",String.class); }
    private String snapshotWithoutOwners(JdbcTemplate jdbc,String table) { return jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t)-ARRAY['school_year_plan_id','team_plan_id'] ORDER BY to_jsonb(t)-ARRAY['school_year_plan_id','team_plan_id']),'[]')::text FROM "+table+" t",String.class); }
    private Map<String,String> snapshots(JdbcTemplate jdbc,List<String> tables) { Map<String,String> result=new LinkedHashMap<>(); for (String table:tables) result.put(table,snapshot(jdbc,table)); return result; }
    private Flyway flyway(String schema,String target) { return Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load(); }
}
