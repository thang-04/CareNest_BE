package com.carenest.service.account.impl;

import com.carenest.dto.account.ChildTeacherResponse;
import com.carenest.entity.account.UserAccount;
import com.carenest.exception.GlobalException;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.security.CurrentUser;
import com.carenest.service.account.ChildTeacherService;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.organization.StaffAssignmentService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChildTeacherServiceImpl implements ChildTeacherService {
    private static final String LOG_PREFIX = "[ChildTeacherServiceImpl]";

    /** Nhân sự đăng nhập bằng SĐT di động 10 số; tên đăng nhập khác (tài khoản thử) thì không có SĐT để gọi. */
    private static final Pattern PHONE = Pattern.compile("0[0-9]{9}");

    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final StaffAssignmentService staffAssignmentService;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ChildTeacherResponse> getTeachers(CurrentUser currentUser, Long childId) {
        String logPrefix = LOG_PREFIX + "|getTeachers|userId=" + currentUser.userId() + "|childId=" + childId;
        log.info("{}|START", logPrefix);
        try {
            // AUTH-04: chỉ phụ huynh đã liên kết với trẻ
            if (!currentUser.hasRole(CareNestRole.PARENT)
                    || !guardianChildLinkService.isGuardianOf(currentUser.userId(), childId)) {
                throw GlobalException.of(ApiCode.PARENT_CHILD_FORBIDDEN);
            }
            LocalDate today = LocalDate.now(clock);
            Long classroomId = enrollmentService.findActiveClassroomIds(childId, today).stream()
                    .findFirst()
                    .orElse(null);
            List<ChildTeacherResponse> teachers = new ArrayList<>();
            if (classroomId != null) {
                List<Long> ids = staffAssignmentService
                        .findCurrentTeacherIds(List.of(classroomId), today)
                        .getOrDefault(classroomId, List.of());
                Long homeroomId = staffAssignmentService
                        .findHomeroomTeacherIds(List.of(classroomId), today)
                        .get(classroomId);
                for (UserAccount account :
                        ids.isEmpty() ? List.<UserAccount>of() : userAccountRepository.findAllById(ids)) {
                    String username = account.getUsername();
                    teachers.add(new ChildTeacherResponse(
                            account.getFullName(),
                            username != null && PHONE.matcher(username).matches() ? username : null,
                            account.getId().equals(homeroomId)));
                }
                teachers.sort(Comparator.comparing(ChildTeacherResponse::homeroom)
                        .reversed()
                        .thenComparing(ChildTeacherResponse::fullName));
            }
            log.info("{}|count={}|END", logPrefix, teachers.size());
            return teachers;
        } catch (RuntimeException ex) {
            log.warn("{}|ERROR|type={}", logPrefix, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
