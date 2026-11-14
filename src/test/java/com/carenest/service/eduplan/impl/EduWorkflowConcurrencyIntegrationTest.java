package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.eduplan.EduWorkflowPolicyRequest;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.AccountService;
import com.carenest.service.eduplan.EduWorkflowService;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.enums.CareNestRole;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {"spring.jpa.properties.hibernate.generate_statistics=true", "carenest.storage.bucket=test-bucket"
        })
@Testcontainers(disabledWithoutDocker = true)
class EduWorkflowConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static org.testcontainers.postgresql.PostgreSQLContainer postgres =
            new org.testcontainers.postgresql.PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EduYearPlanService years;

    @Autowired
    EduWorkflowService workflow;

    @Autowired
    com.carenest.service.eduplan.EduClassDocService classDocs;

    @Autowired
    tools.jackson.databind.ObjectMapper mapper;

    @Autowired
    com.carenest.service.eduplan.LearningResourceService learningResources;

    @org.springframework.test.context.bean.override.mockito.MockitoBean(
            answers = org.mockito.Answers.CALLS_REAL_METHODS)
    software.amazon.awssdk.services.s3.S3Client s3;

    @MockitoSpyBean
    com.carenest.repository.eduplan.EduPlanGoalRepository goals;

    @Autowired
    PlatformTransactionManager transactions;

    @Autowired
    jakarta.persistence.EntityManager entityManager;

    @Autowired
    jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @MockitoSpyBean
    StaffAssignmentService assignments;

    @MockitoSpyBean
    AccountService accounts;

    @Test
    void themeApprovalExcludesConcurrentWeekMutation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        CurrentUser principal = user("week-principal" + suffix, CareNestRole.PRINCIPAL);
        CurrentUser author = user("week-author" + suffix, CareNestRole.TEACHER);
        CurrentUser leader = user("week-leader" + suffix, CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                principal.userId());
        long age = jdbc.queryForObject("SELECT min(id) FROM age_groups", Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                leader.userId(),
                age);
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date) VALUES (?,'2026-01-01','2026-12-31') RETURNING id",
                suffix);
        long campus = id("INSERT INTO campuses(code,name) VALUES (?,?) RETURNING id", suffix, suffix);
        long classroom = id(
                "INSERT INTO classrooms(campus_id,school_year_id,age_group_id,name) VALUES (?,?,?,'Synthetic') RETURNING id",
                campus,
                year,
                age);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,classroom_id,valid_from,is_homeroom) VALUES (?,'TEACHER',?,'2020-01-01',true)",
                author.userId(),
                classroom);
        long plan = draft(year, age, author.userId(), suffix);
        jdbc.update("UPDATE edu_team_plans SET status='APPROVED' WHERE id=?", plan);
        long theme = id(
                "INSERT INTO edu_themes(team_plan_id,sort_order,name,abbr,start_date,end_date,festivals,note) VALUES (?,1,'Synthetic','S','2026-09-07','2026-09-11','','') RETURNING id",
                plan);
        String rootKey = "ds_" + classroom + "_" + theme;
        String weekKey = "wp_" + classroom + "_2026-09-07";
        // Spec 003: hồ sơ chủ đề là gốc duyệt; kế hoạch chủ đề là thành viên
        jdbc.update(
                "INSERT INTO edu_class_docs(doc_key,kind,classroom_id,team_plan_id,theme_id,status,created_by,updated_by,review_round) VALUES (?,'theme',?,?,?,'REVIEW',?,?,1)",
                "tp_" + classroom + "_" + theme,
                classroom,
                plan,
                theme,
                author.userId(),
                author.userId());
        jdbc.update(
                "INSERT INTO edu_class_docs(doc_key,kind,classroom_id,team_plan_id,theme_id,status,created_by,updated_by,review_round) VALUES (?,'dossier',?,?,?,'REVIEW',?,?,1)",
                rootKey,
                classroom,
                plan,
                theme,
                author.userId(),
                author.userId());
        jdbc.update(
                "INSERT INTO edu_class_docs(doc_key,kind,classroom_id,team_plan_id,theme_id,week_start,status,created_by,updated_by,review_round) VALUES (?,'week',?,?,?,'2026-09-07','REVIEW',?,?,1)",
                weekKey,
                classroom,
                plan,
                theme,
                author.userId(),
                author.userId());
        var content = mapper.createObjectNode();
        content.put("classId", classroom);
        content.put("yearPlanId", plan);
        content.put("themeId", theme);
        content.put("weekStart", "2026-09-07");
        CountDownLatch approved = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            workflow.savePolicy(principal, originalPolicy(principal));
            Future<?> approval = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                classDocs.actOnClassDoc(
                        leader,
                        "dossier",
                        rootKey,
                        new com.carenest.dto.eduplan.ClassDocActionRequest("approve", null),
                        0L);
                approved.countDown();
                await(release);
                return null;
            }));
            assertThat(approved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> save = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout='400ms'");
                return classDocs.saveClassDoc(author, "week", weekKey, content, 0L, 0L);
            }));
            assertThatThrownBy(() -> save.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
            release.countDown();
            approval.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> classDocs.saveClassDoc(author, "week", weekKey, content, 0L, 0L))
                    .isInstanceOf(com.carenest.exception.GlobalException.class);
            assertThat(jdbc.queryForObject("SELECT status FROM edu_class_docs WHERE doc_key=?", String.class, weekKey))
                    .isEqualTo("REVIEW");
            assertThat(jdbc.queryForObject(
                            "SELECT content::text FROM edu_class_docs WHERE doc_key=?", String.class, weekKey))
                    .isEqualTo("{}");
        } finally {
            release.countDown();
            pool.shutdownNow();
            jdbc.update("DELETE FROM edu_team_plans WHERE id=?", plan);
        }
    }

    @Test
    void reconciliationBatchesCandidatesForTwentyFourDocuments() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        CurrentUser principal = user("batch-principal" + suffix, CareNestRole.PRINCIPAL);
        CurrentUser author = user("batch-author" + suffix, CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                principal.userId());
        long age = jdbc.queryForObject("SELECT min(id) FROM age_groups", Long.class);
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date) VALUES (?,'2026-01-01','2026-12-31') RETURNING id",
                suffix);
        long campus = id("INSERT INTO campuses(code,name) VALUES (?,?) RETURNING id", suffix, suffix);
        long plan = draft(year, age, author.userId(), suffix);
        for (int i = 0; i < 24; i++) {
            long classroom = id(
                    "INSERT INTO classrooms(campus_id,school_year_id,age_group_id,name) VALUES (?,?,?,?) RETURNING id",
                    campus,
                    year,
                    age,
                    "L" + i);
            jdbc.update(
                    "INSERT INTO edu_class_docs(doc_key,kind,classroom_id,team_plan_id,status,created_by,updated_by,version) VALUES (?,'branch',?,?,'REVIEW',?,?,0)",
                    suffix + i,
                    classroom,
                    plan,
                    author.userId(),
                    author.userId());
        }
        clearInvocations(assignments, accounts);
        var statistics =
                entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();
        var listing = classDocs.getClassDocs(principal, year);
        assertThat(listing.branchPlans()).hasSize(24);
        long listingStatements = statistics.getPrepareStatementCount();
        System.out.println("EDU_LIST_MEASURE documents=24 statements=" + listingStatements);
        var permissions = listing.branchPlans().getFirst().path("permissions");
        assertThat(listing.branchPlans().getFirst().path("superseded").asBoolean())
                .isFalse();
        assertThat(permissions.has("canReject")).isTrue();
        // Spec 005 FR-012: đã bỏ ô "Kế hoạch điều chỉnh và bổ sung"
        assertThat(permissions.has("canEditAdjustments")).isFalse();
        assertThat(permissions.has("canAdjust")).isFalse();
        assertThat(listing.branchPlans().getFirst().has("adjustments")).isFalse();
        assertThat(permissions.path("canReject").asBoolean())
                .isEqualTo(permissions.path("canReturn").asBoolean());
        assertThat(listingStatements).isLessThan(100);
        statistics.clear();
        clearInvocations(assignments, accounts);
        long started = System.nanoTime();
        workflow.savePolicy(principal, changedPolicy(principal));
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        verify(assignments, times(1)).findActiveEducationReviewerAssignments(any());
        verify(accounts, times(1)).findActiveUserIds(any());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_class_docs WHERE team_plan_id=? AND applied_policy_revision IS NOT NULL",
                        Long.class,
                        plan))
                .isEqualTo(24L);
        System.out.println("EDU_RECONCILE_MEASURE documents=24 assignmentBatches=1 activeAccountBatches=1 statements="
                + statistics.getPrepareStatementCount() + " elapsedMs=" + elapsed);
        assertThat(statistics.getPrepareStatementCount()).isLessThan(24 * 3L);
        jdbc.update("DELETE FROM edu_team_plans WHERE id=?", plan);
        workflow.savePolicy(principal, originalPolicy(principal));
    }

    private EduWorkflowPolicyRequest changedPolicy(CurrentUser principal) {
        return policy(principal, List.of("PRINCIPAL"));
    }

    private EduWorkflowPolicyRequest originalPolicy(CurrentUser principal) {
        return policy(principal, List.of("TEAM_LEADER", "VICE_PRINCIPAL"));
    }

    private EduWorkflowPolicyRequest policy(CurrentUser principal, List<String> classReviewers) {
        return new EduWorkflowPolicyRequest(
                workflow.getPolicy(principal).revision(),
                Map.of(
                        "year",
                                new EduWorkflowPolicyRequest.PolicyEntry(
                                        List.of("TEAM_LEADER"), List.of("VICE_PRINCIPAL")),
                        "branch", new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEACHER"), classReviewers),
                        "theme", new EduWorkflowPolicyRequest.PolicyEntry(List.of("TEACHER"), classReviewers)));
    }

    private long draft(long year, long age, long owner, String code) {
        return id(
                "INSERT INTO edu_team_plans(legacy_team_without_source, code,school_year_id,age_group_id,title,description,status,owner_id) VALUES (true, ?,?,?,'Synthetic','','DRAFT',?) RETURNING id",
                code,
                year,
                age,
                owner);
    }

    private CurrentUser user(String username, CareNestRole role) {
        long id = id(
                "INSERT INTO user_accounts(keycloak_user_id,username,full_name,status) VALUES (?,?,?,'ACTIVE') RETURNING id",
                username,
                username,
                "Synthetic");
        return new CurrentUser(id, username, username, "Synthetic", null, Set.of(role));
    }

    private long id(String sql, Object... values) {
        return jdbc.queryForObject(sql, Long.class, values);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Barrier timed out");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
