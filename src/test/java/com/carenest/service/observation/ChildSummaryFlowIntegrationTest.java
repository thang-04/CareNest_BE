package com.carenest.service.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.observation.ChildSummaryResponse;
import com.carenest.dto.observation.CreateSummaryDraftRequest;
import com.carenest.dto.observation.ObservationCriteriaResponse;
import com.carenest.dto.observation.RecordObservationsRequest;
import com.carenest.dto.observation.UpdateSummaryContentRequest;
import com.carenest.exception.GlobalException;
import com.carenest.integration.ai.AiClient;
import com.carenest.integration.ai.AiClientException;
import com.carenest.integration.ai.dto.SummaryDraftContent;
import com.carenest.integration.ai.dto.SummaryDraftContent.EvidencedPoint;
import com.carenest.integration.ai.dto.SummaryDraftInput;
import com.carenest.integration.ai.dto.SummaryDraftResult;
import com.carenest.security.CurrentUser;
import com.carenest.support.TestDatabaseImage;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.AiErrorReason;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.SummaryPeriodType;
import com.carenest.utils.enums.SummarySource;
import com.carenest.utils.enums.SummaryStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Luồng nhận xét trên PostgreSQL thật: GV nhập quan sát → tạo nháp (AI giả lập hoặc AI lỗi ⇒ template) → sửa →
 * duyệt. Embedding tắt (mặc định) nên RAG trả rỗng mà luồng vẫn chạy. Mỗi test rollback. Cần Docker.
 */
@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildSummaryFlowIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(TestDatabaseImage.POSTGRES);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @MockitoBean
    private AiClient aiClient;

    @Autowired
    private ObservationService observationService;

    @Autowired
    private ChildSummaryService childSummaryService;

    @Autowired
    private JdbcTemplate jdbc;

    private CurrentUser teacher;
    private long classroomId;
    private long childId;

    @BeforeEach
    void setUp() {
        long campus = insertId("INSERT INTO campuses (code, name) VALUES ('CS-T', 'Campus T') RETURNING id");
        long schoolYear = insertId("INSERT INTO school_years (name, start_date, end_date) "
                + "VALUES ('2026-2027', '2026-09-01', '2027-05-31') RETURNING id");
        classroomId = insertId(
                "INSERT INTO classrooms (campus_id, school_year_id, name) VALUES (?, ?, 'T1') RETURNING id",
                campus,
                schoolYear);
        long teacherId = insertId("INSERT INTO user_accounts (keycloak_user_id, username, full_name) "
                + "VALUES ('kc-teacher-t', 'teacher.t', 'GV T') RETURNING id");
        jdbc.update(
                "INSERT INTO staff_assignments (user_id, staff_role, classroom_id, valid_from) "
                        + "VALUES (?, 'TEACHER', ?, '2026-09-01')",
                teacherId,
                classroomId);
        teacher = new CurrentUser(teacherId, "kc-teacher-t", "teacher.t", "GV T", null, Set.of(CareNestRole.TEACHER));

        childId = insertId(
                "INSERT INTO children (full_name, date_of_birth) VALUES ('Nguyễn Văn An', '2022-03-15') RETURNING id");
        long classmateId = insertId(
                "INSERT INTO children (full_name, date_of_birth) VALUES ('Trần Thị Bình', '2022-05-01') RETURNING id");
        for (long id : List.of(childId, classmateId)) {
            jdbc.update(
                    "INSERT INTO enrollments (child_id, classroom_id, valid_from) VALUES (?, ?, '2026-09-01')",
                    id,
                    classroomId);
        }
        long criteria = insertId("INSERT INTO observation_criteria (code, name, sort_order) "
                + "VALUES ('PARTICIPATION_T', 'Tham gia hoạt động', 1) RETURNING id");
        jdbc.update(
                "INSERT INTO criteria_options (criteria_id, code, label, score, sort_order) "
                        + "VALUES (?, 'GOOD', 'Tốt', 3, 1), (?, 'OK', 'Đạt', 2, 2), (?, 'TRY', 'Cần cố gắng', 1, 3)",
                criteria,
                criteria,
                criteria);
    }

    @Test
    void aiDraftIsStoredAsDraftWithPseudonymizedInputThenEditedAndApproved() {
        recordWeek("Bé An chơi cùng Bình rất vui, mẹ gọi 0912 345 678");
        given(aiClient.draftChildSummary(any())).willAnswer(invocation -> {
            SummaryDraftInput input = invocation.getArgument(0);
            String noteRef = input.notes().getFirst().ref();
            return new SummaryDraftResult(
                    new SummaryDraftContent(
                            "Trẻ tham gia hoạt động tích cực.",
                            List.of(new EvidencedPoint("Hòa đồng với bạn", List.of(noteRef))),
                            List.of(),
                            List.of()),
                    "groq",
                    "test-model",
                    "summary/draft_v1");
        });

        ChildSummaryResponse draft = childSummaryService.createDraft(teacher, childId, weekRequest());

        ArgumentCaptor<SummaryDraftInput> sent = ArgumentCaptor.forClass(SummaryDraftInput.class);
        verify(aiClient).draftChildSummary(sent.capture());
        String sentNotes = sent.getValue().notes().toString();
        assertThat(sentNotes).doesNotContain("An ", "Bình", "0912").contains("Trẻ", "bạn", "[đã ẩn]");
        assertThat(sent.getValue().childRef()).isEqualTo("TRE-A");
        assertThat(sent.getValue().observedDays()).isEqualTo(5);

        assertThat(draft.source()).isEqualTo(SummarySource.AI);
        assertThat(draft.status()).isEqualTo(SummaryStatus.DRAFT);
        assertThat(draft.aiDraft().strengths()).hasSize(1);
        assertThat(draft.content()).contains("Trẻ tham gia hoạt động tích cực.", "Hòa đồng với bạn");
        assertThat(draft.aiErrorCode()).isNull();

        ChildSummaryResponse edited = childSummaryService.updateContent(
                teacher, draft.id(), new UpdateSummaryContentRequest("Bản giáo viên đã sửa"));
        assertThat(edited.content()).isEqualTo("Bản giáo viên đã sửa");
        // Bản AI gốc vẫn giữ nguyên để đối chiếu
        assertThat(edited.aiDraft().summary()).isEqualTo("Trẻ tham gia hoạt động tích cực.");

        ChildSummaryResponse approved = childSummaryService.approve(teacher, draft.id());
        assertThat(approved.status()).isEqualTo(SummaryStatus.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo(teacher.userId());
        assertThatThrownBy(() -> childSummaryService.updateContent(
                        teacher, draft.id(), new UpdateSummaryContentRequest("Sửa sau khi duyệt")))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.CONFLICT);
    }

    @Test
    void fallsBackToTemplateWhenAiUnavailableAndReusesExistingDraft() {
        recordWeek(null);
        given(aiClient.draftChildSummary(any())).willThrow(new AiClientException(AiErrorReason.TIMEOUT, "slow", null));

        ChildSummaryResponse draft = childSummaryService.createDraft(teacher, childId, weekRequest());

        assertThat(draft.source()).isEqualTo(SummarySource.TEMPLATE);
        assertThat(draft.aiErrorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(draft.aiDraft()).isNull();
        assertThat(draft.content()).contains("5 ngày quan sát", "Tham gia hoạt động: Tốt 5 ngày");

        ChildSummaryResponse again = childSummaryService.createDraft(teacher, childId, weekRequest());
        assertThat(again.id()).isEqualTo(draft.id());
    }

    @Test
    void rejectsDraftWithoutObservations() {
        assertThatThrownBy(() -> childSummaryService.createDraft(teacher, childId, weekRequest()))
                .isInstanceOf(GlobalException.class)
                .hasMessageContaining("AI_CONTEXT_INSUFFICIENT");
        verify(aiClient, never()).draftChildSummary(any());
    }

    @Test
    void rejectsObservationForChildNotEnrolledInClass() {
        long outsider = insertId(
                "INSERT INTO children (full_name, date_of_birth) VALUES ('Lê Văn C', '2022-01-01') RETURNING id");
        ObservationCriteriaResponse criteria =
                observationService.listCriteria(teacher).getFirst();
        RecordObservationsRequest request = new RecordObservationsRequest(
                MONDAY,
                List.of(new RecordObservationsRequest.Entry(
                        outsider,
                        List.of(new RecordObservationsRequest.Value(
                                criteria.id(), criteria.options().getFirst().id())),
                        null)));

        assertThatThrownBy(() -> observationService.recordObservations(teacher, classroomId, request))
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(ApiCode.BAD_REQUEST);
    }

    private void recordWeek(String mondayNote) {
        ObservationCriteriaResponse criteria =
                observationService.listCriteria(teacher).getFirst();
        Long goodOption = criteria.options().getFirst().id();
        for (LocalDate day = MONDAY; !day.isAfter(FRIDAY); day = day.plusDays(1)) {
            String note = day.equals(MONDAY) ? mondayNote : null;
            observationService.recordObservations(
                    teacher,
                    classroomId,
                    new RecordObservationsRequest(
                            day,
                            List.of(new RecordObservationsRequest.Entry(
                                    childId,
                                    List.of(new RecordObservationsRequest.Value(criteria.id(), goodOption)),
                                    note))));
        }
    }

    private static CreateSummaryDraftRequest weekRequest() {
        return new CreateSummaryDraftRequest(SummaryPeriodType.WEEK, MONDAY, FRIDAY);
    }

    private long insertId(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
