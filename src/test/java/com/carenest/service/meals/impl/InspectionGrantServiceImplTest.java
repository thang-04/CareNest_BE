package com.carenest.service.meals.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.carenest.dto.meals.InspectionApprovalDecisionRequest;
import com.carenest.dto.meals.InspectionApprovalRequest;
import com.carenest.dto.meals.InspectionApprovalResponse;
import com.carenest.dto.meals.InspectionDayResponse;
import com.carenest.entity.meals.InspectionDayGrant;
import com.carenest.exception.GlobalException;
import com.carenest.repository.meals.FoodSampleRepository;
import com.carenest.repository.meals.InspectionDayGrantRepository;
import com.carenest.repository.meals.InspectionSheetRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.CampusAccessPolicy;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.InspectionGrantStatus;
import com.carenest.utils.enums.InspectionTab;
import com.carenest.utils.enums.Permission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

// INS-05: ngày cũ chỉ ghi khi có quyền mở còn hạn của HT; kho giả giữ các dòng trong bộ nhớ
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InspectionGrantServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-11-24T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final LocalDate PAST = LocalDate.of(2026, 11, 20);
    private static final Long CAMPUS = 1L;
    private static final InspectionTab TAB = InspectionTab.SAMPLE;
    private static final CurrentUser PHT = TestPermissions.userWithDefaults(7L, CareNestRole.VICE_PRINCIPAL);
    private static final CurrentUser HT = TestPermissions.userWithDefaults(1L, CareNestRole.PRINCIPAL);

    @Mock
    private InspectionDayGrantRepository grantRepository;

    @Mock
    private InspectionSheetRepository sheetRepository;

    @Mock
    private FoodSampleRepository campusLookup;

    @Mock
    private CampusAccessPolicy campusAccessPolicy;

    private final List<InspectionDayGrant> rows = new ArrayList<>();
    private InspectionGrantServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InspectionGrantServiceImpl(
                grantRepository, sheetRepository, campusLookup, campusAccessPolicy, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(campusLookup.campusExists(CAMPUS)).thenReturn(true);
        lenient()
                .when(campusAccessPolicy.canEditCampus(any(), eq(Permission.FOOD_SAFETY_APPROVE), eq(CAMPUS)))
                .thenReturn(true);
        lenient()
                .when(grantRepository.saveAndFlush(any(InspectionDayGrant.class)))
                .thenAnswer(call -> {
                    InspectionDayGrant grant = call.getArgument(0);
                    if (grant.getId() == null) {
                        ReflectionTestUtils.setField(grant, "id", (long) rows.size() + 1);
                        rows.add(grant);
                    }
                    return grant;
                });
        lenient()
                .when(grantRepository.findByCampusIdAndSheetDateAndTabAndStatusIn(anyLong(), any(), any(), any()))
                .thenAnswer(call -> {
                    Collection<InspectionGrantStatus> statuses = call.getArgument(3);
                    return rows.stream()
                            .filter(g -> g.getCampusId().equals(call.getArgument(0))
                                    && g.getSheetDate().equals(call.getArgument(1))
                                    && g.getTab() == call.getArgument(2)
                                    && statuses.contains(g.getStatus()))
                            .findFirst();
                });
        lenient()
                .when(grantRepository.findById(anyLong()))
                .thenAnswer(call -> rows.stream()
                        .filter(g -> g.getId().equals(call.getArgument(0)))
                        .findFirst());
        lenient()
                .when(grantRepository.findCampusId(anyLong()))
                .thenAnswer(call -> rows.stream()
                        .filter(g -> g.getId().equals(call.getArgument(0)))
                        .map(InspectionDayGrant::getCampusId)
                        .findFirst());
        lenient()
                .when(grantRepository.findByCampusIdAndSheetDateOrderByIdDesc(anyLong(), any()))
                .thenAnswer(call -> rows.stream()
                        .filter(g -> g.getCampusId().equals(call.getArgument(0))
                                && g.getSheetDate().equals(call.getArgument(1)))
                        .sorted(Comparator.comparing(InspectionDayGrant::getId).reversed())
                        .toList());
        lenient()
                .when(grantRepository.findQueue(any(), any(), any()))
                .thenAnswer(call -> rows.stream()
                        .filter(g -> g.getStatus() == call.getArgument(0))
                        .toList());
    }

    private static InspectionApprovalRequest reason(String text) {
        return new InspectionApprovalRequest(text, null);
    }

    private static void assertApiCode(Throwable thrown, ApiCode expected) {
        assertThat(thrown).isInstanceOf(GlobalException.class);
        assertThat(((GlobalException) thrown).getApiCode()).isEqualTo(expected);
    }

    private InspectionApprovalResponse pending() {
        return service.request(PHT, CAMPUS, PAST, TAB, reason("Quên ghi mẫu hôm qua"));
    }

    private InspectionApprovalResponse approved() {
        InspectionApprovalResponse request = pending();
        return service.approve(HT, request.id(), null);
    }

    @Test
    @DisplayName("PHT gửi yêu cầu ngày cũ ⇒ chờ HT duyệt, chưa mở khóa")
    void phtRequestIsPendingAndDoesNotUnlock() {
        InspectionApprovalResponse response = pending();

        assertThat(response.status()).isEqualTo(InspectionGrantStatus.PENDING);
        assertThat(response.selfOpened()).isFalse();
        assertThat(response.requestedByName()).isEqualTo("Demo");
        assertThatThrownBy(() -> service.requireOpenDay(CAMPUS, PAST, TAB))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
    }

    @Test
    @DisplayName("Yêu cầu chỉ cho ngày đã qua, có quyền ghi điểm trường và có lý do")
    void requestIsValidated() {
        CurrentUser teacher = TestPermissions.userWithDefaults(8L, CareNestRole.TEACHER);
        lenient()
                .when(campusAccessPolicy.canEditCampus(teacher, Permission.FOOD_SAFETY_APPROVE, CAMPUS))
                .thenReturn(false);

        assertThatThrownBy(() -> service.request(PHT, CAMPUS, TODAY, TAB, reason("Sửa số liệu")))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_NOT_NEEDED));
        assertThatThrownBy(() -> service.request(PHT, CAMPUS, TODAY.plusDays(1), TAB, reason("Sửa số liệu")))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_DATE_FUTURE));
        assertThatThrownBy(() -> service.request(PHT, CAMPUS, PAST, TAB, reason(" a ")))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REASON_REQUIRED));
        assertThatThrownBy(() -> service.request(PHT, CAMPUS, PAST, TAB, null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REASON_REQUIRED));
        assertThatThrownBy(() -> service.request(teacher, CAMPUS, PAST, TAB, reason("Sửa số liệu")))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_ACCESS_DENIED));
        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("Một mẫu-ngày-điểm trường chỉ có một yêu cầu đang mở")
    void duplicateRequestIsRejected() {
        InspectionApprovalResponse first = pending();

        assertThatThrownBy(this::pending).satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_PENDING));

        service.approve(HT, first.id(), null);
        assertThatThrownBy(this::pending).satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_ACTIVE));
        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("HT duyệt ⇒ mở đúng mẫu đó tới hạn; mẫu khác và ngày khác vẫn khóa")
    void approveOpensOnlyThatScope() {
        InspectionApprovalResponse request = pending();

        InspectionApprovalResponse response = service.approve(HT, request.id(), null);

        assertThat(response.status()).isEqualTo(InspectionGrantStatus.APPROVED);
        assertThat(response.decidedByName()).isEqualTo("Demo");
        assertThat(response.expiresAt()).isEqualTo(NOW.plus(48, ChronoUnit.HOURS));
        assertThatCode(() -> service.requireOpenDay(CAMPUS, PAST, TAB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireOpenDay(CAMPUS, PAST, InspectionTab.FRESH))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThatThrownBy(() -> service.requireOpenDay(CAMPUS, PAST.minusDays(1), TAB))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
    }

    @Test
    @DisplayName("HT chọn hạn mở; tối đa theo cấu hình")
    void approveHonoursChosenHours() {
        InspectionApprovalResponse request = pending();

        InspectionApprovalResponse response =
                service.approve(HT, request.id(), new InspectionApprovalDecisionRequest("ok", 6));

        assertThat(response.expiresAt()).isEqualTo(NOW.plus(6, ChronoUnit.HOURS));
        assertThat(response.decisionNote()).isEqualTo("ok");
    }

    @Test
    @DisplayName("Chỉ HT duyệt, từ chối, thu hồi; PHT không tự duyệt yêu cầu của mình")
    void onlyPrincipalDecides() {
        InspectionApprovalResponse request = pending();

        assertThatThrownBy(() -> service.approve(PHT, request.id(), null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_DENIED));
        assertThatThrownBy(
                        () -> service.reject(PHT, request.id(), new InspectionApprovalDecisionRequest("không", null)))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_DENIED));
        assertThatThrownBy(
                        () -> service.revoke(PHT, request.id(), new InspectionApprovalDecisionRequest("không", null)))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_DENIED));
        assertThatThrownBy(() -> service.queue(PHT, null, null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_DENIED));
        assertThat(rows.get(0).getStatus()).isEqualTo(InspectionGrantStatus.PENDING);
    }

    @Test
    @DisplayName("Từ chối cần ghi chú và không mở khóa; yêu cầu không còn chờ thì không xử lý lại")
    void rejectNeedsNoteAndDoesNotUnlock() {
        InspectionApprovalResponse request = pending();

        assertThatThrownBy(() -> service.reject(HT, request.id(), new InspectionApprovalDecisionRequest(" ", null)))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REASON_REQUIRED));
        InspectionApprovalResponse rejected =
                service.reject(HT, request.id(), new InspectionApprovalDecisionRequest("Không đủ lý do", null));

        assertThat(rejected.status()).isEqualTo(InspectionGrantStatus.REJECTED);
        assertThatThrownBy(() -> service.requireOpenDay(CAMPUS, PAST, TAB))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThatThrownBy(() -> service.approve(HT, request.id(), null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_STATE_INVALID));
        assertThatThrownBy(() -> service.approve(HT, 99L, null))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_NOT_FOUND));
    }

    @Test
    @DisplayName("Thu hồi khóa lại ngay; chỉ thu hồi được yêu cầu đã duyệt")
    void revokeLocksAgain() {
        InspectionApprovalResponse request = approved();

        InspectionApprovalResponse revoked =
                service.revoke(HT, request.id(), new InspectionApprovalDecisionRequest("Mở nhầm", null));

        assertThat(revoked.status()).isEqualTo(InspectionGrantStatus.REVOKED);
        assertThatThrownBy(() -> service.requireOpenDay(CAMPUS, PAST, TAB))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThatThrownBy(() -> service.revoke(HT, request.id(), new InspectionApprovalDecisionRequest("Lại", null)))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_STATE_INVALID));
    }

    @Test
    @DisplayName("HT tự mở: mở ngay, có lý do và người mở trong lịch sử, không cần duyệt")
    void principalSelfOpens() {
        InspectionApprovalResponse response = service.request(HT, CAMPUS, PAST, TAB, reason("Kiểm tra lại sổ mẫu"));

        assertThat(response.selfOpened()).isTrue();
        assertThat(response.status()).isEqualTo(InspectionGrantStatus.APPROVED);
        assertThat(response.reason()).isEqualTo("Kiểm tra lại sổ mẫu");
        assertThat(response.decidedByName()).isEqualTo("Demo");
        assertThatCode(() -> service.requireOpenDay(CAMPUS, PAST, TAB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.request(HT, CAMPUS, PAST, TAB, reason("   ")))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REASON_REQUIRED));
    }

    @Test
    @DisplayName("Hết hạn thì khóa lại; yêu cầu mới đóng dòng quá hạn rồi mở dòng mới")
    void expiredGrantLocksAndCanBeRequestedAgain() {
        InspectionApprovalResponse request = approved();
        Clock later = Clock.fixed(NOW.plus(49, ChronoUnit.HOURS), ZoneOffset.UTC);
        InspectionGrantServiceImpl afterExpiry = new InspectionGrantServiceImpl(
                grantRepository, sheetRepository, campusLookup, campusAccessPolicy, later);

        assertThatThrownBy(() -> afterExpiry.requireOpenDay(CAMPUS, PAST, TAB))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThat(afterExpiry.dayApprovals(PHT, CAMPUS, PAST).stream()
                        .filter(a -> a.tab() == TAB)
                        .findFirst()
                        .orElseThrow()
                        .status())
                .isEqualTo(InspectionGrantStatus.EXPIRED);

        InspectionApprovalResponse again = afterExpiry.request(PHT, CAMPUS, PAST, TAB, reason("Cần sửa thêm"));

        assertThat(again.status()).isEqualTo(InspectionGrantStatus.PENDING);
        assertThat(rows.get(0).getStatus()).isEqualTo(InspectionGrantStatus.EXPIRED);
        assertThat(rows.get(0).getId()).isEqualTo(request.id());
    }

    @Test
    @DisplayName("Ký xong đóng quyền mở: lần sửa lại sau cần yêu cầu mới")
    void consumeClosesGrant() {
        approved();

        service.consume(CAMPUS, PAST, TAB);

        assertThat(rows.get(0).getStatus()).isEqualTo(InspectionGrantStatus.CONSUMED);
        assertThat(rows.get(0).getConsumedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.requireOpenDay(CAMPUS, PAST, TAB))
                .satisfies(ex -> assertApiCode(ex, ApiCode.INSPECTION_APPROVAL_REQUIRED));
        assertThatCode(this::pending).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ngày hôm nay và ngày tương lai không bị khóa, không có gì để đóng")
    void todayIsNeverLocked() {
        assertThatCode(() -> service.requireOpenDay(CAMPUS, TODAY, TAB)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireOpenDay(CAMPUS, TODAY.plusDays(1), TAB))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.consume(CAMPUS, TODAY, TAB)).doesNotThrowAnyException();
        assertThat(service.dayApprovals(PHT, CAMPUS, TODAY))
                .allSatisfy(a -> assertThat(a.locked()).isFalse());
    }

    @Test
    @DisplayName("Xem ngày cũ: mỗi mẫu cho biết khóa, trạng thái yêu cầu, người xem gửi được hay tự mở")
    void dayShowsApprovalStatePerTab() {
        pending();
        service.request(HT, CAMPUS, PAST, InspectionTab.FRESH, reason("HT kiểm lại hàng tươi"));

        List<InspectionDayResponse.TabApproval> forPht = service.dayApprovals(PHT, CAMPUS, PAST);
        InspectionDayResponse.TabApproval sample =
                forPht.stream().filter(a -> a.tab() == TAB).findFirst().orElseThrow();
        InspectionDayResponse.TabApproval fresh = forPht.stream()
                .filter(a -> a.tab() == InspectionTab.FRESH)
                .findFirst()
                .orElseThrow();
        InspectionDayResponse.TabApproval dry = forPht.stream()
                .filter(a -> a.tab() == InspectionTab.DRY)
                .findFirst()
                .orElseThrow();

        assertThat(forPht).hasSize(InspectionTab.values().length);
        assertThat(sample.locked()).isTrue();
        assertThat(sample.status()).isEqualTo(InspectionGrantStatus.PENDING);
        assertThat(sample.canRequest()).isFalse();
        assertThat(sample.selfOpen()).isFalse();
        assertThat(fresh.locked()).isFalse();
        assertThat(fresh.status()).isEqualTo(InspectionGrantStatus.APPROVED);
        assertThat(fresh.expiresAt()).isEqualTo(NOW.plus(48, ChronoUnit.HOURS));
        assertThat(dry.locked()).isTrue();
        assertThat(dry.status()).isNull();
        assertThat(dry.canRequest()).isTrue();
        assertThat(service.dayApprovals(HT, CAMPUS, PAST).get(0).selfOpen()).isTrue();
    }

    @Test
    @DisplayName("Hàng chờ của HT: mặc định chờ duyệt, lọc theo trạng thái")
    void queueListsPendingByDefault() {
        pending();
        service.request(HT, CAMPUS, PAST, InspectionTab.FRESH, reason("HT kiểm lại hàng tươi"));

        assertThat(service.queue(HT, null, null))
                .extracting(InspectionApprovalResponse::tab)
                .containsExactly(TAB);
        assertThat(service.queue(HT, CAMPUS, InspectionGrantStatus.APPROVED))
                .extracting(InspectionApprovalResponse::tab)
                .containsExactly(InspectionTab.FRESH);
    }
}
