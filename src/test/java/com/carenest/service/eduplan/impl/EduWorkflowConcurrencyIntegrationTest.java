package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.carenest.dto.eduplan.EduWorkflowPolicyRequest;
import com.carenest.dto.eduplan.YearPlanEditRequest;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.carenest.integration.storage.StorageService storage;

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
    void sharedWritersProgressAndExclusivePolicyWaitsThenSnapshotStaysStable() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        CurrentUser author = user("author" + suffix, CareNestRole.TEACHER);
        CurrentUser principal = user("principal" + suffix, CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                principal.userId());
        var ages = jdbc.queryForList("SELECT id FROM age_groups ORDER BY id LIMIT 2", Long.class);
        for (long age : ages)
            jdbc.update(
                    "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                    author.userId(),
                    age);
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date) VALUES (?,'2026-01-01','2026-12-31') RETURNING id",
                suffix);
        long first = draft(year, ages.get(0), author.userId(), "A" + suffix);
        long second = draft(year, ages.get(1), author.userId(), "B" + suffix);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> writer = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                years.startEditing(author, first, new YearPlanEditRequest(UUID.randomUUID(), 0L, UUID.randomUUID()));
                held.countDown();
                await(release);
                return null;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> independent = pool.submit(() -> years.startEditing(
                    author, second, new YearPlanEditRequest(UUID.randomUUID(), 0L, UUID.randomUUID())));
            independent.get(10, TimeUnit.SECONDS);
            var request = changedPolicy(principal);
            Future<?> exclusive = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout='400ms'");
                return workflow.savePolicy(principal, request);
            }));
            assertThatThrownBy(() -> exclusive.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
            release.countDown();
            writer.get(10, TimeUnit.SECONDS);
            workflow.savePolicy(principal, request);
            // GET phải đọc policy/document từ cùng snapshot dù writer khác đã commit.
            CountDownLatch read = new CountDownLatch(1), committed = new CountDownLatch(1);
            TransactionTemplate snapshot = new TransactionTemplate(transactions);
            snapshot.setReadOnly(true);
            snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            Future<?> reader = pool.submit(() -> snapshot.execute(status -> {
                long before = workflow.getPolicy(principal).revision();
                Long holder =
                        jdbc.queryForObject("SELECT editing_by FROM edu_year_plans WHERE id=?", Long.class, first);
                assertThat(holder).isEqualTo(author.userId());
                read.countDown();
                await(committed);
                entityManager.clear();
                assertThat(workflow.getPolicy(principal).revision()).isEqualTo(before);
                assertThat(jdbc.queryForObject("SELECT editing_by FROM edu_year_plans WHERE id=?", Long.class, first))
                        .isEqualTo(holder);
                return null;
            }));
            assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
            var restored = originalPolicy(principal);
            var changed = new HashMap<>(restored.policies());
            changed.put(
                    "year", new EduWorkflowPolicyRequest.PolicyEntry(List.of("PRINCIPAL"), List.of("VICE_PRINCIPAL")));
            workflow.savePolicy(principal, new EduWorkflowPolicyRequest(restored.revision(), changed));
            committed.countDown();
            reader.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT editing_by FROM edu_year_plans WHERE id=?", Long.class, first))
                    .isNull();
            workflow.savePolicy(principal, originalPolicy(principal));
        } finally {
            release.countDown();
            pool.shutdownNow();
            jdbc.update("DELETE FROM edu_year_plans WHERE id IN (?,?)", first, second);
        }
    }

    @Test
    void slowUploadDoesNotBlockPolicyChangeAndCannotCommitAfterRightsRevoked() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        CurrentUser principal = user("media-principal" + suffix, CareNestRole.PRINCIPAL);
        CurrentUser author = user("media-author" + suffix, CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                principal.userId());
        long age = jdbc.queryForObject("SELECT min(id) FROM age_groups", Long.class);
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
                "INSERT INTO staff_assignments(user_id,staff_role,classroom_id,valid_from) VALUES (?,'TEACHER',?,'2020-01-01')",
                author.userId(),
                classroom);
        long plan = draft(year, age, author.userId(), suffix);
        jdbc.update("UPDATE edu_year_plans SET status='APPROVED' WHERE id=?", plan);
        long theme = id(
                "INSERT INTO edu_themes(year_plan_id,sort_order,name,abbr,festivals,note) VALUES (?,1,'Synthetic','S','','') RETURNING id",
                plan);
        String key = "tp_" + classroom + "_" + theme;
        jdbc.update(
                "INSERT INTO edu_class_docs(doc_key,kind,classroom_id,year_plan_id,theme_id,status,created_by,updated_by,content) VALUES (?,'theme',?,?,?,'DRAFT',?,?,'{\"rows\":{\"123\":{\"activities\":\"HĐ học: Vận động mới\"}}}'::jsonb)",
                key,
                classroom,
                plan,
                theme,
                author.userId(),
                author.userId());
        doReturn(true).when(goals).belongsToTheme(123L, plan, theme);
        CountDownLatch storing = new CountDownLatch(1), release = new CountDownLatch(1);
        when(storage.store(any(), anyLong())).thenAnswer(invocation -> {
            storing.countDown();
            await(release);
            return "synthetic-key";
        });
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "demo.pdf", "application/pdf", "%PDF-1.7\ndemo".getBytes());
        var binding = new com.carenest.dto.eduplan.LearningResourceBinding(
                "123#0", com.carenest.utils.LearningResourcePolicy.signature("HĐ học: Vận động mới"), "Mới");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> upload =
                    pool.submit(() -> learningResources.upload(author, key, file, binding, UUID.randomUUID()));
            assertThat(storing.await(10, TimeUnit.SECONDS)).isTrue();
            var original = originalPolicy(principal);
            var entries = new HashMap<>(original.policies());
            entries.put(
                    "theme", new EduWorkflowPolicyRequest.PolicyEntry(List.of("PRINCIPAL"), List.of("TEAM_LEADER")));
            Future<?> policy = pool.submit(
                    () -> workflow.savePolicy(principal, new EduWorkflowPolicyRequest(original.revision(), entries)));
            policy.get(5, TimeUnit.SECONDS);
            release.countDown();
            assertThatThrownBy(() -> upload.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(com.carenest.exception.GlobalException.class);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM edu_learning_resources WHERE class_doc_id=(SELECT id FROM edu_class_docs WHERE doc_key=?)",
                            Long.class,
                            key))
                    .isZero();
            verify(storage).remove("synthetic-key");

            workflow.savePolicy(principal, originalPolicy(principal));
            reset(storage);
            CountDownLatch bothStored = new CountDownLatch(2), finishStorage = new CountDownLatch(1);
            var counter = new java.util.concurrent.atomic.AtomicInteger();
            when(storage.store(any(), anyLong())).thenAnswer(invocation -> {
                String storedKey = "synthetic-" + counter.incrementAndGet();
                bothStored.countDown();
                await(finishStorage);
                return storedKey;
            });
            UUID requestId = UUID.randomUUID();
            var firstUpload = pool.submit(() -> learningResources.upload(author, key, file, binding, requestId));
            var secondUpload = pool.submit(() -> learningResources.upload(author, key, file, binding, requestId));
            assertThat(bothStored.await(10, TimeUnit.SECONDS)).isTrue();
            finishStorage.countDown();
            assertThat(firstUpload.get(10, TimeUnit.SECONDS).id())
                    .isEqualTo(secondUpload.get(10, TimeUnit.SECONDS).id());
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM edu_learning_resources WHERE class_doc_id=(SELECT id FROM edu_class_docs WHERE doc_key=?)",
                            Long.class,
                            key))
                    .isEqualTo(1);
            verify(storage, times(1)).remove(anyString());
        } finally {
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
            jdbc.update(
                    "DELETE FROM edu_learning_resources WHERE class_doc_id=(SELECT id FROM edu_class_docs WHERE doc_key=?)",
                    key);
            jdbc.update("DELETE FROM edu_year_plans WHERE id=?", plan);
            workflow.savePolicy(principal, originalPolicy(principal));
        }
    }

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
                "INSERT INTO staff_assignments(user_id,staff_role,classroom_id,valid_from) VALUES (?,'TEACHER',?,'2020-01-01')",
                author.userId(),
                classroom);
        long plan = draft(year, age, author.userId(), suffix);
        jdbc.update("UPDATE edu_year_plans SET status='APPROVED' WHERE id=?", plan);
        long theme = id(
                "INSERT INTO edu_themes(year_plan_id,sort_order,name,abbr,start_date,end_date,festivals,note) VALUES (?,1,'Synthetic','S','2026-09-07','2026-09-11','','') RETURNING id",
                plan);
        String rootKey = "tp_" + classroom + "_" + theme;
        String weekKey = "wp_" + classroom + "_2026-09-07";
        jdbc.update(
                "INSERT INTO edu_class_docs(doc_key,kind,classroom_id,year_plan_id,theme_id,status,created_by,updated_by,review_round) VALUES (?,'theme',?,?,?,'REVIEW',?,?,1)",
                rootKey,
                classroom,
                plan,
                theme,
                author.userId(),
                author.userId());
        jdbc.update(
                "INSERT INTO edu_class_docs(doc_key,kind,classroom_id,year_plan_id,theme_id,week_start,status,created_by,updated_by,review_round) VALUES (?,'week',?,?,?,'2026-09-07','REVIEW',?,?,1)",
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
                        "theme",
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
            jdbc.update("DELETE FROM edu_year_plans WHERE id=?", plan);
        }
    }

    @Test
    void policySaveExcludesConcurrentExpiredLeaseCleanup() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        CurrentUser principal = user("cleanup-principal" + suffix, CareNestRole.PRINCIPAL);
        CurrentUser author = user("cleanup-author" + suffix, CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                principal.userId());
        long age = jdbc.queryForObject("SELECT min(id) FROM age_groups", Long.class);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                author.userId(),
                age);
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date) VALUES (?,'2026-01-01','2026-12-31') RETURNING id",
                suffix);
        long plan = draft(year, age, author.userId(), suffix);
        jdbc.update(
                "UPDATE edu_year_plans SET editing_by=?, edit_session_id=?, editing_since='2020-01-01', editing_at='2020-01-01', editing_from='DRAFT' WHERE id=?",
                author.userId(),
                UUID.randomUUID(),
                plan);
        CountDownLatch saved = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var request = changedPolicy(principal);
            Future<?> policy = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                workflow.savePolicy(principal, request);
                saved.countDown();
                await(release);
                return null;
            }));
            assertThat(saved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> cleanup = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout='400ms'");
                return years.releaseExpiredEditLocks();
            }));
            assertThatThrownBy(() -> cleanup.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
            release.countDown();
            policy.get(10, TimeUnit.SECONDS);
            assertThat(years.releaseExpiredEditLocks()).isGreaterThanOrEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT editing_by IS NULL FROM edu_year_plans WHERE id=?", Boolean.class, plan))
                    .isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
            jdbc.update("DELETE FROM edu_year_plans WHERE id=?", plan);
            workflow.savePolicy(principal, originalPolicy(principal));
        }
    }

    @Test
    void draftLeaseReconciliationBatchesHolderLookups() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        CurrentUser principal = user("leases-principal" + suffix, CareNestRole.PRINCIPAL);
        CurrentUser author = user("leases-author" + suffix, CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments(user_id,staff_role,valid_from) VALUES (?,'PRINCIPAL','2020-01-01')",
                principal.userId());
        var ages = jdbc.queryForList("SELECT id FROM age_groups ORDER BY id LIMIT 2", Long.class);
        for (long age : ages) {
            jdbc.update(
                    "INSERT INTO staff_assignments(user_id,staff_role,age_group_id,valid_from) VALUES (?,'AGE_GROUP_TEAM_LEADER',?,'2020-01-01')",
                    author.userId(),
                    age);
        }
        long year = id(
                "INSERT INTO school_years(name,start_date,end_date) VALUES (?,'2026-01-01','2026-12-31') RETURNING id",
                suffix);
        long first = draft(year, ages.get(0), author.userId(), "A" + suffix);
        long second = draft(year, ages.get(1), author.userId(), "B" + suffix);
        for (long plan : List.of(first, second)) {
            jdbc.update(
                    "UPDATE edu_year_plans SET editing_by=?, edit_session_id=?, editing_since=now(), editing_at=now(), editing_from='DRAFT' WHERE id=?",
                    author.userId(),
                    UUID.randomUUID(),
                    plan);
        }
        try {
            var request = changedPolicy(principal);
            clearInvocations(assignments, accounts);
            workflow.savePolicy(principal, request);
            verify(assignments, times(1)).findActiveAssignmentsByUserIds(any(), any());
            verify(assignments, never()).findActiveAssignments(eq(author.userId()), any());
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM edu_year_plans WHERE id IN (?,?) AND editing_by=?",
                            Long.class,
                            first,
                            second,
                            author.userId()))
                    .isEqualTo(2);
        } finally {
            jdbc.update("DELETE FROM edu_year_plans WHERE id IN (?,?)", first, second);
            workflow.savePolicy(principal, originalPolicy(principal));
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
                    "INSERT INTO edu_class_docs(doc_key,kind,classroom_id,year_plan_id,status,created_by,updated_by,version) VALUES (?,'branch',?,?,'REVIEW',?,?,0)",
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
        assertThat(permissions.has("canEditAdjustments")).isTrue();
        assertThat(permissions.path("canReject").asBoolean())
                .isEqualTo(permissions.path("canReturn").asBoolean());
        assertThat(permissions.path("canEditAdjustments").asBoolean())
                .isEqualTo(permissions.path("canAdjust").asBoolean());
        assertThat(listingStatements).isLessThan(100);
        statistics.clear();
        clearInvocations(assignments, accounts);
        long started = System.nanoTime();
        workflow.savePolicy(principal, changedPolicy(principal));
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        verify(assignments, times(1)).findActiveEducationReviewerAssignments(any());
        verify(accounts, times(1)).findActiveUserIds(any());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM edu_class_docs WHERE year_plan_id=? AND applied_policy_revision IS NOT NULL",
                        Long.class,
                        plan))
                .isEqualTo(24L);
        System.out.println("EDU_RECONCILE_MEASURE documents=24 assignmentBatches=1 activeAccountBatches=1 statements="
                + statistics.getPrepareStatementCount() + " elapsedMs=" + elapsed);
        assertThat(statistics.getPrepareStatementCount()).isLessThan(24 * 3L);
        jdbc.update("DELETE FROM edu_year_plans WHERE id=?", plan);
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
                "INSERT INTO edu_year_plans(legacy_team_without_source, code,school_year_id,age_group_id,title,description,status,owner_id) VALUES (true, ?,?,?,'Synthetic','','DRAFT',?) RETURNING id",
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
