package com.carenest.repository.meals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.account.UserAccount;
import com.carenest.entity.meals.InspectionDayGrant;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** INS-05: migration V174, mapping và truy vấn của bảng quyền mở ghi ngày cũ trên PostgreSQL thật. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InspectionDayGrantRepositoryIntegrationTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 20);
    private static final Instant AT = Instant.parse("2026-11-24T01:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    private InspectionDayGrantRepository grants;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    private Long campusId;
    private Long userId;

    @BeforeEach
    void setUp() {
        campusId = jdbc.queryForObject(
                "INSERT INTO campuses (code, name) VALUES ('INS-IT', 'Điểm trường thử')"
                        + " ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                Long.class);
        userId = users.saveAndFlush(UserAccount.create("kc-ins-grant", "ins.grant", null, "Người thử"))
                .getId();
    }

    @AfterEach
    void cleanUp() {
        grants.deleteAll();
        users.deleteById(userId);
    }

    private InspectionDayGrant request(InspectionTab tab) {
        return InspectionDayGrant.request(campusId, DAY, tab, "Quên ghi mẫu", userId, "Người thử", AT);
    }

    @Test
    @DisplayName("Lưu và đọc lại đủ trường; duyệt có hạn, từ chối giữ ghi chú")
    void mapsEveryField() {
        InspectionDayGrant saved = grants.saveAndFlush(request(InspectionTab.SAMPLE));
        saved.approve(userId, "Hiệu trưởng", "ok", AT.plusSeconds(60), AT.plusSeconds(3600));
        grants.saveAndFlush(saved);

        InspectionDayGrant read = grants.findById(saved.getId()).orElseThrow();

        assertThat(read.getStatus()).isEqualTo(InspectionGrantStatus.APPROVED);
        assertThat(read.getTab()).isEqualTo(InspectionTab.SAMPLE);
        assertThat(read.getSheetDate()).isEqualTo(DAY);
        assertThat(read.getReason()).isEqualTo("Quên ghi mẫu");
        assertThat(read.getDecidedByName()).isEqualTo("Hiệu trưởng");
        assertThat(read.getDecisionNote()).isEqualTo("ok");
        assertThat(read.getExpiresAt()).isEqualTo(AT.plusSeconds(3600));
        assertThat(read.isSelfOpened()).isFalse();
        assertThat(grants.findCampusId(saved.getId())).contains(campusId);
    }

    @Test
    @DisplayName("Mỗi mẫu-ngày-điểm trường chỉ một dòng đang mở; đóng dòng cũ thì mở được dòng mới")
    void onlyOneOpenRowPerScope() {
        InspectionDayGrant first = grants.saveAndFlush(request(InspectionTab.SAMPLE));

        assertThatThrownBy(() -> grants.saveAndFlush(request(InspectionTab.SAMPLE)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(grants.saveAndFlush(request(InspectionTab.FRESH)).getId()).isNotNull();
        assertThat(grants.findByCampusIdAndSheetDateAndTabAndStatusIn(
                        campusId,
                        DAY,
                        InspectionTab.SAMPLE,
                        List.of(InspectionGrantStatus.PENDING, InspectionGrantStatus.APPROVED)))
                .get()
                .extracting(InspectionDayGrant::getId)
                .isEqualTo(first.getId());

        first.reject(userId, "Hiệu trưởng", "Không đủ lý do", AT.plusSeconds(60));
        grants.saveAndFlush(first);

        InspectionDayGrant second = grants.saveAndFlush(request(InspectionTab.SAMPLE));

        assertThat(second.getId()).isNotEqualTo(first.getId());
        // Mới nhất trước: mỗi mẫu lấy dòng đầu để hiện trạng thái
        List<InspectionDayGrant> history = grants.findByCampusIdAndSheetDateOrderByIdDesc(campusId, DAY);
        assertThat(history)
                .extracting(InspectionDayGrant::getTab)
                .containsExactly(InspectionTab.SAMPLE, InspectionTab.FRESH, InspectionTab.SAMPLE);
        assertThat(history.get(0).getId()).isEqualTo(second.getId());
        assertThat(history.get(2).getStatus()).isEqualTo(InspectionGrantStatus.REJECTED);
    }

    @Test
    @DisplayName("Hàng chờ lọc theo trạng thái, điểm trường tùy chọn, cũ nhất trước")
    void queueFiltersByStatusAndOptionalCampus() {
        grants.saveAndFlush(request(InspectionTab.SAMPLE));
        grants.saveAndFlush(InspectionDayGrant.request(
                campusId, DAY, InspectionTab.DRY, "Sửa kho", userId, "Người thử", AT.minusSeconds(30)));

        List<InspectionDayGrant> all = grants.findQueue(InspectionGrantStatus.PENDING, null, PageRequest.of(0, 10));
        List<InspectionDayGrant> mine =
                grants.findQueue(InspectionGrantStatus.PENDING, campusId, PageRequest.of(0, 10));
        List<InspectionDayGrant> other =
                grants.findQueue(InspectionGrantStatus.PENDING, campusId + 1000, PageRequest.of(0, 10));

        assertThat(all).extracting(InspectionDayGrant::getTab).containsExactly(InspectionTab.DRY, InspectionTab.SAMPLE);
        assertThat(mine).hasSize(2);
        assertThat(other).isEmpty();
        assertThat(grants.findQueue(InspectionGrantStatus.APPROVED, null, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("CHECK chặn đã duyệt mà không có hạn, đã quyết định mà thiếu thời điểm, tab lạ")
    void checksRejectInconsistentRows() {
        String insert = "INSERT INTO inspection_day_grants (campus_id, sheet_date, tab, status, reason, requested_by,"
                + " requested_by_name, requested_at, decided_at, expires_at) VALUES (?, ?, ?, ?, 'lý do', ?, 'Tên', now(), ?, ?)";

        assertThatThrownBy(() ->
                        jdbc.update(insert, campusId, DAY, "SAMPLE", "APPROVED", userId, Timestamp.from(AT), null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, campusId, DAY, "SAMPLE", "REJECTED", userId, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, campusId, DAY, "M4", "PENDING", userId, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
