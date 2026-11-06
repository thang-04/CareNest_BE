package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class EduWorkflowMigrationIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    @DisplayName("EDU-54: expand giữ completed/legacy, seed policy tách biệt và baseline chỉ creator")
    void expandsLegacyWithoutChangingWorkflow() {
        String schema = "wf_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, "79").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword()));
        Long owner = jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES ('owner','owner','Tác giả thử','ACTIVE') RETURNING id",
                Long.class);
        Long reviewer = jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES ('reviewer','reviewer','Người duyệt thử','ACTIVE') RETURNING id",
                Long.class);
        Long year = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        Long age = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-4'", Long.class);
        Long plan = jdbc.queryForObject(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id,issued_by,issued_at) VALUES ('WF-LEGACY',?,?, 'Kế hoạch thử','','APPROVED',?,?,TIMESTAMPTZ '2026-10-01 00:00:00Z') RETURNING id",
                Long.class,
                year,
                age,
                owner,
                reviewer);
        String signers = jdbc.queryForObject("SELECT signers FROM edu_settings WHERE id=1", String.class);
        flyway(schema, "80").migrate();
        assertThat(jdbc.queryForObject("SELECT status FROM edu_year_plans WHERE id=?", String.class, plan))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT issued_by FROM edu_year_plans WHERE id=?", Long.class, plan))
                .isEqualTo(reviewer);
        assertThat(jdbc.queryForList("SELECT user_id FROM edu_document_authors WHERE year_plan_id=?", Long.class, plan))
                .containsExactly(owner);
        assertThat(jdbc.queryForObject("SELECT author_coverage FROM edu_year_plans WHERE id=?", String.class, plan))
                .isEqualTo("LEGACY_BASELINE");
        assertThat(jdbc.queryForObject(
                        "SELECT print_snapshot->>'origin' FROM edu_year_plans WHERE id=?", String.class, plan))
                .isEqualTo("LEGACY_BASELINE");
        assertThat(jdbc.queryForObject("SELECT legacy_signers FROM edu_settings WHERE id=1", String.class))
                .isEqualTo(signers);
        assertThat(jdbc.queryForObject(
                        "SELECT workflow_policy->'year'->'creators'->>0 FROM edu_settings WHERE id=1", String.class))
                .isEqualTo("TEAM_LEADER");
        jdbc.update("UPDATE edu_year_plans SET status='REVIEW' WHERE id=?", plan);
        jdbc.update("UPDATE edu_year_plans SET status='PENDING_VP' WHERE id=?", plan);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO edu_document_authors(year_plan_id,user_id,origin) VALUES (?,?,'CONTENT_WRITE')",
                        plan,
                        owner))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO edu_document_authors(user_id,origin) VALUES (?,'CONTENT_WRITE')", owner))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO edu_document_authors(year_plan_id,user_id,origin) VALUES (?,?,'CONTENT_WRITE')",
                        plan,
                        -123L))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        flyway(schema, "82").migrate();
        String snapshot =
                jdbc.queryForObject("SELECT print_snapshot::text FROM edu_year_plans WHERE id=?", String.class, plan);
        assertThat(snapshot).contains("CREATOR", "Tác giả thử", "Người duyệt thử");
        jdbc.update("UPDATE user_accounts SET full_name='Changed' WHERE id IN (?,?)", owner, reviewer);
        assertThat(jdbc.queryForObject(
                        "SELECT print_snapshot::text FROM edu_year_plans WHERE id=?", String.class, plan))
                .isEqualTo(snapshot);
        flyway(schema, "82").migrate();
        assertThat(jdbc.queryForObject(
                        "SELECT print_snapshot::text FROM edu_year_plans WHERE id=?", String.class, plan))
                .isEqualTo(snapshot);
    }

    @Test
    @DisplayName("EDU-54: contract chuẩn hóa 3 trạng thái DRAFT/REVIEW/APPROVED và siết CHECK constraints")
    void contractsToThreeStatesAndNormalizesLegacy() {
        String schema = "wf_c_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, "80").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword()));
        Long owner = jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES ('owner2','owner2','Tác giả 2','ACTIVE') RETURNING id",
                Long.class);
        Long year = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-C','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        Long age1 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-2'", Long.class);
        Long age2 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);
        Long age3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-4'", Long.class);

        Long planPending = jdbc.queryForObject(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id) VALUES ('WF-P',?,?, 'Kế hoạch P','','PENDING_VP',?) RETURNING id",
                Long.class,
                year,
                age1,
                owner);
        Long planEditing = jdbc.queryForObject(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id,edit_session_id,editing_by,editing_since,editing_at,editing_from) VALUES ('WF-E',?,?, 'Kế hoạch E','','EDITING',?,gen_random_uuid(),?,now(),now(),'DRAFT') RETURNING id",
                Long.class,
                year,
                age2,
                owner,
                owner);
        Long planRejected = jdbc.queryForObject(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id) VALUES ('WF-R',?,?, 'Kế hoạch R','','REJECTED',?) RETURNING id",
                Long.class,
                year,
                age3,
                owner);

        Long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name,status) VALUES ('CS-MIG','Cơ sở di chuyển','ACTIVE') RETURNING id",
                Long.class);
        Long cr1 = jdbc.queryForObject(
                "INSERT INTO classrooms(campus_id,age_group_id,school_year_id,name,capacity) VALUES (?,?,?,'Lớp C1',20) RETURNING id",
                Long.class,
                campus,
                age1,
                year);
        Long cr2 = jdbc.queryForObject(
                "INSERT INTO classrooms(campus_id,age_group_id,school_year_id,name,capacity) VALUES (?,?,?,'Lớp C2',20) RETURNING id",
                Long.class,
                campus,
                age1,
                year);
        Long cr3 = jdbc.queryForObject(
                "INSERT INTO classrooms(campus_id,age_group_id,school_year_id,name,capacity) VALUES (?,?,?,'Lớp C3',20) RETURNING id",
                Long.class,
                campus,
                age1,
                year);
        Long cr4 = jdbc.queryForObject(
                "INSERT INTO classrooms(campus_id,age_group_id,school_year_id,name,capacity) VALUES (?,?,?,'Lớp C4',20) RETURNING id",
                Long.class,
                campus,
                age1,
                year);

        Long docTl = jdbc.queryForObject(
                "INSERT INTO edu_class_docs(classroom_id,year_plan_id,kind,doc_key,status,created_by,updated_by,version) VALUES (?,?,'branch','k-tl','PENDING_TL',?,?,0) RETURNING id",
                Long.class,
                cr1,
                planPending,
                owner,
                owner);
        Long docVp = jdbc.queryForObject(
                "INSERT INTO edu_class_docs(classroom_id,year_plan_id,kind,doc_key,status,created_by,updated_by,version) VALUES (?,?,'branch','k-vp','PENDING_VP',?,?,0) RETURNING id",
                Long.class,
                cr2,
                planPending,
                owner,
                owner);
        Long docHt = jdbc.queryForObject(
                "INSERT INTO edu_class_docs(classroom_id,year_plan_id,kind,doc_key,status,created_by,updated_by,version) VALUES (?,?,'branch','k-ht','PENDING_HT',?,?,0) RETURNING id",
                Long.class,
                cr3,
                planPending,
                owner,
                owner);
        Long docRej = jdbc.queryForObject(
                "INSERT INTO edu_class_docs(classroom_id,year_plan_id,kind,doc_key,status,created_by,updated_by,version) VALUES (?,?,'branch','k-rej','REJECTED',?,?,0) RETURNING id",
                Long.class,
                cr4,
                planPending,
                owner,
                owner);

        String legacyApproval = "[{\"userId\":" + owner
                + ",\"name\":\"Known reviewer\",\"roleKey\":\"TEAM_LEADER\",\"at\":\"2026-10-01T00:00:00Z\",\"note\":\"Original\"}]";
        jdbc.update("UPDATE edu_class_docs SET approvals=?::jsonb, sign_chain=NULL WHERE id=?", legacyApproval, docVp);
        jdbc.update(
                "UPDATE edu_class_docs SET approvals=?::jsonb, sign_chain='[\"TEAM_LEADER\",\"VICE_PRINCIPAL\",\"PRINCIPAL\"]'::jsonb WHERE id=?",
                legacyApproval,
                docHt);
        // Migrate to 81 (contract)
        flyway(schema, "82").migrate();

        assertThat(jdbc.queryForObject("SELECT status FROM edu_year_plans WHERE id=?", String.class, planPending))
                .isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject("SELECT status FROM edu_year_plans WHERE id=?", String.class, planEditing))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT status FROM edu_year_plans WHERE id=?", String.class, planRejected))
                .isEqualTo("DRAFT");

        assertThat(jdbc.queryForObject("SELECT status FROM edu_class_docs WHERE id=?", String.class, docTl))
                .isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject("SELECT status FROM edu_class_docs WHERE id=?", String.class, docVp))
                .isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject("SELECT status FROM edu_class_docs WHERE id=?", String.class, docHt))
                .isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject("SELECT status FROM edu_class_docs WHERE id=?", String.class, docRej))
                .isEqualTo("DRAFT");

        assertThatThrownBy(() -> jdbc.update("UPDATE edu_class_docs SET status='PENDING_VP' WHERE id=?", docTl))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_year_plans SET status='PENDING_VP' WHERE id=?", planPending))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                        "SELECT approvals->0->>'round' FROM edu_class_docs WHERE id=?", Integer.class, docVp))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT approvals->0->>'name' FROM edu_class_docs WHERE id=?", String.class, docVp))
                .isEqualTo("Known reviewer");
        assertThat(jdbc.queryForObject(
                        "SELECT approvals->0->>'tracking' FROM edu_class_docs WHERE id=?", String.class, docVp))
                .isEqualTo("LEGACY_BASELINE");
        assertThat(jdbc.queryForObject(
                        "SELECT approvals->0->>'round' FROM edu_class_docs WHERE id=?", Integer.class, docHt))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT sign_chain::text FROM edu_class_docs WHERE id=?", String.class, docHt))
                .contains("TEAM_LEADER", "VICE_PRINCIPAL", "PRINCIPAL");
        // Invalid status is rejected by CHECK constraint
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_year_plans SET status='FOOBAR' WHERE id=?", planPending))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE edu_class_docs SET status='FOOBAR' WHERE id=?", docTl))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("EDU-54: nâng cấp trực tiếp từ V79 lên V81 (contract) thành công")
    void upgradesDirectlyFromLegacy79ToContract81() {
        String schema = "wf_dir_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, "79").migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword()));
        Long owner = jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES ('owner_dir','owner_dir','Tác giả Dir','ACTIVE') RETURNING id",
                Long.class);
        Long year = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-DIR','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        Long age = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-4'", Long.class);

        Long plan = jdbc.queryForObject(
                "INSERT INTO edu_year_plans(code,school_year_id,age_group_id,title,description,status,owner_id) VALUES ('WF-DIR',?,?, 'Kế hoạch Dir','','PENDING_VP',?) RETURNING id",
                Long.class,
                year,
                age,
                owner);

        flyway(schema, "82").migrate();

        assertThat(jdbc.queryForObject("SELECT status FROM edu_year_plans WHERE id=?", String.class, plan))
                .isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject(
                        "SELECT workflow_policy->'year'->'creators'->>0 FROM edu_settings WHERE id=1", String.class))
                .isEqualTo("TEAM_LEADER");
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
