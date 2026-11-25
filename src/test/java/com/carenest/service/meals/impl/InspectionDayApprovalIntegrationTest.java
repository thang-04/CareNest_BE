package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.dto.meals.InspectionApprovalDecisionRequest;
import com.carenest.dto.meals.InspectionApprovalRequest;
import com.carenest.dto.meals.InspectionApprovalResponse;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.dto.meals.InspectionSaveRequest;
import com.carenest.exception.GlobalException;
import com.carenest.security.CurrentUser;
import com.carenest.service.meals.InspectionGrantService;
import com.carenest.service.meals.InspectionService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * INS-05 trên PostgreSQL thật, qua service thật: ngày cũ bị chặn khi chưa có HT duyệt, PHT gửi yêu cầu rồi HT duyệt thì ghi
 * được đúng mẫu đó, mẫu khác và điểm trường khác vẫn khóa, thu hồi khóa lại ngay, HT tự mở thì ghi được luôn.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InspectionDayApprovalIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InspectionService inspectionService;

    @Autowired
    InspectionGrantService grantService;

    @Autowired
    Clock clock;

    private long campus;
    private long otherCampus;
    private CurrentUser pht;
    private CurrentUser principal;
    private LocalDate pastDay;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());
        // Tên điểm trường phải duy nhất theo LOWER(name), nên gắn hậu tố theo từng lần chạy
        campus = id("INSERT INTO campuses (code, name) VALUES ('AP" + suffix + "', 'Điểm trường A " + suffix
                + "') RETURNING id");
        otherCampus = id("INSERT INTO campuses (code, name) VALUES ('BP" + suffix + "', 'Điểm trường B " + suffix
                + "') RETURNING id");
        long phtId = account("pht" + suffix);
        long principalId = account("ht" + suffix);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, campus_id, valid_from)"
                        + " VALUES (?, 'VICE_PRINCIPAL', ?, '2026-01-01')",
                phtId,
                campus);
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2026-01-01')",
                principalId);
        pht = user(phtId, CareNestRole.VICE_PRINCIPAL);
        principal = user(principalId, CareNestRole.PRINCIPAL);
        pastDay = LocalDate.now(clock).minusDays(3);
        // Mẫu lưu mẫu của ngày cũ có một dòng nguồn: chỉ mẫu có dòng mới gửi được yêu cầu mở
        jdbc.update(
                "INSERT INTO food_samples (campus_id, sample_date, session, sampled_at) VALUES (?, ?, 'LUNCH', now())",
                campus,
                pastDay);
    }

    private long account(String name) {
        return id("INSERT INTO user_accounts (keycloak_user_id, username, full_name) VALUES ('kc-" + name + "', '"
                + name + "', 'Người " + name + "') RETURNING id");
    }

    private static CurrentUser user(long id, CareNestRole role) {
        return new CurrentUser(
                id,
                "kc-" + id,
                "u" + id,
                "Người " + id,
                null,
                Set.of(role),
                Set.of(Permission.FOOD_SAFETY_APPROVE),
                Set.of());
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static InspectionSaveRequest save(int expectedVersion) {
        return new InspectionSaveRequest(
                expectedVersion, Map.of(), List.of(Map.of("key", "sample:1", "qualityNote", "Mẫu đạt")));
    }

    private static void assertCode(Throwable thrown, ApiCode expected) {
        assertThat(thrown).isInstanceOf(GlobalException.class);
        assertThat(((GlobalException) thrown).getApiCode()).isEqualTo(expected);
    }

    private InspectionDayResponse.TabApproval stateOf(CurrentUser viewer, LocalDate day, InspectionTab tab) {
        return inspectionService.getDay(viewer, campus, day).approvals().stream()
                .filter(a -> a.tab() == tab)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Ngày cũ chưa duyệt: lưu bị từ chối và không ghi biên bản; xem ngày cho biết khóa và được gửi yêu cầu")
    void oldDayIsLockedUntilApproved() {
        assertThatThrownBy(() -> inspectionService.save(pht, campus, pastDay, InspectionTab.SAMPLE, save(0)))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM inspection_sheets WHERE campus_id = ?", Integer.class, campus))
                .isZero();
        InspectionDayResponse.TabApproval state = stateOf(pht, pastDay, InspectionTab.SAMPLE);
        assertThat(state.locked()).isTrue();
        assertThat(state.canRequest()).isTrue();
        assertThat(state.status()).isNull();
        // Hôm nay không khóa
        assertThat(stateOf(pht, LocalDate.now(clock), InspectionTab.SAMPLE).locked())
                .isFalse();
    }

    @Test
    @DisplayName(
            "PHT gửi yêu cầu, HT duyệt: ghi được đúng mẫu đó; mẫu khác và điểm trường khác vẫn khóa; thu hồi khóa lại")
    void approvedScopeOnlyThenRevoked() {
        InspectionApprovalResponse request = grantService.request(
                pht,
                campus,
                pastDay,
                InspectionTab.SAMPLE,
                new InspectionApprovalRequest("Quên ghi mẫu hôm trước", null));
        assertThat(request.status()).isEqualTo(InspectionGrantStatus.PENDING);
        assertThatThrownBy(() -> inspectionService.save(pht, campus, pastDay, InspectionTab.SAMPLE, save(0)))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThatThrownBy(() -> grantService.approve(pht, request.id(), null))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_DENIED));

        grantService.approve(principal, request.id(), null);

        assertThat(inspectionService
                        .save(pht, campus, pastDay, InspectionTab.SAMPLE, save(0))
                        .version())
                .isEqualTo(1);
        assertThat(stateOf(pht, pastDay, InspectionTab.SAMPLE).locked()).isFalse();
        assertThat(stateOf(pht, pastDay, InspectionTab.SAMPLE).status()).isEqualTo(InspectionGrantStatus.APPROVED);
        assertThatThrownBy(() -> inspectionService.save(pht, campus, pastDay, InspectionTab.FRESH, save(0)))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThatThrownBy(
                        () -> inspectionService.save(pht, campus, pastDay.minusDays(1), InspectionTab.SAMPLE, save(0)))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThatThrownBy(() -> inspectionService.save(pht, otherCampus, pastDay, InspectionTab.SAMPLE, save(0)))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_ACCESS_DENIED));

        grantService.revoke(principal, request.id(), new InspectionApprovalDecisionRequest("Mở nhầm", null));

        assertThatThrownBy(() -> inspectionService.save(pht, campus, pastDay, InspectionTab.SAMPLE, save(1)))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThat(stateOf(pht, pastDay, InspectionTab.SAMPLE).status()).isEqualTo(InspectionGrantStatus.REVOKED);
    }

    @Test
    @DisplayName("Quá hạn thì khóa lại: hạn lùi về quá khứ trong DB làm lưu bị từ chối, trạng thái hiện EXPIRED")
    void expiredGrantLocksAgain() {
        InspectionApprovalResponse request = grantService.request(
                pht, campus, pastDay, InspectionTab.SAMPLE, new InspectionApprovalRequest("Cần bổ sung", null));
        grantService.approve(principal, request.id(), new InspectionApprovalDecisionRequest(null, 1));
        assertThatCode(() -> inspectionService.save(pht, campus, pastDay, InspectionTab.SAMPLE, save(0)))
                .doesNotThrowAnyException();

        jdbc.update(
                "UPDATE inspection_day_grants SET expires_at = now() - interval '1 minute' WHERE id = ?", request.id());

        assertThatThrownBy(() -> inspectionService.save(pht, campus, pastDay, InspectionTab.SAMPLE, save(1)))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThat(stateOf(pht, pastDay, InspectionTab.SAMPLE).status()).isEqualTo(InspectionGrantStatus.EXPIRED);
        // Nháp đã lưu giữ nguyên, PHT gửi yêu cầu mới được
        assertThat(jdbc.queryForObject(
                        "SELECT version FROM inspection_sheets WHERE campus_id = ?", Integer.class, campus))
                .isEqualTo(1);
        assertThat(grantService
                        .request(
                                pht,
                                campus,
                                pastDay,
                                InspectionTab.SAMPLE,
                                new InspectionApprovalRequest("Xin mở lại", null))
                        .status())
                .isEqualTo(InspectionGrantStatus.PENDING);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM inspection_day_grants WHERE id = ?", String.class, request.id()))
                .isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("HT tự mở: ghi được ngay, lý do và người mở nằm trong lịch sử, không cần ai duyệt")
    void principalSelfOpens() {
        InspectionApprovalResponse opened = grantService.request(
                principal, campus, pastDay, InspectionTab.SAMPLE, new InspectionApprovalRequest("HT rà lại sổ mẫu", 2));

        assertThat(opened.selfOpened()).isTrue();
        assertThat(opened.status()).isEqualTo(InspectionGrantStatus.APPROVED);
        assertThat(opened.reason()).isEqualTo("HT rà lại sổ mẫu");
        assertThat(opened.decidedByName()).isEqualTo(opened.requestedByName());
        assertThatCode(() -> inspectionService.save(principal, campus, pastDay, InspectionTab.SAMPLE, save(0)))
                .doesNotThrowAnyException();
        assertThat(grantService.queue(principal, campus, InspectionGrantStatus.APPROVED))
                .extracting(InspectionApprovalResponse::id)
                .contains(opened.id());
        assertThatThrownBy(() -> grantService.queue(pht, campus, null))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_DENIED));
    }

    @Test
    @DisplayName("Gửi hai yêu cầu cùng một phạm vi: chỉ một dòng đang mở, lần hai nhận mã đã có yêu cầu chờ duyệt")
    void duplicateRequestsKeepOneOpenRow() {
        grantService.request(
                pht, campus, pastDay, InspectionTab.SAMPLE, new InspectionApprovalRequest("Sửa sổ lưu mẫu", null));

        assertThatThrownBy(() -> grantService.request(
                        pht, campus, pastDay, InspectionTab.SAMPLE, new InspectionApprovalRequest("Gửi lần hai", null)))
                .satisfies(ex -> assertCode(ex, ApiCode.INSPECTION_APPROVAL_PENDING));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM inspection_day_grants WHERE campus_id = ? AND tab = 'SAMPLE'",
                        Integer.class,
                        campus))
                .isEqualTo(1);
    }
}
