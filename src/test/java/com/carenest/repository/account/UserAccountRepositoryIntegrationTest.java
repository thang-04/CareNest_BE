package com.carenest.repository.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carenest.entity.account.UserAccount;
import com.carenest.entity.account.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Migration V1 + mapping UserAccount trên PostgreSQL thật. Cần Docker đang chạy. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class UserAccountRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UserAccountRepository userAccountRepository;

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
}
