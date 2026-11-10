package com.carenest.service.account.impl;

import com.carenest.entity.account.UserAccount;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.service.child.EnrollmentService;
import com.carenest.service.child.GuardianChildLinkService;
import com.carenest.service.child.YearTransitionService;
import com.carenest.utils.enums.CareNestRole;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ADR-0004: sau khi kích hoạt năm học, khóa tài khoản chỉ là phụ huynh mà không còn con nào đang học (CHD-04, user
 * chốt 2026-10-30). Không khóa tài khoản nhân sự kiêm phụ huynh. Xét theo ngày bắt đầu chuyển lớp nên kích hoạt sớm
 * (trước ngày bắt đầu) thì khóa ngay từ lúc kích hoạt. Policy đã chặn xem trẻ ra trường nên lỗi ở đây chỉ làm chậm
 * việc khóa, không mở dữ liệu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ParentAccountLeftSchoolListener {
    private static final String LOG_PREFIX = "[ParentAccountLeftSchoolListener]";

    private final UserAccountRepository userAccountRepository;
    private final GuardianChildLinkService guardianChildLinkService;
    private final EnrollmentService enrollmentService;
    private final AccountLifecycleOperations accountLifecycle;

    // NOT_SUPPORTED: không chạy trong transaction đã commit; mỗi tài khoản một transaction riêng, Keycloak gọi ngoài
    // transaction
    @TransactionalEventListener
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onChildrenLeftSchool(YearTransitionService.ChildrenLeftSchoolEvent event) {
        String logPrefix = LOG_PREFIX + "|onChildrenLeftSchool|children="
                + event.childIds().size();
        log.info("{}|START", logPrefix);
        LocalDate effectiveDate = event.effectiveDate();
        Set<Long> guardianIds = event.childIds().stream()
                .flatMap(childId -> guardianChildLinkService.findLinksOfChild(childId).stream())
                .map(link -> link.userId())
                .collect(Collectors.toSet());
        int deactivated = 0;
        for (UserAccount account : userAccountRepository.findAllById(guardianIds)) {
            if (account.getPrimaryRole() != CareNestRole.PARENT || !account.isActive()) continue;
            if (hasChildStillStudying(account.getId(), effectiveDate)) continue;
            try {
                accountLifecycle.deactivateQuietly(logPrefix + "|userId=" + account.getId(), account);
                deactivated++;
            } catch (RuntimeException ex) {
                // Lỗi DB của một tài khoản không làm hỏng các tài khoản khác; policy vẫn chặn xem trẻ đã ra trường
                log.warn(
                        "{}|userId={}|ERROR|type={}",
                        logPrefix,
                        account.getId(),
                        ex.getClass().getSimpleName());
            }
        }
        log.info("{}|deactivated={}|END", logPrefix, deactivated);
    }

    private boolean hasChildStillStudying(Long guardianId, LocalDate date) {
        List<Long> childIds = guardianChildLinkService.findChildIdsOfGuardian(guardianId);
        Collection<Long> left = enrollmentService.findChildIdsLeftSchool(childIds, date);
        return childIds.stream().anyMatch(childId -> !left.contains(childId));
    }
}
