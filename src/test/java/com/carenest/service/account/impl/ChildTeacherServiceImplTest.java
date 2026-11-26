package com.carenest.service.account.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carenest.dto.account.ChildTeacherResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChildTeacherServiceImplTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private final GuardianChildLinkService links = mock(GuardianChildLinkService.class);
    private final EnrollmentService enrollments = mock(EnrollmentService.class);
    private final StaffAssignmentService staff = mock(StaffAssignmentService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final ChildTeacherServiceImpl service = new ChildTeacherServiceImpl(
            links,
            enrollments,
            staff,
            accounts,
            Clock.fixed(Instant.parse("2026-11-26T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));

    private static CurrentUser parent() {
        return new CurrentUser(
                9L,
                "kc-9",
                "0900000009",
                "Phụ huynh",
                null,
                Set.of(CareNestRole.PARENT),
                TestPermissions.defaultsOf(CareNestRole.PARENT));
    }

    private static UserAccount account(long id, String name, String username) {
        UserAccount account = mock(UserAccount.class);
        when(account.getId()).thenReturn(id);
        when(account.getFullName()).thenReturn(name);
        when(account.getUsername()).thenReturn(username);
        return account;
    }

    @Test
    @DisplayName("AUTH-04: phụ huynh không liên kết với trẻ ⇒ 4108")
    void otherParentForbidden() {
        when(links.isGuardianOf(9L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.getTeachers(parent(), 5L))
                .isInstanceOfSatisfying(
                        GlobalException.class,
                        ex -> assertThat(ex.getApiCode()).isEqualTo(ApiCode.PARENT_CHILD_FORBIDDEN));
    }

    @Test
    @DisplayName("PAR-04: GV đang dạy lớp của trẻ, chủ nhiệm trước; SĐT chỉ khi tên đăng nhập là SĐT")
    void teachersOfCurrentClassHomeroomFirst() {
        when(links.isGuardianOf(9L, 5L)).thenReturn(true);
        when(enrollments.findActiveClassroomIds(5L, TODAY)).thenReturn(List.of(12L));
        when(staff.findCurrentTeacherIds(List.of(12L), TODAY)).thenReturn(Map.of(12L, List.of(31L, 30L)));
        when(staff.findHomeroomTeacherIds(List.of(12L), TODAY)).thenReturn(Map.of(12L, 30L));
        UserAccount homeroom = account(30L, "Kiều Thu Nga", "0343484905");
        UserAccount other = account(31L, "Lê Hoa", "teacher1");
        when(accounts.findAllById(List.of(31L, 30L))).thenReturn(List.of(homeroom, other));

        assertThat(service.getTeachers(parent(), 5L))
                .containsExactly(
                        new ChildTeacherResponse("Kiều Thu Nga", "0343484905", true),
                        new ChildTeacherResponse("Lê Hoa", null, false));
    }

    @Test
    @DisplayName("Trẻ chưa có lớp hôm nay ⇒ danh sách rỗng")
    void noClassIsEmpty() {
        when(links.isGuardianOf(9L, 5L)).thenReturn(true);
        when(enrollments.findActiveClassroomIds(5L, TODAY)).thenReturn(List.of());

        assertThat(service.getTeachers(parent(), 5L)).isEmpty();
    }
}
