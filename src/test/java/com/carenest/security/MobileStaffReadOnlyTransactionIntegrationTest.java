package com.carenest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.carenest.entity.account.UserAccount;
import com.carenest.repository.account.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** ADR-0026 lớp 2: transaction chỉ đọc của filter làm PostgreSQL thật từ chối ghi. Cần Docker đang chạy. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MobileStaffReadOnlyTransactionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        userAccountRepository.deleteAll();
    }

    @Test
    @DisplayName("AUTH-21: ghi trong transaction chỉ đọc của BGH trên App bị PostgreSQL chặn, không lưu dòng nào")
    void shouldRejectWriteInsideReadOnlyTransaction() {
        Throwable error = catchThrowable(() -> MobileStaffReadOnlyFilter.readOnlyTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    status.setRollbackOnly();
                    // Repository có @Transactional riêng (REQUIRED) ⇒ nhập vào transaction chỉ đọc bên ngoài
                    userAccountRepository.saveAndFlush(
                            UserAccount.create("kc-sub-ro", "principal.ro", null, "Hiệu trưởng Demo"));
                }));

        assertThat(MobileStaffReadOnlyFilter.isReadOnlyViolation(error)).isTrue();
        assertThat(userAccountRepository.findByKeycloakUserId("kc-sub-ro")).isEmpty();
    }

    @Test
    @DisplayName("AUTH-21: đọc trong transaction chỉ đọc vẫn chạy bình thường")
    void shouldReadInsideReadOnlyTransaction() {
        userAccountRepository.save(UserAccount.create("kc-sub-read", "principal.read", null, "Hiệu trưởng Demo"));

        Boolean found = MobileStaffReadOnlyFilter.readOnlyTemplate(transactionManager)
                .execute(status -> userAccountRepository
                        .findByKeycloakUserId("kc-sub-read")
                        .isPresent());

        assertThat(found).isTrue();
    }
}
