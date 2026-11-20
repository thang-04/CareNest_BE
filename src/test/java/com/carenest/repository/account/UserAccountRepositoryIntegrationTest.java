package com.carenest.repository.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.account.UserAccount;
import com.carenest.support.PostgresTestcontainer;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Migration V1 + mapping UserAccount trên PostgreSQL thật. Cần Docker đang chạy. */
@Import(PostgresTestcontainer.class)
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class UserAccountRepositoryIntegrationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        userAccountRepository.deleteAll();
    }

    @Test
    void shouldFindAccountByKeycloakSubject() {
        userAccountRepository.save(UserAccount.create("kc-sub-1", "teacher.a", null, "Giáo viên Demo"));

        UserAccount found =
                userAccountRepository.findByKeycloakUserId("kc-sub-1").orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(userAccountRepository.findByKeycloakUserId("kc-sub-unknown")).isEmpty();
    }

    @Test
    void shouldRejectSecondAccountForSameKeycloakUser() {
        userAccountRepository.saveAndFlush(UserAccount.create("kc-sub-1", "teacher.a", null, "Giáo viên Demo"));

        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(
                        UserAccount.create("kc-sub-1", "teacher.b", null, "Giáo viên Khác")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatePrimaryRoleChangesOnlyRoleAndStaleMergeDoesNotRevertIt() {
        UserAccount saved = userAccountRepository.saveAndFlush(
                UserAccount.create("kc-sub-1", "0912000001", null, "Phụ huynh", CareNestRole.PARENT));

        transactionTemplate.executeWithoutResult(
                status -> userAccountRepository.updatePrimaryRole(saved.getId(), CareNestRole.TEACHER.name()));
        // Bản cũ (role PARENT) được ghi lại, vd. khóa/mở song song: cột không updatable nên role mới giữ nguyên
        saved.changeStatus(UserStatus.INACTIVE);
        userAccountRepository.saveAndFlush(saved);

        UserAccount reloaded = userAccountRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPrimaryRole()).isEqualTo(CareNestRole.TEACHER);
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }
}
