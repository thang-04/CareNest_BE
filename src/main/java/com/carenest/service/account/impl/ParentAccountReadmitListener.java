package com.carenest.service.account.impl;

import com.carenest.entity.account.UserAccount;
import com.carenest.repository.account.UserAccountRepository;
import com.carenest.service.child.ChildIntakeService;
import com.carenest.service.child.GuardianChildLinkService;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mở lại hồ sơ trẻ (CHD-03) ⇒ mở lại tài khoản phụ huynh của trẻ mà hệ thống đã khóa khi trẻ nghỉ (CHD-04, user chốt
 * 2026-11-11; cờ {@code locked_by_system}, V87). Tài khoản HT/PHT khóa tay giữ nguyên. Giữ mật khẩu cũ, không gửi SMS.
 * Lỗi ở đây chỉ làm tài khoản còn khóa (HT/PHT mở tay được), không mở thêm dữ liệu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ParentAccountReadmitListener {
    private static final String LOG_PREFIX = "[ParentAccountReadmitListener]";

    private final UserAccountRepository userAccountRepository;
    private final GuardianChildLinkService guardianChildLinkService;
    private final AccountLifecycleOperations accountLifecycle;

    // Chạy sau khi mở lại hồ sơ commit; mỗi tài khoản một transaction riêng, Keycloak gọi ngoài transaction
    @TransactionalEventListener
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onChildReadmitted(ChildIntakeService.ChildReadmittedEvent event) {
        String logPrefix = LOG_PREFIX + "|onChildReadmitted|childId=" + event.childId();
        log.info("{}|START", logPrefix);
        Set<Long> guardianIds = guardianChildLinkService.findLinksOfChild(event.childId()).stream()
                .map(link -> link.userId())
                .collect(Collectors.toSet());
        int reactivated = 0;
        for (UserAccount account : userAccountRepository.findAllById(guardianIds)) {
            if (account.isActive() || !account.isLockedBySystem()) continue;
            try {
                accountLifecycle.reactivateQuietly(logPrefix + "|userId=" + account.getId(), account);
                reactivated++;
            } catch (RuntimeException ex) {
                // Lỗi DB của một tài khoản không làm hỏng tài khoản khác; tài khoản đó vẫn khóa, mở tay được
                log.warn(
                        "{}|userId={}|ERROR|type={}",
                        logPrefix,
                        account.getId(),
                        ex.getClass().getSimpleName());
            }
        }
        log.info("{}|reactivated={}|END", logPrefix, reactivated);
    }
}
