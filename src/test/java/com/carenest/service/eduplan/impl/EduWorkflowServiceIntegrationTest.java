package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.eduplan.EduClassDoc;
import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.exception.GlobalException;
import com.carenest.repository.eduplan.EduClassDocRepository;
import com.carenest.repository.eduplan.EduYearPlanRepository;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.utils.ApiCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduWorkflowServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private EduWorkflowService workflowService;

    @Autowired
    private EduYearPlanRepository yearPlanRepository;

    @Autowired
    private EduClassDocRepository classDocRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("EDU-54: recordAuthor ghi và cập nhật thời gian, lấy union authors cho dossier")
    void recordAuthorAndUnionAuthors() {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        long ownerId = createAccount("wf-owner");
        long coauthorId = createAccount("wf-coauthor");
        long yearId = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-T','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        long ageId = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-3'", Long.class);

        EduYearPlan yearPlan = legacyDraft("WF-P1", yearId, ageId, "Kế hoạch năm thử", "Mô tả", ownerId, "TL", now);
        yearPlanRepository.saveAndFlush(yearPlan);

        workflowService.recordAuthor(yearPlan.getId(), null, coauthorId, "CONTENT_WRITE", now);
        Set<Long> yearAuthors = workflowService.getAuthorUserIdsForYearPlan(yearPlan.getId());
        assertThat(yearAuthors).contains(ownerId, coauthorId);

        long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name) VALUES ('C-WF','Campus WF') RETURNING id", Long.class);
        long classroom = jdbc.queryForObject(
                "INSERT INTO classrooms(name,campus_id,school_year_id,age_group_id) VALUES ('Lớp WF',?,?,?) RETURNING id",
                Long.class,
                campus,
                yearId,
                ageId);

        Long themeId = jdbc.queryForObject(
                "INSERT INTO edu_themes(year_plan_id,name,abbr,sort_order) VALUES (?,'Chủ đề 1','CD1',1) RETURNING id",
                Long.class,
                yearPlan.getId());

        EduClassDoc themeDoc =
                EduClassDoc.create("theme-doc-01", "theme", classroom, yearPlan.getId(), themeId, null, ownerId);
        classDocRepository.saveAndFlush(themeDoc);

        EduClassDoc weekDoc = EduClassDoc.create(
                "week-doc-01", "week", classroom, yearPlan.getId(), themeId, LocalDate.of(2026, 9, 7), coauthorId);
        classDocRepository.saveAndFlush(weekDoc);

        long weekAuthorId = createAccount("wf-week-author");
        workflowService.recordAuthor(null, weekDoc.getId(), weekAuthorId, "CONTENT_WRITE", now);

        Set<Long> dossierAuthors = workflowService.getUnionAuthorUserIdsForDossier(themeDoc);
        assertThat(dossierAuthors).contains(ownerId, coauthorId, weekAuthorId);

        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                weekAuthorId,
                ageId);
        jdbc.update("UPDATE edu_class_docs SET status='REVIEW' WHERE id=?", themeDoc.getId());
        entityManager.refresh(themeDoc);
        var evaluation = workflowService
                .evaluateClassDocs(java.util.List.of(themeDoc), LocalDate.now())
                .get(themeDoc.getId());
        assertThat(evaluation.waitingRole()).isEqualTo(EduWorkflowPolicy.Role.VICE_PRINCIPAL);
        long otherLeader = createAccount("wf-other-leader");
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                otherLeader,
                ageId);
        assertThat(workflowService
                        .evaluateClassDocs(java.util.List.of(themeDoc), LocalDate.now())
                        .get(themeDoc.getId())
                        .waitingRole())
                .isEqualTo(EduWorkflowPolicy.Role.TEAM_LEADER);
    }

    @Test
    @DisplayName("EDU-54: validateClassDocVersion kiểm tra stale version và sentinel -1")
    void validatesClassDocVersions() {
        long ownerId = createAccount("wf-ver-owner");
        long yearId = jdbc.queryForObject(
                "INSERT INTO school_years(name,start_date,end_date) VALUES ('2026-2027-V','2026-08-01','2027-07-31') RETURNING id",
                Long.class);
        long ageId = jdbc.queryForObject("SELECT id FROM age_groups WHERE code='ag-4'", Long.class);
        long campus = jdbc.queryForObject(
                "INSERT INTO campuses(code,name) VALUES ('C-V','Campus V') RETURNING id", Long.class);
        long classroom = jdbc.queryForObject(
                "INSERT INTO classrooms(name,campus_id,school_year_id,age_group_id) VALUES ('Lớp V',?,?,?) RETURNING id",
                Long.class,
                campus,
                yearId,
                ageId);

        EduYearPlan yearPlan =
                legacyDraft("WF-P2", yearId, ageId, "Kế hoạch năm thử V", "Mô tả", ownerId, "TL", Instant.now());
        yearPlanRepository.saveAndFlush(yearPlan);

        EduClassDoc doc = EduClassDoc.create("doc-v-01", "branch", classroom, yearPlan.getId(), null, null, ownerId);
        classDocRepository.saveAndFlush(doc);

        assertThatCode(() -> workflowService.validateClassDocVersion(doc, 0L, null))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> workflowService.validateClassDocVersion(doc, 999L, null))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.CONCURRENT_UPDATE);

        assertThatThrownBy(() -> workflowService.validateClassDocVersion(doc, -1L, null))
                .isInstanceOf(GlobalException.class)
                .hasFieldOrPropertyWithValue("apiCode", ApiCode.CONCURRENT_UPDATE);
    }

    private long createAccount(String username) {
        return jdbc.queryForObject(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES (?,?,?,?) RETURNING id",
                Long.class,
                username,
                username,
                "Người thử",
                "ACTIVE");
    }

    private static com.carenest.entity.eduplan.EduYearPlan legacyDraft(
            String code,
            Long yearId,
            Long ageId,
            String title,
            String description,
            Long ownerId,
            String ownerTitle,
            java.time.Instant now) {
        var plan = com.carenest.entity.eduplan.EduYearPlan.createDraft(
                code, yearId, ageId, title, description, ownerId, ownerTitle, now);
        plan.initializePlanSource(
                com.carenest.utils.enums.EduPlanLevel.TEAM, null, true, "{\"mode\":\"LEGACY_UNBOUNDED\"}");
        return plan;
    }
}
