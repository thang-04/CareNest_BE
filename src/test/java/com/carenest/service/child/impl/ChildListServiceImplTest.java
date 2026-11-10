package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.child.ChildGuardianResponse;
import com.carenest.dto.child.ChildListFilter;
import com.carenest.dto.child.ChildListItemResponse;
import com.carenest.dto.common.PageResponse;
import com.carenest.dto.organization.ClassroomInfoResponse;
import com.carenest.entity.child.Child;
import com.carenest.exception.GlobalException;
import com.carenest.repository.child.ChildAllergyRepository;
import com.carenest.repository.child.ChildHealthDeclarationRepository;
import com.carenest.repository.child.ChildRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.security.authorization.StaffAccessPolicy;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.ClassroomService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.ChildStatus;
import com.carenest.utils.enums.ClassroomStatus;
import com.carenest.utils.enums.Gender;
import com.carenest.utils.enums.GuardianAccountStatus;
import com.carenest.utils.enums.GuardianRelationship;
import com.carenest.utils.enums.SchoolYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChildListServiceImplTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final CurrentUser VICE = TestPermissions.userWithDefaults(3L, CareNestRole.VICE_PRINCIPAL);
    private static final CurrentUser TEACHER = TestPermissions.userWithDefaults(7L, CareNestRole.TEACHER);

    private final ChildRepository childRepository = mock(ChildRepository.class);
    private final ChildAllergyRepository allergyRepository = mock(ChildAllergyRepository.class);
    private final ChildHealthDeclarationRepository declarationRepository = mock(ChildHealthDeclarationRepository.class);
    private final GuardianViews guardianViews = mock(GuardianViews.class);
    private final GuardianChildLinkService linkService = mock(GuardianChildLinkService.class);
    private final ClassroomService classroomService = mock(ClassroomService.class);
    private final StaffAccessPolicy policy = mock(StaffAccessPolicy.class);
    private final StaffAssignmentService assignmentService = mock(StaffAssignmentService.class);
    private final ChildListServiceImpl service = new ChildListServiceImpl(
            childRepository,
            allergyRepository,
            declarationRepository,
            guardianViews,
            linkService,
            classroomService,
            policy,
            assignmentService,
            CLOCK);

    @BeforeEach
    void setUp() {
        given(policy.managedCampusIds(VICE)).willReturn(Set.of(10L));
        given(assignmentService.findCurrentTeacherClassroomIds(7L, TODAY)).willReturn(Set.of(11L));
    }

    @Test
    @DisplayName("D-4: mặc định bỏ trẻ đã nghỉ; PHT lọc theo điểm trường mình; từ khóa họ tên hoặc mã")
    void defaultsAndScopeReachQuery() {
        given(childRepository.findListRows(
                        any(),
                        any(),
                        anyCollection(),
                        anyBoolean(),
                        anyCollection(),
                        anyCollection(),
                        any(),
                        any(),
                        any()))
                .willReturn(List.of());

        service.searchChildren(VICE, new ChildListFilter(null, null, null, null, " HS26 ", 0, 20));

        verify(childRepository)
                .findListRows(
                        isNull(),
                        eq(TODAY),
                        eq(List.of("ACTIVE", "PENDING_PLACEMENT")),
                        eq(false),
                        eq(Set.of(10L)),
                        eq(List.of(-1L)),
                        isNull(),
                        isNull(),
                        eq("%hs26%"));
    }

    @Test
    @DisplayName("AUTH-08: PHT lọc điểm trường / lớp ngoài phạm vi ⇒ 403; GV lọc lớp không dạy ⇒ 403")
    void filtersOutsideScopeAreDenied() {
        given(classroomService.findCampusId(21L)).willReturn(Optional.of(20L));
        expectCode(VICE, new ChildListFilter(null, 20L, null, null, null, 0, 20), ApiCode.CAMPUS_ACCESS_DENIED);
        expectCode(VICE, new ChildListFilter(null, null, 21L, null, null, 0, 20), ApiCode.CLASS_ACCESS_DENIED);
        expectCode(TEACHER, new ChildListFilter(null, null, 21L, null, null, 0, 20), ApiCode.CLASS_ACCESS_DENIED);
        verify(childRepository, never())
                .findListRows(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName(
            "D-4: trang 2 cỡ 1; dòng có lớp hiển thị, CCCD/email phụ huynh bị bỏ, canManage theo điểm trường, giữ trường cũ")
    void pagesAndMapsRows() {
        ChildRepository.ChildListRow first = row(101L, 11L);
        ChildRepository.ChildListRow second = row(102L, null);
        given(childRepository.findListRows(
                        any(),
                        any(),
                        anyCollection(),
                        anyBoolean(),
                        anyCollection(),
                        anyCollection(),
                        any(),
                        any(),
                        any()))
                .willReturn(List.of(first, second));
        Child pending = child(102L, ChildStatus.PENDING_PLACEMENT, 10L);
        given(childRepository.findAllById(List.of(102L))).willReturn(List.of(pending));
        given(guardianViews.of(List.of(102L)))
                .willReturn(Map.of(
                        102L,
                        List.of(new ChildGuardianResponse(
                                "Mẹ Bình",
                                GuardianRelationship.MOTHER,
                                "0912345678",
                                "001234567890",
                                "me@example.test",
                                GuardianAccountStatus.ACTIVE,
                                null,
                                null,
                                null,
                                5L,
                                true))));
        given(declarationRepository.findDeclaredChildIds(List.of(102L))).willReturn(List.of(102L));
        given(linkService.countGuardiansByChildIds(List.of(102L))).willReturn(Map.of(102L, 1L));

        PageResponse<ChildListItemResponse> page =
                service.searchChildren(VICE, new ChildListFilter(null, null, null, null, null, 1, 1));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(102L);
            assertThat(item.classId()).isNull();
            assertThat(item.campusId()).isEqualTo(10L);
            assertThat(item.hasDeclaration()).isTrue();
            assertThat(item.canManage()).isTrue();
            assertThat(item.guardianCount()).isEqualTo(1L);
            assertThat(item.guardians()).singleElement().satisfies(g -> {
                assertThat(g.idNumber()).isNull();
                assertThat(g.email()).isNull();
                assertThat(g.phone()).isEqualTo("0912345678");
            });
        });
    }

    @Test
    @DisplayName(
            "AUTH-19: GV không quản lý tài khoản phụ huynh ⇒ canManage false; lớp, nhóm tuổi, năm học theo lớp hiển thị")
    void teacherRowsAreReadOnly() {
        given(childRepository.findListRows(
                        any(),
                        any(),
                        anyCollection(),
                        anyBoolean(),
                        anyCollection(),
                        anyCollection(),
                        any(),
                        any(),
                        any()))
                .willReturn(List.of(row(101L, 11L)));
        given(childRepository.findAllById(List.of(101L))).willReturn(List.of(child(101L, ChildStatus.ACTIVE, null)));
        given(classroomService.findClassroomInfo(11L))
                .willReturn(Optional.of(new ClassroomInfoResponse(
                        11L,
                        "Lớp Chồi 1",
                        10L,
                        5L,
                        SchoolYearStatus.ACTIVE,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2027, 5, 31),
                        3L,
                        List.of(),
                        25,
                        ClassroomStatus.ACTIVE)));

        PageResponse<ChildListItemResponse> page =
                service.searchChildren(TEACHER, new ChildListFilter(null, null, 11L, null, null, 0, 20));

        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.canManage()).isFalse();
            assertThat(item.classId()).isEqualTo(11L);
            assertThat(item.classroomId()).isEqualTo(11L);
            assertThat(item.className()).isEqualTo("Lớp Chồi 1");
            assertThat(item.ageGroupId()).isEqualTo(3L);
            assertThat(item.schoolYearId()).isEqualTo(5L);
            assertThat(item.campusId()).isEqualTo(10L);
        });
    }

    @Test
    @DisplayName("AUTH-04: phụ huynh (có CHILD_PROFILE_VIEW) không dùng được danh sách toàn trường ⇒ 403")
    void parentIsForbidden() {
        CurrentUser parent = TestPermissions.userWithDefaults(40L, CareNestRole.PARENT);

        expectCode(parent, new ChildListFilter(null, null, null, null, null, 0, 20), ApiCode.FORBIDDEN);
        verify(childRepository, never())
                .findListRows(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any());
    }

    private void expectCode(CurrentUser actor, ChildListFilter filter, ApiCode code) {
        assertThatThrownBy(() -> service.searchChildren(actor, filter))
                .isInstanceOfSatisfying(
                        GlobalException.class, ex -> assertThat(ex.getApiCode()).isEqualTo(code));
    }

    private static ChildRepository.ChildListRow row(Long childId, Long classroomId) {
        return new ChildRepository.ChildListRow() {
            @Override
            public Long getChildId() {
                return childId;
            }

            @Override
            public Long getClassroomId() {
                return classroomId;
            }
        };
    }

    private static Child child(Long id, ChildStatus status, Long intakeCampusId) {
        Child child = Child.enroll(
                "HS26" + id,
                "Trẻ " + id,
                LocalDate.of(2022, 3, 1),
                Gender.FEMALE,
                status != ChildStatus.PENDING_PLACEMENT,
                intakeCampusId,
                LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(child, "id", id);
        return child;
    }
}
