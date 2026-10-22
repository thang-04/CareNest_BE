package com.carenest.service.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carenest.dto.organization.CampusRequest;
import com.carenest.dto.organization.CampusResponse;
import com.carenest.dto.organization.StaffAssignmentSummaryResponse;
import com.carenest.entity.organization.Campus;
import com.carenest.exception.GlobalException;
import com.carenest.mapper.organization.CampusMapper;
import com.carenest.mapper.organization.SchoolStructureMapper;
import com.carenest.repository.organization.AgeGroupRepository;
import com.carenest.repository.organization.CampusRepository;
import com.carenest.repository.organization.ClassroomRepository;
import com.carenest.repository.organization.RoomRepository;
import com.carenest.repository.organization.SchoolYearRepository;
import com.carenest.repository.organization.StaffAssignmentRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CampusServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final CurrentUser PRINCIPAL =
            new CurrentUser(2L, "kc-2", "principal.dev", "Demo", null, Set.of(CareNestRole.PRINCIPAL));
    private static final CurrentUser VICE_PRINCIPAL =
            new CurrentUser(3L, "kc-3", "viceprincipal.a", "Demo", null, Set.of(CareNestRole.VICE_PRINCIPAL));

    private final CampusRepository campusRepository = mock(CampusRepository.class);
    private final ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
    private final SchoolYearRepository schoolYearRepository = mock(SchoolYearRepository.class);
    private final StaffAssignmentService staffAssignmentService = mock(StaffAssignmentService.class);
    private final CampusServiceImpl service = new CampusServiceImpl(
            campusRepository,
            classroomRepository,
            schoolYearRepository,
            mock(RoomRepository.class),
            mock(StaffAssignmentRepository.class),
            mock(AgeGroupRepository.class),
            new SchoolStructureMapper(),
            staffAssignmentService,
            new CampusMapper(),
            mock(YearSnapshotReader.class),
            FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        given(staffAssignmentService.hasActivePrincipalAssignment(2L, TODAY)).willReturn(true);
        given(staffAssignmentService.findActiveAssignments(3L, TODAY))
                .willReturn(List.of(new StaffAssignmentSummaryResponse(
                        StaffRole.VICE_PRINCIPAL, 10L, null, null, TODAY.minusMonths(1), null)));
        given(campusRepository.save(any(Campus.class))).willAnswer(invocation -> {
            Campus saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
    }

    @Test
    void firstCampusBecomesMain() {
        given(campusRepository.findFirstByMainTrue()).willReturn(Optional.empty());

        CampusResponse created = service.createCampus(PRINCIPAL, request("CS-C", "Cơ sở C", false));

        assertThat(created.isMain()).isTrue();
        assertThat(created.code()).isEqualTo("CS-C");
    }

    @Test
    void phoneIsStoredNormalized() {
        given(campusRepository.findFirstByMainTrue()).willReturn(Optional.empty());

        CampusResponse created = service.createCampus(PRINCIPAL, request("CS-C", "Cơ sở C", false));

        assertThat(created.phone()).isEqualTo("0912345678");
    }

    @Test
    void newMainCampusUnsetsOldMain() {
        Campus oldMain = campus(10L, "CS-A", true);
        given(campusRepository.findFirstByMainTrue()).willReturn(Optional.of(oldMain));

        CampusResponse created = service.createCampus(PRINCIPAL, request("CS-C", "Cơ sở C", true));

        assertThat(created.isMain()).isTrue();
        assertThat(oldMain.isMain()).isFalse();
    }

    @Test
    void duplicatedCodeIsConflict() {
        given(campusRepository.existsByCodeIgnoreCase("CS-A")).willReturn(true);

        expectError(() -> service.createCampus(PRINCIPAL, request("CS-A", "Cơ sở mới", false)), ApiCode.CONFLICT);
    }

    @Test
    void vicePrincipalCannotCreate() {
        expectError(() -> service.createCampus(VICE_PRINCIPAL, request("CS-C", "Cơ sở C", false)), ApiCode.FORBIDDEN);
        verify(campusRepository, never()).save(any());
    }

    @Test
    void mainCampusCannotBeUnset() {
        given(campusRepository.findById(10L)).willReturn(Optional.of(campus(10L, "CS-A", true)));

        expectError(() -> service.updateCampus(PRINCIPAL, 10L, request("CS-A", "Cơ sở A", false)), ApiCode.CONFLICT);
    }

    @Test
    void mainCampusCannotBeDeleted() {
        given(campusRepository.findById(10L)).willReturn(Optional.of(campus(10L, "CS-A", true)));

        expectError(() -> service.deleteCampus(PRINCIPAL, 10L), ApiCode.CONFLICT);
        verify(campusRepository, never()).delete(any());
    }

    @Test
    void campusWithClassesCannotBeDeleted() {
        given(campusRepository.findById(11L)).willReturn(Optional.of(campus(11L, "CS-B", false)));
        given(classroomRepository.countByCampusId(11L)).willReturn(3L);

        expectError(() -> service.deleteCampus(PRINCIPAL, 11L), ApiCode.CONFLICT);
        verify(campusRepository, never()).delete(any());
    }

    @Test
    void vicePrincipalSeesOnlyOwnCampus() {
        given(campusRepository.findAllByIdInOrderByMainDescCodeAsc(Set.of(10L)))
                .willReturn(List.of(campus(10L, "CS-A", true)));

        List<CampusResponse> campuses = service.getCampuses(VICE_PRINCIPAL, null);

        assertThat(campuses).extracting(CampusResponse::id).containsExactly(10L);
        verify(campusRepository, never()).findAllByOrderByMainDescCodeAsc();
    }

    @Test
    void vicePrincipalCannotOpenOtherCampus() {
        given(campusRepository.findById(11L)).willReturn(Optional.of(campus(11L, "CS-B", false)));

        expectError(() -> service.getCampusDetail(VICE_PRINCIPAL, 11L, null), ApiCode.FORBIDDEN);
    }

    private static CampusRequest request(String code, String name, boolean main) {
        return new CampusRequest(code, name, name, "Địa chỉ demo", "+84 912.345-678", main);
    }

    private static Campus campus(Long id, String code, boolean main) {
        Campus campus = Campus.create(code, "Campus " + code, code, "Địa chỉ demo", null);
        campus.markMain(main);
        ReflectionTestUtils.setField(campus, "id", id);
        return campus;
    }

    private static void expectError(ThrowingCallable call, ApiCode apiCode) {
        assertThatThrownBy(call)
                .isInstanceOf(GlobalException.class)
                .extracting(ex -> ((GlobalException) ex).getApiCode())
                .isEqualTo(apiCode);
    }
}
