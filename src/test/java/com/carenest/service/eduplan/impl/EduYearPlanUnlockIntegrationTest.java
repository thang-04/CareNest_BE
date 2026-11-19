package com.carenest.service.eduplan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.eduplan.YearPlanDetailResponse;
import com.carenest.dto.eduplan.YearPlanEditRequest;
import com.carenest.dto.eduplan.YearPlanRequest;
import com.carenest.dto.eduplan.YearPlanSummaryResponse;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.eduplan.EduYearPlanService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.EduPlanLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Spec 005 FR-021, FR-021a: chỉ HT mở hạn kế hoạch năm/tổ chưa ban hành đã quá hạn; danh sách trả timeLock. */
@SpringBootTest(properties = "carenest.eduplan.plan-time-lock-enabled=true")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduYearPlanUnlockIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired
    private EduYearPlanService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void principalUnlocksOnlyUnreleasedOverduePlan() {
        long campus = id("INSERT INTO campuses (code, name) VALUES ('CS-A', 'Campus A') RETURNING id");
        // Năm học đã bắt đầu (01/09) ⇒ kế hoạch năm chưa ban hành đang quá hạn
        long year = id("INSERT INTO school_years (name, start_date, end_date, status) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31', 'ACTIVE') RETURNING id");
        long ageGroup = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long domain = jdbc.queryForObject("SELECT id FROM edu_domains WHERE sort_order = 1", Long.class);
        CurrentUser vp = user("vp", CareNestRole.VICE_PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from) "
                        + "VALUES (?, 'VICE_PRINCIPAL', ?, '2026-09-01')",
                vp.userId(),
                campus);
        CurrentUser leader = user("leader", CareNestRole.TEACHER);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, '2026-09-01')",
                leader.userId(),
                ageGroup);
        CurrentUser ht = user("ht", CareNestRole.PRINCIPAL);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-09-01')",
                ht.userId());
        jdbc.update("UPDATE edu_settings SET duration_policy = '{\"theme\":{\"minWeeks\":1,\"maxWeeks\":4},"
                + "\"branch\":{\"minWeeks\":1,\"maxWeeks\":2}}'::jsonb");
        UUID session = UUID.randomUUID();
        YearPlanDetailResponse draft = service.createYearPlan(
                vp, request(year, ageGroup, domain), new YearPlanEditRequest(session, 0L, UUID.randomUUID()));

        assertThat(listed(ht, year, draft.id()).timeLock()).satisfies(lock -> {
            assertThat(lock.timeLocked()).isTrue();
            assertThat(lock.canUnlock()).isTrue();
            assertThat(lock.deadlineState()).isEqualTo(EduDeadline.OVERDUE);
        });
        assertThat(listed(vp, year, draft.id()).timeLock().canUnlock()).isFalse();
        assertCode(
                () -> service.unlockYearPlan(vp, draft.id(), "Lý do", LocalDate.of(2026, 10, 30)), ApiCode.FORBIDDEN);

        YearPlanDetailResponse unlocked = service.unlockYearPlan(ht, draft.id(), "Lý do", LocalDate.of(2026, 10, 30));
        assertThat(unlocked.timeLock().extendedTo()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(listed(ht, year, draft.id()).timeLock().extendedTo()).isEqualTo(LocalDate.of(2026, 10, 30));

        YearPlanDetailResponse sent = service.submitYearPlan(
                vp, draft.id(), null, new YearPlanEditRequest(session, unlocked.revision(), UUID.randomUUID()));
        service.issueYearPlan(
                ht, draft.id(), "Ban hành", new YearPlanEditRequest(null, sent.revision(), UUID.randomUUID()));
        // Hạn mở đã qua ⇒ bản đã ban hành khóa lại và không mở khóa nữa
        jdbc.update("UPDATE edu_plan_events SET deadline = '2026-10-21' WHERE action = 'unlock'");
        entityManager.clear();
        assertThat(listed(ht, year, draft.id()).timeLock()).satisfies(lock -> {
            assertThat(lock.timeLocked()).isTrue();
            assertThat(lock.canUnlock()).isFalse();
        });
        assertThatThrownBy(() -> service.unlockYearPlan(ht, draft.id(), "Lý do", LocalDate.of(2026, 10, 30)))
                .isInstanceOfSatisfying(GlobalException.class, ex -> {
                    assertThat(ex.getApiCode()).isEqualTo(ApiCode.INVALID_VALUE);
                    assertThat(ex.getMessage()).contains("đã ban hành");
                });
    }

    private YearPlanSummaryResponse listed(CurrentUser user, long year, Long planId) {
        return service
                .getYearPlans(user, year, null, null, Pageable.unpaged(), EduPlanLevel.SCHOOL_YEAR)
                .items()
                .stream()
                .filter(p -> p.id().equals(planId))
                .findFirst()
                .orElseThrow();
    }

    private static YearPlanRequest request(long year, long ageGroup, long domain) {
        List<YearPlanRequest.Theme> themes = IntStream.rangeClosed(1, 9)
                .mapToObj(no -> {
                    LocalDate monday = LocalDate.of(2026, 9, 7).plusWeeks(no - 1);
                    return new YearPlanRequest.Theme(
                            "t-" + no, null, "Chủ đề " + no, "CĐ" + no, monday, monday.plusDays(4), "", "");
                })
                .toList();
        return new YearPlanRequest(
                year,
                ageGroup,
                "Kế hoạch năm học",
                "",
                List.of(new YearPlanRequest.Domain(
                        domain,
                        List.of(
                                new YearPlanRequest.Row(null, "group", "Kết quả mong đợi", List.of()),
                                new YearPlanRequest.Row(
                                        null,
                                        "goal",
                                        "Trẻ thực hiện đủ động tác",
                                        List.of(new YearPlanRequest.Content(null, "Hô hấp", List.of())))))),
                themes,
                List.of(),
                EduPlanLevel.SCHOOL_YEAR,
                null,
                List.of());
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ApiCode code) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }

    private CurrentUser user(String username, CareNestRole role) {
        long userId = id(
                "INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES (?, ?, ?) RETURNING id",
                "kc-" + username,
                username,
                "Demo " + username);
        return new CurrentUser(userId, "kc-" + username, username, "Demo " + username, null, Set.of(role));
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
