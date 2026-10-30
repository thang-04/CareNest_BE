package com.carenest.repository.eduplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.eduplan.EduContentThemeTick;
import com.carenest.entity.eduplan.EduGoalContent;
import com.carenest.entity.eduplan.EduGoalRow;
import com.carenest.entity.eduplan.EduPlanGoal;
import com.carenest.entity.eduplan.EduPlanSubDomain;
import com.carenest.entity.eduplan.EduTheme;
import com.carenest.entity.eduplan.EduYearPlan;
import com.carenest.utils.enums.EduGoalRowKind;
import com.carenest.utils.enums.EduPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Migration V10/V11 (nhóm tuổi, phân công Tổ trưởng, bảng edu_*) trên PostgreSQL thật. Mỗi test rollback. Cần Docker. */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class EduYearPlanSchemaIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EduYearPlanRepository yearPlanRepository;

    @Autowired
    private EduPlanGoalRepository goalRepository;

    @Autowired
    private EduPlanSubDomainRepository subDomainRepository;

    @Autowired
    private EduGoalContentRepository goalContentRepository;

    @Autowired
    private EduThemeRepository themeRepository;

    @Autowired
    private EduContentThemeTickRepository tickRepository;

    @Autowired
    private EduDomainRepository domainRepository;

    private long schoolYear;
    private long ageGroup3;
    private long ageGroup4;
    private long owner;

    @BeforeEach
    void setUp() {
        schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31') RETURNING id");
        ageGroup3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        ageGroup4 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-4'", Long.class);
        owner = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-leader', 'leader.a', 'Demo leader') RETURNING id");
    }

    @Test
    void seedsFourAgeGroupsAndFiveStandardDomains() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM age_groups", Integer.class))
                .isEqualTo(4);
        assertThat(domainRepository.findAllByActiveTrueOrderBySortOrderAsc())
                .extracting(d -> d.getName())
                .containsExactly(
                        "Phát triển thể chất",
                        "Phát triển nhận thức",
                        "Phát triển ngôn ngữ",
                        "Phát triển tình cảm và kỹ năng xã hội",
                        "Phát triển thẩm mỹ");
    }

    @Test
    void teamLeaderAssignmentNeedsAgeGroupAndNoCampusOrClassroom() {
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, age_group_id, valid_from) "
                        + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', ?, '2026-09-01')",
                owner,
                ageGroup4);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO staff_assignments (user_id, staff_role, valid_from) "
                                + "VALUES (?, 'AGE_GROUP_TEAM_LEADER', '2026-09-01')",
                        owner))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("EDU-01: mỗi (năm học, nhóm tuổi) chỉ 1 kế hoạch")
    void rejectsSecondPlanForSameSchoolYearAndAgeGroup() {
        yearPlanRepository.saveAndFlush(draft("KHNH-20262027-MGB", ageGroup3));

        assertThat(yearPlanRepository.existsBySchoolYearIdAndAgeGroupId(schoolYear, ageGroup3))
                .isTrue();
        assertThatThrownBy(() -> yearPlanRepository.saveAndFlush(draft("KHNH-20262027-MGB2", ageGroup3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("EDU-09: chỉ dòng MT có số MT; xóa kế hoạch xóa cả cây con")
    void storesGoalTreeAndCascadesOnDelete() {
        EduYearPlan plan = yearPlanRepository.saveAndFlush(draft("KHNH-20262027-MGB", ageGroup3));
        long domain =
                domainRepository.findAllByActiveTrueOrderBySortOrderAsc().get(0).getId();

        EduPlanSubDomain sub = (EduPlanSubDomain) EduGoalRow.create(plan.getId(), EduGoalRowKind.SUB);
        sub.place(domain, (short) 0, EduGoalRowKind.SUB, "Phát triển vận động", null);
        EduPlanGoal goal = (EduPlanGoal) EduGoalRow.create(plan.getId(), EduGoalRowKind.GOAL);
        goal.place(domain, (short) 1, EduGoalRowKind.GOAL, "Trẻ thực hiện đủ động tác", (short) 1);
        subDomainRepository.saveAndFlush(sub);
        goal.attachParents(sub.getId(), null);
        goalRepository.saveAndFlush(goal);

        EduGoalContent content = EduGoalContent.create();
        content.place(goal.getId(), (short) 0, "Hô hấp: hít vào, thở ra");
        goalContentRepository.saveAndFlush(content);
        EduTheme theme = EduTheme.create(plan.getId());
        theme.update((short) 0, "Trường mầm non", "TMN", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 25), "", "");
        themeRepository.saveAndFlush(theme);
        tickRepository.saveAndFlush(EduContentThemeTick.of(content.getId(), theme.getId()));

        assertThat(jdbc.queryForObject(
                        "SELECT sub_domain_id FROM edu_plan_goals WHERE id = ?", Long.class, goal.getId()))
                .isEqualTo(sub.getId());
        assertThat(goalRepository.countGoalsByPlan(Set.of(plan.getId())))
                .singleElement()
                .satisfies(row -> assertThat(row[1]).isEqualTo(1L));

        jdbc.update("DELETE FROM edu_year_plans WHERE id = ?", plan.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_content_theme_ticks", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edu_plan_goals", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("EDU-09: dòng MT thiếu số MT bị DB từ chối")
    void rejectsGoalRowWithoutGoalNumber() {
        EduYearPlan plan = yearPlanRepository.saveAndFlush(draft("KHNH-20262027-MGB", ageGroup3));
        long domain =
                domainRepository.findAllByActiveTrueOrderBySortOrderAsc().get(0).getId();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO edu_plan_goals (year_plan_id, domain_id, sort_order, text) "
                                + "VALUES (?, ?, 0, 'MT thiếu số')",
                        plan.getId(),
                        domain))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("EDU-04: bản nháp lưu được chủ đề trùng viết tắt; kiểm khi gửi duyệt")
    void draftThemesMayShareAbbr() {
        EduYearPlan plan = yearPlanRepository.saveAndFlush(draft("KHNH-20262027-MGB", ageGroup3));
        EduTheme first = EduTheme.create(plan.getId());
        first.update((short) 0, "Trường mầm non", "TMN", null, null, "", "");
        EduTheme second = EduTheme.create(plan.getId());
        second.update((short) 1, "Thế giới mới", "tmn", null, null, "", "");

        themeRepository.saveAllAndFlush(List.of(first, second));

        assertThat(themeRepository.findAllByYearPlanIdOrderBySortOrderAsc(plan.getId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("EDU-06: tìm kiếm lọc theo nhóm tuổi được xem, trạng thái và từ khóa")
    void searchFiltersByScopeStatusAndKeyword() {
        EduYearPlan draft3 = yearPlanRepository.saveAndFlush(draft("KHNH-20262027-MGB", ageGroup3));
        EduYearPlan issued4 = draft("KHNH-20262027-MGN", ageGroup4);
        issued4.issue(owner, NOW);
        yearPlanRepository.saveAndFlush(issued4);

        Page<EduYearPlan> principalView = yearPlanRepository.search(
                schoolYear, null, Set.of(EduPlanStatus.APPROVED), Set.of(), Set.of(), null, PageRequest.of(0, 20));
        assertThat(principalView.getContent()).extracting(EduYearPlan::getId).containsExactly(issued4.getId());

        Page<EduYearPlan> leaderView = yearPlanRepository.search(
                schoolYear, null, Set.of(), Set.of(ageGroup3), Set.of(), "%mgb%", PageRequest.of(0, 20));
        assertThat(leaderView.getContent()).extracting(EduYearPlan::getId).containsExactly(draft3.getId());

        // GV dạy cả 2 nhóm tuổi chỉ thấy bản đã ban hành
        Page<EduYearPlan> teacherView = yearPlanRepository.search(
                schoolYear, null, Set.of(), Set.of(), Set.of(ageGroup3, ageGroup4), null, PageRequest.of(0, 20));
        assertThat(teacherView.getContent()).extracting(EduYearPlan::getId).containsExactly(issued4.getId());
    }

    private EduYearPlan draft(String code, long ageGroupId) {
        return EduYearPlan.createDraft(code, schoolYear, ageGroupId, "Kế hoạch " + code, "", owner, null, NOW);
    }

    @Test
    @DisplayName("EDU-10: phiên đang giữ quyền bắt buộc có trạng thái để trả về, không để CHECK lọt NULL")
    void activeSessionCannotHaveNullReturnStatus() {
        EduYearPlan plan = yearPlanRepository.saveAndFlush(draft("NULL-GUARD", ageGroup3));
        assertThatThrownBy(
                        () -> jdbc.update("UPDATE edu_year_plans SET editing_from = NULL WHERE id = ?", plan.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
